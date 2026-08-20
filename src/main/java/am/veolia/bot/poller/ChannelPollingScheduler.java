package am.veolia.bot.poller;

import am.veolia.bot.bot.VeoliaNotifierBot;
import am.veolia.bot.i18n.Messages;
import am.veolia.bot.model.ChannelPost;
import am.veolia.bot.model.Language;
import am.veolia.bot.model.OutageAnnouncement;
import am.veolia.bot.model.Subscription;
import am.veolia.bot.model.SubscriptionType;
import am.veolia.bot.model.UserAccount;
import am.veolia.bot.parser.KeywordMatcher;
import am.veolia.bot.parser.OutageAnnouncementParser;
import am.veolia.bot.repository.ProcessedPostRepository;
import am.veolia.bot.repository.UserRepository;
import am.veolia.bot.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Periodically polls the channel, parses new posts, matches them against
 * every user's subscriptions, and notifies matches — end to end.
 */
@Component
public class ChannelPollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(ChannelPollingScheduler.class);

    private final ChannelHtmlFetcher fetcher;
    private final OutageAnnouncementParser parser;
    private final KeywordMatcher keywordMatcher;
    private final ProcessedPostRepository processedPostRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final VeoliaNotifierBot bot;

    public ChannelPollingScheduler(ChannelHtmlFetcher fetcher,
                                    OutageAnnouncementParser parser,
                                    KeywordMatcher keywordMatcher,
                                    ProcessedPostRepository processedPostRepository,
                                    SubscriptionRepository subscriptionRepository,
                                    UserRepository userRepository,
                                    VeoliaNotifierBot bot) {
        this.fetcher = fetcher;
        this.parser = parser;
        this.keywordMatcher = keywordMatcher;
        this.processedPostRepository = processedPostRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.bot = bot;
    }

    @Scheduled(
            initialDelayString = "${app.polling.initial-delay-ms}",
            fixedDelayString = "${app.polling.interval-ms}"
    )
    public void poll() {
        try {
            pollOnce();
        } catch (Exception e) {
            // A single failed poll cycle (network blip, unexpected page shape, ...) must
            // never stop future scheduled runs.
            log.error("Poll cycle failed: {}", e.toString(), e);
        }
    }

    private void pollOnce() {
        List<ChannelPost> posts = fetcher.fetchPosts();
        if (posts.isEmpty()) {
            return;
        }

        List<ChannelPost> newPosts = posts.stream()
                .filter(post -> !processedPostRepository.isProcessed(post.postId()))
                .toList();
        if (newPosts.isEmpty()) {
            return;
        }
        log.info("Found {} new post(s) on the channel", newPosts.size());

        for (ChannelPost post : newPosts) {
            handlePost(post);
        }
    }

    private void handlePost(ChannelPost post) {
        Optional<OutageAnnouncement> parsed = parser.parse(post);
        if (parsed.isEmpty()) {
            // Not an outage announcement (or malformed) — mark processed so we don't retry it forever.
            processedPostRepository.markProcessed(post.postId());
            return;
        }

        OutageAnnouncement announcement = parsed.get();
        List<Subscription> subscriptions = subscriptionRepository.findAll();
        int notified = 0;
        for (Subscription subscription : subscriptions) {
            if (matches(subscription, announcement)) {
                notifyUser(subscription.userId(), announcement);
                notified++;
            }
        }
        log.info("Processed post {} ({} street fragment(s)), notified {} user(s)",
                post.postId(), announcement.streets().size(), notified);

        processedPostRepository.markProcessed(post.postId());
    }

    /**
     * A {@link am.veolia.bot.model.SubscriptionType#SCOPED_STREET} subscription must match
     * both its region/district scope and its street keyword; everything else (legacy
     * free-text subscriptions, and the guided flow's "whole region"/"whole district" choices)
     * is a plain keyword match against every fragment, same as before scoping existed.
     */
    private boolean matches(Subscription subscription, OutageAnnouncement announcement) {
        if (subscription.type() == SubscriptionType.SCOPED_STREET) {
            String scopeArmenian = subscription.district() != null
                    ? subscription.district().armenian()
                    : subscription.region() != null ? subscription.region().armenian() : null;
            if (scopeArmenian == null) {
                // Scope code doesn't resolve to a known region/district (shouldn't normally
                // happen) — fail closed rather than notifying with no real scope check.
                return false;
            }
            return keywordMatcher.matchesScoped(scopeArmenian, subscription.keyword(), announcement);
        }
        return keywordMatcher.matches(subscription.keyword(), announcement);
    }

    private void notifyUser(long chatId, OutageAnnouncement announcement) {
        Language lang = userRepository.findByChatId(chatId)
                .map(UserAccount::language)
                .orElse(Language.HY);
        String message = Messages.newOutageHeader(lang) + "\n\n" + announcement.rawText();
        bot.send(chatId, message);
    }
}
