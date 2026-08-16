package am.veolia.bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point. Boots a minimal (non-web) Spring context that wires together:
 * <ul>
 *     <li>the Telegram long-polling bot ({@code am.veolia.bot.bot}),</li>
 *     <li>the scheduled channel poller ({@code am.veolia.bot.poller}),</li>
 *     <li>and SQLite-backed persistence ({@code am.veolia.bot.repository}).</li>
 * </ul>
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class VeoliaBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(VeoliaBotApplication.class, args);
    }
}
