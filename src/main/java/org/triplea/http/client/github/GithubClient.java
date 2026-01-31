package org.triplea.http.client.github;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import feign.FeignException;
import feign.Param;
import feign.QueryMap;
import feign.RequestLine;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import javax.annotation.Nonnull;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.triplea.http.client.HttpClient;
import static org.slf4j.LoggerFactory.getLogger;

/** Can be used to interact with github's webservice API. */
public class GithubClient  {

  private static final Logger log = getLogger(GithubClient.class);

  /**
   * Inner implementation for the HTTP client, Feign interface,
   * we can instantiate this with Feign.
   */
  @SuppressWarnings("InterfaceNeverImplemented")
  interface GithubApiFeignClient {
    @RequestLine("POST /repos/{org}/{repo}/issues")
    CreateIssueResponse newIssue(
        @Param("org") String org, @Param("repo") String repo, CreateIssueRequest createIssueRequest);

    @RequestLine("GET /orgs/{org}/repos")
    List<MapRepoListing> listRepos(
        @QueryMap Map<String, String> queryParams, @Param("org") String org);

    @RequestLine("GET /repos/{org}/{repo}/branches/{branch}")
    BranchInfoResponse getBranchInfo(
        @Param("org") String org, @Param("repo") String repo, @Param("branch") String branch);

    @RequestLine("GET /repos/{org}/{repo}/releases/latest")
    LatestReleaseResponse getLatestRelease(@Param("org") String org, @Param("repo") String repo);
  }

  private final static String GITHUB_HOST= "api.github.com";

  private final GithubApiFeignClient githubApiFeignClient;
  private final String org;

  /**
   * @param authToken Auth token that will be sent to Github for webservice calls. Can be empty, but
   *     if specified must be valid (no auth token still works, but rate limits will be more
   *     restrictive).
   * @param org Name of the github org to be queried.
   */
  @Builder
  public GithubClient(String authToken, @Nonnull String org) {
    this(URI.create(GITHUB_HOST), authToken, org);
  }

  public GithubClient(@Nonnull URI uri, String authToken, @Nonnull String org) {
    githubApiFeignClient =
        HttpClient.newClient(
            GithubApiFeignClient.class,
            uri,
            Strings.isNullOrEmpty(authToken)
                ? Map.of()
                : Map.of("Authorization", "token " + authToken));
    this.org = org;
  }

  /**
   * Returns a listing of the repositories within a github organization. This call handles paging,
   * it returns a complete list and may perform multiple calls to Github.
   *
   * <p>Example equivalent cUrl call:
   *
   * <p>curl https://api.github.com/orgs/triplea-maps/repos
   */
  public Collection<MapRepoListing> listRepositories() {
    final Collection<MapRepoListing> allRepos = new HashSet<>();
    int pageNumber = 1;
    Collection<MapRepoListing> repos = listRepositories(pageNumber);
    while (!repos.isEmpty()) {
      pageNumber++;
      allRepos.addAll(repos);
      repos = listRepositories(pageNumber);
    }
    return allRepos;
  }

  private Collection<MapRepoListing> listRepositories(int pageNumber) {
    final Map<String, String> queryParams = new HashMap<>();
    queryParams.put("per_page", "100");
    queryParams.put("page", String.valueOf(pageNumber));

    return githubApiFeignClient.listRepos(queryParams, org);
  }

  public Instant getLatestCommitDate(String repoName, String branchName) {
    return githubApiFeignClient.getBranchInfo(org, repoName, branchName).getLastCommitDate();
  }


  /**
   * Invokes github web-API to create a github issue with the provided parameter data.
   *
   * @param createIssueRequest Upload data for creating the body and title of the github issue.
   * @return Response from server containing link to the newly created issue.
   * @throws feign.FeignException thrown on error or if non-2xx response is received
   */
  public CreateIssueResponse newIssue(String repo, CreateIssueRequest createIssueRequest) {
    return githubApiFeignClient.newIssue(org, repo, createIssueRequest);
  }

  /**
   * Fetches details of a specific branch on a specific repo. Useful for retrieving info about the
   * last commit to the repo. Note, the repo listing contains a 'last_push' date, but this method
   * should be used instead as the last_push date on a repo can be for any branch (even PRs).
   *
   * <p>Example equivalent cUrl:
   * https://api.github.com/repos/triplea-maps/star_wars_galactic_war/branches/master
   *
   * @param branch Which branch to be queried.
   * @return Payload response object representing the response from Github's web API.
   *
   */
  public BranchInfoResponse fetchBranchInfo(String repo, String branch) {
    return githubApiFeignClient.getBranchInfo(org, repo, branch);
  }

  public Optional<String> fetchLatestVersion(String repo) {
    try {
      return Optional.of(githubApiFeignClient.getLatestRelease(org, repo).getTagName());
    } catch (final FeignException e) {
      log.warn("No data received from server for latest engine version", e);
      return Optional.empty();
    }
  }
}
