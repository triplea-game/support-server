package org.triplea.http.client.github;

import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

public interface GithubClient {
  /// Max authenticated api requests per hour [2026-April]
  static final int GITHUB_MAX_REQUESTS_PER_HOUR = 5000;

  /// Minimum ms between requests to stay within the hourly rate limit.
  static final int GITHUB_MIN_REQUEST_INTERVAL_MS =
      (int) (TimeUnit.HOURS.toMillis(1) / GITHUB_MAX_REQUESTS_PER_HOUR);

  static GithubClient build(String authToken, @Nonnull String org) {
    return new GithubApiClient(URI.create("https://api.github.com"), authToken, org);
  }

  static GithubClient build(@Nonnull URI baseUri, String authToken, @Nonnull String org) {
    return new GithubApiClient(baseUri, authToken, org);
  }

  Collection<MapRepoListing> listRepositories();

  Instant getLatestCommitDate(String repoName, String branchName);

  BranchInfoResponse fetchBranchInfo(String repo, String branch);

  Optional<String> fetchLatestVersion(String repo);

  CreateIssueResponse newIssue(String repo, CreateIssueRequest createIssueRequest);
}
