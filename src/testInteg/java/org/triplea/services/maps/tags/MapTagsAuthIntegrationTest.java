package org.triplea.services.maps.tags;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.triplea.IntegTestExtension;

/// Authorization integration tests for the fully-gated tag list: anonymous is rejected (401) on the
/// GET render and on all 4 mutation endpoints; a MapAdmin is allowed through, and a MapAdmin
/// mutation still needs a valid double-submit CSRF token.
///
/// Under `@QuarkusTest` identity comes from the `X-Auth-*` headers (the post-nginx state):
/// anonymous = no headers, MapAdmin = an email plus the MapAdmin group.
@QuarkusTest
@ExtendWith(IntegTestExtension.class)
class MapTagsAuthIntegrationTest {

  private static final String BASE = "/support/admin/map/tags";

  @ConfigProperty(name = "quarkus.http.test-port", defaultValue = "8081")
  int testPort;

  @ConfigProperty(name = "app.auth.map-admin-group")
  String mapAdminGroup;

  private HttpClient httpClient;
  private String baseUrl;

  @BeforeEach
  void setUp() {
    httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    baseUrl = "http://localhost:" + testPort;
  }

  @Test
  void anonymousGetIsRejected() throws Exception {
    assertThat(send(get(false)).statusCode()).isEqualTo(401);
  }

  @Test
  void anonymousIsRejectedOnEveryMutationEndpoint() throws Exception {
    for (String path : List.of("/tag", "/tag/1/rename", "/tag/1/delete", "/tag/1/move")) {
      int status = send(post(path, "", false)).statusCode();
      assertThat(status).as("anonymous POST %s", path).isEqualTo(401);
    }
  }

  @Test
  void mapAdminGetIsAllowed() throws Exception {
    assertThat(send(get(true)).statusCode()).isEqualTo(200);
  }

  @Test
  void mapAdminMutationWithoutCsrfTokenIsRejected() throws Exception {
    assertThat(send(post("/tag", "name=auth-test", true)).statusCode()).isEqualTo(403);
  }

  @Test
  void mapAdminMutationWithValidCsrfTokenSucceeds() throws Exception {
    // GET the page for the csrf_token cookie, then send it back as cookie and _csrf field.
    HttpResponse<String> page = send(get(true));
    String token =
        page.headers().allValues("set-cookie").stream()
            .filter(c -> c.startsWith("csrf_token="))
            .map(c -> c.substring("csrf_token=".length(), c.indexOf(';')))
            .findFirst()
            .orElseThrow(() -> new AssertionError("GET did not issue a csrf_token cookie"));

    var request =
        withAuth(
                HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + BASE + "/tag"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Cookie", "csrf_token=" + token)
                    .POST(
                        HttpRequest.BodyPublishers.ofString("_csrf=" + token + "&name=auth-test")),
                true)
            .build();
    assertThat(send(request).statusCode()).isEqualTo(303);
  }

  private HttpRequest get(boolean mapAdmin) {
    return withAuth(HttpRequest.newBuilder().uri(URI.create(baseUrl + BASE)).GET(), mapAdmin)
        .build();
  }

  private HttpRequest post(String path, String formBody, boolean mapAdmin) {
    return withAuth(
            HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + BASE + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody)),
            mapAdmin)
        .build();
  }

  private HttpRequest.Builder withAuth(HttpRequest.Builder builder, boolean mapAdmin) {
    if (mapAdmin) {
      builder.header("X-Auth-Email", "mapadmin@example.com").header("X-Auth-Groups", mapAdminGroup);
    }
    return builder;
  }

  private HttpResponse<String> send(HttpRequest request) throws Exception {
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
