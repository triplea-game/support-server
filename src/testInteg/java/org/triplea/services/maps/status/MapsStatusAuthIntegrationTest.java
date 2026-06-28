package org.triplea.services.maps.status;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.triplea.IntegTestExtension;

/// Conditional-render integration tests for the public map status page. The GET is public (200 for
/// everyone); what differs is the auth-aware scaffolding: a member sees the logout link and the
/// member-only region, an anonymous caller sees neither.
///
/// Identity is derived from the `X-Auth-*` headers (the post-nginx state in TEST launch
/// mode, `DEV_FAKE_AUTH` unset): anonymous = no headers, member = email + the member group.
@QuarkusTest
@ExtendWith(IntegTestExtension.class)
class MapsStatusAuthIntegrationTest {

  private static final String PATH = "/support/maps/status";
  private static final String LOGOUT_LINK = "/oauth2/sign_out";
  private static final String LOGIN_LINK = "/oauth2/start";
  private static final String MEMBER_REGION = "data-member-tools";

  @ConfigProperty(name = "quarkus.http.test-port", defaultValue = "8081")
  int testPort;

  // The exact group string a member carries is whatever the app is configured to match, so the test
  // stays correct if app.auth.member-group (driven by GITHUB_ADMIN_TEAM) changes.
  @ConfigProperty(name = "app.auth.member-group")
  String memberGroup;

  private HttpClient httpClient;
  private String baseUrl;

  @BeforeEach
  void setUp() {
    httpClient = HttpClient.newHttpClient();
    baseUrl = "http://localhost:" + testPort;
  }

  @Test
  void anonymousSeesReadOnlyPage() throws Exception {
    var response = get(false);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).doesNotContain(LOGOUT_LINK).doesNotContain(MEMBER_REGION);
    assertThat(response.body()).contains(LOGIN_LINK);
  }

  @Test
  void memberSeesLogoutAndMemberRegion() throws Exception {
    var response = get(true);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains(LOGOUT_LINK).contains(MEMBER_REGION);
  }

  private HttpResponse<String> get(boolean member) throws Exception {
    var builder = HttpRequest.newBuilder().uri(URI.create(baseUrl + PATH)).GET();
    if (member) {
      builder.header("X-Auth-Email", "member@example.com").header("X-Auth-Groups", memberGroup);
    }
    return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }
}
