package org.triplea.http.client.github;

import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import javax.annotation.Nonnull;

public interface GithubClient {

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
