package org.triplea.maps.indexing;

import io.dropwizard.lifecycle.Managed;
import java.time.Duration;
import lombok.experimental.UtilityClass;
import org.jdbi.v3.core.Jdbi;
import org.triplea.server.SupportServerConfig;
import org.triplea.server.lib.scheduled.tasks.ScheduledTask;

@UtilityClass
public class MapsIndexingObjectFactory {
  /**
   * Factory method to create indexing task on a schedule. This does not start indexing, the
   * 'start()' method must be called for map indexing to begin.
   */
  public static Managed buildMapsIndexingSchedule(
      final SupportServerConfig configuration, final Jdbi jdbi) {

    return ScheduledTask.builder()
        .taskName("Map-Indexing")
        .delay(Duration.ofSeconds(20))
        .period(Duration.ofMinutes(configuration.getMapIndexingPeriodMinutes()))
        .task(
            MapIndexingTaskRunner.builder()
                .githubClient(configuration.createGithubApiClient())
                .mapIndexer(MapIndexer.build(configuration.createGithubApiClient()))
                .mapIndexDao(new MapIndexDao(jdbi))
                .indexingTaskDelaySeconds(configuration.getIndexingTaskDelaySeconds())
                .build())
        .build();
  }
}
