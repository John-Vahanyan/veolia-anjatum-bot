package am.veolia.bot.maintenance;

import am.veolia.bot.bot.VeoliaNotifierBot;
import am.veolia.bot.i18n.Messages;
import am.veolia.bot.model.Language;
import am.veolia.bot.model.UserAccount;
import am.veolia.bot.repository.SubscriptionRepository;
import am.veolia.bot.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * DANGEROUS one-off maintenance task, off by default: wipes every subscriber's
 * subscriptions and sends each of them a localized notice telling them to
 * re-subscribe with the new guided flow. Written for the region/district
 * scoping rollout, where old free-text subscriptions can't be automatically
 * upgraded into the new scoped shape — see README "One-off: migration notice"
 * for exactly how and when to run this.
 *
 * <p>Gated behind {@code app.maintenance.run-migration-notice}
 * ({@code RUN_MIGRATION_NOTICE} env var), which defaults to {@code false} —
 * a normal {@code systemctl restart} with that var unset is a single cheap
 * boolean check and otherwise a complete no-op. Only flip it on deliberately,
 * for exactly one run.
 *
 * <p>When it does run, it calls {@link System#exit} once finished so this
 * process never proceeds to {@link am.veolia.bot.config.TelegramBotRegistrar}
 * and starts long-polling — that would race the real, already-running bot
 * process for the same Telegram bot token if this were launched alongside
 * it. This task only ever needs a database connection and the ability to
 * call the Bot API's {@code sendMessage}, neither of which requires
 * long-polling to be registered.
 *
 * <p>{@code @Order} one step after {@code SubscriptionSchemaMigration}
 * (which runs at {@code HIGHEST_PRECEDENCE}) guarantees the table is already
 * in its current shape before this reads/writes it.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class MigrationNoticeRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrationNoticeRunner.class);

    private final boolean enabled;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final VeoliaNotifierBot bot;

    public MigrationNoticeRunner(@Value("${app.maintenance.run-migration-notice:false}") boolean enabled,
                                  SubscriptionRepository subscriptionRepository,
                                  UserRepository userRepository,
                                  VeoliaNotifierBot bot) {
        this.enabled = enabled;
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.bot = bot;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }

        log.warn("=== RUN_MIGRATION_NOTICE is set: wiping every subscriber's subscriptions and notifying them. "
                + "This does NOT start the bot/poller in this process. ===");

        List<Long> userIds = subscriptionRepository.findDistinctUserIdsWithSubscriptions();
        log.warn("Found {} subscriber(s) with at least one subscription", userIds.size());

        int processed = 0;
        int failed = 0;
        for (Long userId : userIds) {
            try {
                Language lang = userRepository.findByChatId(userId).map(UserAccount::language).orElse(Language.HY);
                int removed = subscriptionRepository.removeAllForUser(userId);
                bot.send(userId, Messages.migrationNoticeResubscribe(lang));
                log.info("chat {}: removed {} subscription(s), sent migration notice ({})", userId, removed, lang);
                processed++;
            } catch (Exception e) {
                // One user's DB hiccup or delivery failure must not abort the sweep for everyone else.
                log.error("chat {}: failed to process migration notice: {}", userId, e.toString(), e);
                failed++;
            }
        }

        log.warn("=== Migration notice run complete: {} processed, {} failed, out of {} total. Exiting now "
                + "WITHOUT starting the bot — remove RUN_MIGRATION_NOTICE and restart normally to resume "
                + "service. ===", processed, failed, userIds.size());
        System.exit(0);
    }
}
