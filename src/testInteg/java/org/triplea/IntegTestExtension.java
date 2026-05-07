package org.triplea;

import com.github.database.rider.core.configuration.GlobalConfig;
import com.github.database.rider.junit5.DBUnitExtension;
import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.AgroalConnectionFactoryConfiguration;
import io.agroal.api.security.NamePrincipal;
import io.agroal.api.security.SimplePassword;
import io.quarkus.arc.Arc;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Sets up database access and shared test state for integration tests.
 *
 * <p>Resolves datasource coordinates from the CDI-managed {@link AgroalDataSource} bean so the
 * coordinates always match the actual datasource (including Dev Services / Testcontainers). Tests
 * that need the server URI should inject it via {@code @ConfigProperty(name =
 * "quarkus.http.test-port", defaultValue = "8081")}.
 */
@ExtendWith(DBUnitExtension.class)
public class IntegTestExtension implements BeforeEachCallback {

  private static boolean initialized = false;
  private static Jdbi jdbi;

  @Override
  public void beforeEach(final ExtensionContext context) throws Exception {
    if (!initialized) {
      jdbi = Arc.container().select(Jdbi.class).get();

      AgroalDataSource ds = Arc.container().select(AgroalDataSource.class).get();
      AgroalConnectionFactoryConfiguration connFactory =
          ds.getConfiguration().connectionPoolConfiguration().connectionFactoryConfiguration();

      String url = connFactory.jdbcUrl();
      String user = ((NamePrincipal) connFactory.principal()).getName();
      String password =
          connFactory.credentials().stream()
              .filter(c -> c instanceof SimplePassword)
              .map(c -> ((SimplePassword) c).getWord())
              .findFirst()
              .orElseThrow(
                  () -> new IllegalStateException("No password found in datasource config"));

      GlobalConfig.instance().getDbUnitConfig().url(url);
      GlobalConfig.instance().getDbUnitConfig().user(user);
      GlobalConfig.instance().getDbUnitConfig().password(password);
      GlobalConfig.instance().getDbUnitConfig().driver("org.postgresql.Driver");

      initialized = true;
    }

    final URL cleanupFileUrl = getClass().getClassLoader().getResource("db-cleanup.sql");
    if (cleanupFileUrl != null) {
      final String cleanupSql = Files.readString(Path.of(cleanupFileUrl.toURI()));
      jdbi.withHandle(handle -> handle.execute(cleanupSql));
    }
  }
}
