package org.triplea.services.maps.attributes;

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

/// Authorization integration tests for the fully-gated attribute catalog: anonymous is rejected on
/// the GET render and on every one of the 8 mutation endpoints; a MapAdmin is allowed through, but
/// only once they supply a valid double-submit CSRF token (a MapAdmin POST without one is 403).
///
/// Under `@QuarkusTest` the app runs in TEST launch mode with `DEV_FAKE_AUTH` unset,
/// so identity is derived from the `X-Auth-*` headers (the post-nginx state): anonymous = no
/// headers, MapAdmin = an email plus the MapAdmin group.
@QuarkusTest
@ExtendWith(IntegTestExtension.class)
class MapAttributesAuthIntegrationTest {

  private static final String BASE = "/support/admin/map/attributes";

  @ConfigProperty(name = "quarkus.http.test-port", defaultValue = "8081")
  int testPort;

  // A MapAdmin carries whatever group the app is configured to match (driven by GITHUB_ADMIN_TEAM).
  @ConfigProperty(name = "app.auth.map-admin-group")
  String mapAdminGroup;

  private HttpClient httpClient;
  private String baseUrl;

  @BeforeEach
  void setUp() {
    httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    baseUrl = "http://localhost:" + testPort;
  }

  /// A gated mutation: path suffix under [#BASE] and its form body (null = no form body).
  private record Mutation(String path, String formBody) {}

  private static List<Mutation> mutationEndpoints() {
    return List.of(
        new Mutation("/attribute", "name=auth-test"),
        new Mutation("/attribute/1/rename", "name=auth-test"),
        new Mutation("/attribute/1/delete", null),
        new Mutation("/attribute/1/move", "direction=up"),
        new Mutation("/attribute/1/value", "value=auth-test"),
        new Mutation("/value/1/rename", "value=auth-test"),
        new Mutation("/value/1/delete", null),
        new Mutation("/value/1/move", "direction=up"));
  }

  @Test
  void anonymousGetIsRejected() throws Exception {
    assertThat(send(get(false)).statusCode()).isEqualTo(401);
  }

  @Test
  void anonymousIsRejectedOnEveryMutationEndpoint() throws Exception {
    for (Mutation mutation : mutationEndpoints()) {
      int status = send(post(mutation, false)).statusCode();
      assertThat(status).as("anonymous POST %s", mutation.path()).isEqualTo(401);
    }
  }

  @Test
  void mapAdminGetIsAllowed() throws Exception {
    assertThat(send(get(true)).statusCode()).isEqualTo(200);
  }

  @Test
  void mapAdminMutationWithoutCsrfTokenIsRejected() throws Exception {
    // Passes the MapAdmin filter (MapAdmin headers) but carries no CSRF token -> 403.
    int status = send(post(new Mutation("/attribute", "name=auth-test"), true)).statusCode();
    assertThat(status).isEqualTo(403);
  }

  @Test
  void mapAdminMutationWithValidCsrfTokenSucceeds() throws Exception {
    // Full double-submit flow: GET the page to obtain the csrf_token cookie, then POST it back as
    // both the cookie and the _csrf form field. 303 means it passed the MapAdmin + CSRF filters
    // and post-redirect-got. createAttribute needs no pre-existing row.
    String token = csrfTokenFromGet();
    int status =
        send(postWithCsrf(new Mutation("/attribute", "name=auth-test"), token)).statusCode();
    assertThat(status).isEqualTo(303);
  }

  /// Issues a MapAdmin GET and pulls the `csrf_token` value out of its `Set-Cookie`.
  private String csrfTokenFromGet() throws Exception {
    HttpResponse<String> response = send(get(true));
    assertThat(response.statusCode()).isEqualTo(200);
    return response.headers().allValues("set-cookie").stream()
        .filter(c -> c.startsWith("csrf_token="))
        .map(c -> c.substring("csrf_token=".length(), c.indexOf(';')))
        .findFirst()
        .orElseThrow(() -> new AssertionError("GET did not issue a csrf_token cookie"));
  }

  private HttpRequest get(boolean mapAdmin) {
    return withAuth(HttpRequest.newBuilder().uri(URI.create(baseUrl + BASE)).GET(), mapAdmin)
        .build();
  }

  private HttpRequest post(Mutation mutation, boolean mapAdmin) {
    var builder = HttpRequest.newBuilder().uri(URI.create(baseUrl + BASE + mutation.path()));
    if (mutation.formBody() != null) {
      builder
          .header("Content-Type", "application/x-www-form-urlencoded")
          .POST(HttpRequest.BodyPublishers.ofString(mutation.formBody()));
    } else {
      builder.POST(HttpRequest.BodyPublishers.noBody());
    }
    return withAuth(builder, mapAdmin).build();
  }

  /// A MapAdmin POST carrying the CSRF token in both the cookie and the `_csrf` form field.
  private HttpRequest postWithCsrf(Mutation mutation, String token) {
    String body = "_csrf=" + token + (mutation.formBody() == null ? "" : "&" + mutation.formBody());
    var builder =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + BASE + mutation.path()))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Cookie", "csrf_token=" + token)
            .POST(HttpRequest.BodyPublishers.ofString(body));
    return withAuth(builder, true).build();
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
