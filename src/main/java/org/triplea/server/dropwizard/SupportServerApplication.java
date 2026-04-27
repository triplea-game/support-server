package org.triplea.server.dropwizard;

import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.jdbi3.JdbiFactory;
import io.dropwizard.jdbi3.bundles.JdbiExceptionsBundle;
import io.dropwizard.lifecycle.Managed;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.triplea.maps.MapsController;
import org.triplea.maps.indexing.MapIndexingConfig;
import org.triplea.maps.indexing.MapsIndexingObjectFactory;
import org.triplea.server.error.reporting.ErrorReportController;

/**
 * Dropwizard entry-point. This class is the framework adapter layer and will be replaced when
 * migrating away from Dropwizard.
 */
@Slf4j
public class SupportServerApplication extends Application<SupportServerConfig> {

  private static final String[] DEFAULT_ARGS = new String[] {"server", "configuration.yml"};

  /**
   * Main entry-point. If no args are provided, defaults suitable for local development are used.
   */
  public static void main(final String[] args) throws Exception {
    final SupportServerApplication application = new SupportServerApplication();
    application.run(args.length == 0 ? DEFAULT_ARGS : args);
  }

  @Override
  public void initialize(final Bootstrap<SupportServerConfig> bootstrap) {
    bootstrap.setConfigurationSourceProvider(
        new SubstitutingSourceProvider(
            bootstrap.getConfigurationSourceProvider(), new EnvironmentVariableSubstitutor(false)));
    bootstrap.addBundle(new JdbiExceptionsBundle());
  }

  @Override
  public void run(final SupportServerConfig configuration, final Environment environment) {
    final Jdbi jdbi =
        new JdbiFactory()
            .build(environment, configuration.getDatabase(), "postgresql-connection-pool");

    if (configuration.isMapIndexingEnabled()) {
      final MapIndexingConfig mapIndexingConfig =
          MapIndexingConfig.builder()
              .githubClient(configuration.githubClientMaps())
              .periodMinutes(configuration.getMapIndexingPeriodMinutes())
              .taskDelaySeconds(configuration.getIndexingTaskDelaySeconds())
              .build();
      environment
          .lifecycle()
          .manage(
              (Managed)
                  MapsIndexingObjectFactory.buildMapsIndexingSchedule(mapIndexingConfig, jdbi));
      log.info(
          "Map indexing is enabled to run every:"
              + " {} minutes with one map indexing request every {} seconds",
          configuration.getMapIndexingPeriodMinutes(),
          configuration.getIndexingTaskDelaySeconds());
    } else {
      log.info("Map indexing is disabled");
    }

    List.of(
            MapsController.build(jdbi),
            ErrorReportController.build(
                configuration.githubClientErrorReporting(),
                configuration.getErrorReportingRepo(),
                jdbi))
        .forEach(controller -> environment.jersey().register(controller));
  }
}
