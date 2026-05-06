package org.triplea.maps.indexing;

import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jdbi.v3.core.Jdbi;
import org.triplea.http.client.github.GithubClient;

/**
 * Quarkus-managed bean that runs the map indexing task on a configurable schedule. Replaces the
 * DropWizard {@code ScheduledTask} + {@code MapsIndexingObjectFactory} wiring.
 */
@ApplicationScoped
@Slf4j
public class MapIndexingScheduler {

  @ConfigProperty(name = "app.map-indexing-enabled", defaultValue = "false")
  boolean mapIndexingEnabled;

  @ConfigProperty(name = "app.map-indexing-period-minutes", defaultValue = "300")
  int periodMinutes;

  @ConfigProperty(name = "app.indexing-task-delay-seconds", defaultValue = "120")
  int taskDelaySeconds;

  @ConfigProperty(name = "app.github-api-token")
  Optional<String> githubApiToken;

  @ConfigProperty(name = "app.github-maps-org-name", defaultValue = "triplea-maps")
  String githubMapsOrgName;

  @Inject Jdbi jdbi;

  private MapIndexingTaskRunner taskRunner;

  @PostConstruct
  void init() {
    var githubClient = GithubClient.build(githubApiToken.orElse(""), githubMapsOrgName);
    taskRunner =
        MapIndexingTaskRunner.builder()
            .githubClient(githubClient)
            .mapIndexer(MapIndexer.build(githubClient))
            .mapIndexDao(new MapIndexDao(jdbi))
            .indexingTaskDelaySeconds(taskDelaySeconds)
            .build();
  }

  @Scheduled(every = "{app.map-indexing-period-duration}", delayed = "20s")
  void index() {
    if (!mapIndexingEnabled) {
      return;
    }
    log.info("Map indexing period: {} minutes", periodMinutes);
    taskRunner.run();
  }
}
