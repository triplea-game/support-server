package org.triplea.maps.indexing;

import lombok.Builder;
import lombok.Value;
import org.triplea.http.client.github.GithubClient;

/** Configuration required to schedule and run the map indexing background task. */
@Value
@Builder
public class MapIndexingConfig {
  GithubClient githubClient;
  int periodMinutes;
  int taskDelaySeconds;
}
