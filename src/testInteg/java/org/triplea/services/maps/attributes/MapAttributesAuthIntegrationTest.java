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

/**
 * Authorization integration tests for the fully-gated attribute catalog: anonymous is rejected on
 * the GET render and on every one of the 8 mutation endpoints; a member is allowed through.
 *
 * <p>Under {@code @QuarkusTest} the app runs in TEST launch mode with {@code DEV_FAKE_AUTH} unset,
 * so identity is derived from the {@code X-Auth-*} headers (the post-nginx state): anonymous = no
 * headers, member = an email plus the member group.
 */
@QuarkusTest
@ExtendWith(IntegTestExtension.class)
class MapAttributesAuthIntegrationTest {

  private static final String BASE = "/support/admin/map/attributes";
  private static final String MEMBER_GROUP = "triplea-game:maintainers";

  @ConfigProperty(name = "quarkus.http.test-port", defaultValue = "8081")
  int testPort;

  private HttpClient httpClient;
  private String baseUrl;

  @BeforeEach
  void setUp() {
    httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    baseUrl = "http://localhost:" + testPort;
  }

  /** A gated mutation: path suffix under {@link #BASE} and its form body (null = no form body). */
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
  void memberGetIsAllowed() throws Exception {
    assertThat(send(get(true)).statusCode()).isEqualTo(200);
  }

  @Test
  void memberMutationIsAllowedThroughTheFilter() throws Exception {
    // createAttribute needs no pre-existing row; 303 means it passed the filter and
    // post-redirect-got.
    int status = send(post(new Mutation("/attribute", "name=auth-test"), true)).statusCode();
    assertThat(status).isEqualTo(303);
  }

  private HttpRequest get(boolean member) {
    return withAuth(HttpRequest.newBuilder().uri(URI.create(baseUrl + BASE)).GET(), member).build();
  }

  private HttpRequest post(Mutation mutation, boolean member) {
    var builder = HttpRequest.newBuilder().uri(URI.create(baseUrl + BASE + mutation.path()));
    if (mutation.formBody() != null) {
      builder
          .header("Content-Type", "application/x-www-form-urlencoded")
          .POST(HttpRequest.BodyPublishers.ofString(mutation.formBody()));
    } else {
      builder.POST(HttpRequest.BodyPublishers.noBody());
    }
    return withAuth(builder, member).build();
  }

  private static HttpRequest.Builder withAuth(HttpRequest.Builder builder, boolean member) {
    if (member) {
      builder.header("X-Auth-Email", "member@example.com").header("X-Auth-Groups", MEMBER_GROUP);
    }
    return builder;
  }

  private HttpResponse<String> send(HttpRequest request) throws Exception {
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
