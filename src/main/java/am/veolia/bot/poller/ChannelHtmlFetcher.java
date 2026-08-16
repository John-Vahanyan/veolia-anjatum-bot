package am.veolia.bot.poller;

import am.veolia.bot.config.ChannelProperties;
import am.veolia.bot.model.ChannelPost;
import am.veolia.bot.model.PostIds;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Fetches and parses the public, login-free {@code https://t.me/s/<channel>}
 * HTML preview Telegram exposes for any public channel — no MTProto/userbot
 * login required.
 */
@Component
public class ChannelHtmlFetcher {

    private static final Logger log = LoggerFactory.getLogger(ChannelHtmlFetcher.class);
    private static final int TIMEOUT_MS = 15_000;
    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; VeoliaJurOutageBot/1.0; +https://t.me/s/)";

    private final ChannelProperties channelProperties;

    public ChannelHtmlFetcher(ChannelProperties channelProperties) {
        this.channelProperties = channelProperties;
    }

    /**
     * Fetches the channel preview page and returns every post found on it,
     * oldest first. Returns an empty list (after logging) on any network/parse failure
     * rather than throwing, so a single failed poll cycle never crashes the scheduler.
     */
    public List<ChannelPost> fetchPosts() {
        String url = channelProperties.previewUrl();
        Document doc;
        try {
            doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();
        } catch (IOException e) {
            log.warn("Failed to fetch {}: {}", url, e.toString());
            return List.of();
        }

        Elements postElements = doc.select("div.tgme_widget_message[data-post]");
        List<ChannelPost> posts = new ArrayList<>();
        for (Element postElement : postElements) {
            String postId = postElement.attr("data-post");
            if (postId.isBlank()) {
                continue;
            }
            Element textElement = postElement.selectFirst("div.tgme_widget_message_text");
            if (textElement == null) {
                // Media-only posts (photo/video with no caption) have no text node — nothing to parse.
                continue;
            }
            String text = extractText(textElement);
            posts.add(new ChannelPost(postId, text));
        }
        posts.sort(Comparator.comparingLong(p -> PostIds.sequenceNumber(p.postId())));
        return posts;
    }

    /**
     * Telegram renders line breaks as {@code <br>} tags rather than newline characters;
     * convert them back so downstream regex parsing sees the same layout a human would.
     */
    private String extractText(Element textElement) {
        for (Element br : textElement.select("br")) {
            br.after("\n");
        }
        return textElement.wholeText().replace("\u00A0", " ").trim();
    }
}
