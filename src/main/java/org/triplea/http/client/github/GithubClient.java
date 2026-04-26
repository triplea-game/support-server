package org.triplea.http.client.github;

import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import javax.annotation.Nonnull;

public interface GithubClient {

  static GithubClient build(String authToken, @Nonnull String org) {
    return new GithubApiClient(URI.create("https://api.github.com"), authToken, org);
  }

  Instant getLatestCommitDate(String repoName, String branchName);

  Collection<MapRepoListing> listRepositories();
}
