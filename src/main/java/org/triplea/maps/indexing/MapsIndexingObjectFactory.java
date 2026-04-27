package org.triplea.maps.indexing;

import java.time.Duration;
import lombok.experimental.UtilityClass;
import org.jdbi.v3.core.Jdbi;
import org.triplea.server.lib.scheduled.tasks.ScheduledTask;
import org.triplea.server.lib.scheduled.tasks.TaskLifecycle;

@UtilityClass
public class MapsIndexingObjectFactory {
  /**
   * Factory method to create indexing task on a schedule. This does not start indexing, the
   * 'start()' method must be called for map indexing to begin.
   */
  public static TaskLifecycle buildMapsIndexingSchedule(
      final MapIndexingConfig config, final Jdbi jdbi) {

    return ScheduledTask.builder()
        .taskName("Map-Indexing")
        .delay(Duration.ofSeconds(20))
        .period(Duration.ofMinutes(config.getPeriodMinutes()))
        .task(
            MapIndexingTaskRunner.builder()
                .githubClient(config.getGithubClient())
                .mapIndexer(MapIndexer.build(config.getGithubClient()))
                .mapIndexDao(new MapIndexDao(jdbi))
                .indexingTaskDelaySeconds(config.getTaskDelaySeconds())
                .build())
        .build();
  }
}
