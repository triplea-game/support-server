package org.triplea.http.client.github;

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
import javax.annotation.Nonnull;

/** Can be used to interact with github's webservice API. */
class GithubApiClient implements GithubClient {

  private static final String LIST_REPOS_PATH = "/orgs/%s/repos?per_page=100&page=%d";
  private static final String BRANCHES_PATH = "/repos/%s/%s/branches/%s";

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
}
