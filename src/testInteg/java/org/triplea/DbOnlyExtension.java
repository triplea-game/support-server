package org.triplea;

import com.github.database.rider.core.configuration.GlobalConfig;
import com.github.database.rider.junit5.DBUnitExtension;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit 5 extension for tests that need only a live database (no server process). Configures
 * DBUnit's {@link GlobalConfig} and runs the db-cleanup script before each test.
 *
 * <p>Reads datasource coordinates from the Quarkus {@link ConfigProvider} — available after
 * {@code @QuarkusTest}'s {@code QuarkusTestExtension.beforeAll()} has set up the test profile and
 * datasource system properties.
 *
 * <p>{@code Jdbi} constructor parameters in test classes are injected by Quarkus CDI (via {@code
 * QuarkusTestExtension}). Use {@link IntegTestExtension} instead when the test also needs the
 * server URI.
 */
@ExtendWith(DBUnitExtension.class)
public class DbOnlyExtension implements BeforeAllCallback, BeforeEachCallback {

  /**
   * Internal Jdbi used only for db-cleanup; test classes receive CDI-injected Jdbi from Quarkus.
   */
  private static Jdbi jdbi;

  @Override
  public void beforeAll(final ExtensionContext context) {
    // QuarkusTestExtension.beforeAll() has already run, so the Quarkus test profile is active
    // and Dev Services has started; ConfigProvider can resolve the dynamic JDBC URL.
    var config = ConfigProvider.getConfig();
    var url = config.getValue("quarkus.datasource.jdbc.url", String.class);
    var user =
        config.getOptionalValue("quarkus.datasource.username", String.class).orElse("support_user");
    var password =
        config.getOptionalValue("quarkus.datasource.password", String.class).orElse("support_user");

    if (jdbi == null) {
      jdbi = Jdbi.create(url, user, password);
      jdbi.installPlugin(new SqlObjectPlugin());
    }

    GlobalConfig.instance().getDbUnitConfig().url(url);
    GlobalConfig.instance().getDbUnitConfig().user(user);
    GlobalConfig.instance().getDbUnitConfig().password(password);
    GlobalConfig.instance().getDbUnitConfig().driver("org.postgresql.Driver");
  }

  @Override
  public void beforeEach(final ExtensionContext context) throws Exception {
    final URL cleanupFileUrl = getClass().getClassLoader().getResource("db-cleanup.sql");
    if (cleanupFileUrl != null) {
      final String cleanupSql = Files.readString(Path.of(cleanupFileUrl.toURI()));
      jdbi.withHandle(handle -> handle.execute(cleanupSql));
    }
  }
}
