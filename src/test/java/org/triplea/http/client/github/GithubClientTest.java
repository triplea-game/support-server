package org.triplea.http.client.github;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsCollectionContaining.hasItem;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.lanwen.wiremock.ext.WiremockResolver;
import ru.lanwen.wiremock.ext.WiremockUriResolver;

@ExtendWith({WiremockResolver.class, WiremockUriResolver.class})
class GithubClientTest {

  @Test
  void repoListing(@WiremockResolver.Wiremock final WireMockServer server) {
    stubRepoListingResponse(
        1,
        server,
        TestDataFileReader.readContents("sample_responses/repo_listing_response_page1.json"));
    stubRepoListingResponse(
        2,
        server,
        TestDataFileReader.readContents("sample_responses/repo_listing_response_page2.json"));
    stubRepoListingResponse(3, server, "[]");

    final Collection<MapRepoListing> repos =
        new GithubClient(URI.create(server.baseUrl()), "", "exmaple-org").listRepositories();

    assertThat(repos, hasSize(3));

    for (String uri :
        List.of(
            "https://github.com/triplea-maps/tutorial",
            "https://github.com/triplea-maps/aa_enhanced_revised",
            "https://github.com/triplea-maps/roman_invasion")) {

      assertThat(
          repos, //
          hasItem(MapRepoListing.builder().uri(uri).defaultBranch("main").build()));
    }
  }

  private void stubRepoListingResponse(
      final int expectedPageNumber, final WireMockServer server, final String response) {
    server.stubFor(
        get("/orgs/example-org/repos?per_page=100&page=" + expectedPageNumber)
            .willReturn(aResponse().withStatus(200).withBody(response)));
  }

  @Test
  @DisplayName("Invoke branches API and verify we can retrieve last commit date")
  void branchListingResponseFetchLastCommitDate(
      @WiremockResolver.Wiremock final WireMockServer server) {
    final String exampleResponse =
        TestDataFileReader.readContents("sample_responses/branch_listing_response.json");
    server.stubFor(
        get("/repos/example-org/map-repo/branches/main")
            .withHeader("Authorization", equalTo("token test-token"))
            .willReturn(aResponse().withStatus(200).withBody(exampleResponse)));

    final BranchInfoResponse branchInfoResponse =
        new GithubClient(URI.create(server.baseUrl()), "test-token", "example-org")
            .fetchBranchInfo("map-repo", "main");

    final Instant expectedLastCommitDate =
        LocalDateTime.of(2021, 2, 4, 19, 30, 32).atOffset(ZoneOffset.UTC).toInstant();
    assertThat(branchInfoResponse.getLastCommitDate(), is(expectedLastCommitDate));
  }

  @Test
  void getLatestRelease(@WiremockResolver.Wiremock final WireMockServer server) {
    final String exampleResponse =
        TestDataFileReader.readContents("sample_responses/latest_release_response.json");
    server.stubFor(
        get("/repos/example-org/map-repo/releases/latest")
            .withHeader("Authorization", equalTo("token test-token"))
            .willReturn(aResponse().withStatus(200).withBody(exampleResponse)));

    final String latestVersion =
        new GithubClient(URI.create(server.baseUrl()), "example-org", "test-token")
            .fetchLatestVersion("map-repo")
            .orElseThrow();

    assertThat(latestVersion, is("2.5.22294"));
  }
}
