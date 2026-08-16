package am.veolia.bot.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Builds the SQLite {@link DataSource} by hand (rather than relying on
 * {@code spring.datasource.*} auto-configuration) so we can guarantee the
 * parent directory of the configured DB file exists before the SQLite JDBC
 * driver ever tries to open it — the driver creates the file but not its
 * parent directories.
 */
@Configuration
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    @Bean
    public DataSource dataSource(DbProperties dbProperties) throws IOException {
        Path dbPath = Path.of(dbProperties.path()).toAbsolutePath().normalize();
        Path parentDir = dbPath.getParent();
        if (parentDir != null) {
            Files.createDirectories(parentDir);
        }
        log.info("Using SQLite database at {}", dbPath);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbPath);
        config.setDriverClassName("org.sqlite.JDBC");
        // SQLite allows only one writer at a time; a single pooled connection
        // avoids SQLITE_BUSY lock contention entirely for this bot's low write volume.
        config.setMaximumPoolSize(1);
        config.setPoolName("sqlite-pool");
        return new HikariDataSource(config);
    }
}
