package org.triplea.http.client.github;

import static org.slf4j.LoggerFactory.getLogger;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.slf4j.Logger;

/** Can be used to interact with github's webservice API. */
class GithubApiClient implements GithubClient {

  private static final Logger log = getLogger(GithubApiClient.class);

  private static final String LIST_REPOS_PATH = "/orgs/%s/repos?per_page=100&page=%d";
  private static final String BRANCHES_PATH = "/repos/%s/%s/branches/%s";
  private static final String ISSUES_PATH = "/repos/%s/%s/issues";
  private static final String LATEST_RELEASE_PATH = "/repos/%s/%s/releases/latest";

  private static final Gson GSON = new Gson();

  private final HttpClient httpClient;
  private final URI baseUri;
  private final String authToken;
  private final String org;

  /**
   * @param baseUri The base URI for the Github webservice API.
   * @param authToken Auth token sent to Github for webservice calls. Can be empty, but if specified
   *     must be valid (no auth token still works, but rate limits will be more restrictive).
   * @param org Name of the Github org to be queried.
   */
  GithubApiClient(@Nonnull URI baseUri, String authToken, @Nonnull String org) {
    this.httpClient = HttpClient.newHttpClient();
    this.baseUri = baseUri;
    this.authToken = authToken;
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
  @Override
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
    String path = String.format(LIST_REPOS_PATH, encodePath(org), pageNumber);
    Type listType = new TypeToken<List<MapRepoListing>>() {}.getType();
    return GSON.fromJson(sendGet(path), listType);
  }

  @Override
  public Instant getLatestCommitDate(String repoName, String branchName) {
    String path =
        String.format(BRANCHES_PATH, encodePath(org), encodePath(repoName), encodePath(branchName));
    return GSON.fromJson(sendGet(path), BranchInfoResponse.class).getLastCommitDate();
  }

  @Override
  public BranchInfoResponse fetchBranchInfo(String repo, String branch) {
    String path =
        String.format(BRANCHES_PATH, encodePath(org), encodePath(repo), encodePath(branch));
    return GSON.fromJson(sendGet(path), BranchInfoResponse.class);
  }

  @Override
  public Optional<String> fetchLatestVersion(String repo) {
    String path = String.format(LATEST_RELEASE_PATH, encodePath(org), encodePath(repo));
    try {
      return Optional.of(GSON.fromJson(sendGet(path), LatestReleaseResponse.class).getTagName());
    } catch (RuntimeException e) {
      log.warn("No data received from server for latest engine version", e);
      return Optional.empty();
    }
  }

  @Override
  public CreateIssueResponse newIssue(String repo, CreateIssueRequest createIssueRequest) {
    String path = String.format(ISSUES_PATH, encodePath(org), encodePath(repo));
    return GSON.fromJson(sendPost(path, createIssueRequest), CreateIssueResponse.class);
  }

  private static String encodePath(String segment) {
    return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private String sendGet(String path) {
    HttpRequest.Builder builder = HttpRequest.newBuilder().uri(baseUri.resolve(path)).GET();
    if (authToken != null && !authToken.isEmpty()) {
      builder.header("Authorization", "token " + authToken);
    }
    try {
      HttpResponse<String> response =
          httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new RuntimeException(
            "GitHub API request to "
                + path
                + " failed with status "
                + response.statusCode()
                + ": "
                + response.body());
      }
      return response.body();
    } catch (IOException e) {
      throw new RuntimeException("GitHub API request failed: " + path, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("GitHub API request interrupted: " + path, e);
    }
  }

  private String sendPost(String path, Object body) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(baseUri.resolve(path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)));
    if (authToken != null && !authToken.isEmpty()) {
      builder.header("Authorization", "token " + authToken);
    }
    try {
      HttpResponse<String> response =
          httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new RuntimeException(
            "GitHub API request to "
                + path
                + " failed with status "
                + response.statusCode()
                + ": "
                + response.body());
      }
      return response.body();
    } catch (IOException e) {
      throw new RuntimeException("GitHub API request failed: " + path, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("GitHub API request interrupted: " + path, e);
    }
  }
}
