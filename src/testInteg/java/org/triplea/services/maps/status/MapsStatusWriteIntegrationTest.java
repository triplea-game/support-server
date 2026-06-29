package org.triplea.services.maps.status;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.database.rider.core.api.dataset.DataSet;
import com.github.database.rider.junit5.DBUnitExtension;
import io.quarkus.test.junit.QuarkusTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.triplea.DbOnlyExtension;

/// End-to-end tests for the status page's MapAdmin attribute-edit endpoint
/// (`POST /support/maps/status/{mapId}/attribute/{attributeId}`).
///
/// Covers HTMX behaviour (a POST carrying `HX-Request` returns the re-rendered attributes `<td>`
/// fragment, never a full document; without it, the no-JS `303` redirect) and authorization (the
/// public GET is open, but the mutation is MapAdmin-only and CSRF-protected).
///
/// Seeds map 10 with era=ancient (attribute 3300 -> value 100) and difficulty=easy (8800 -> 200);
/// the catalog also has difficulty value 201 ("hard"). Every test re-seeds, so order is irrelevant.
@DataSet(value = "map_status.yml", useSequenceFiltering = false)
@QuarkusTest
@ExtendWith(DbOnlyExtension.class)
@ExtendWith(DBUnitExtension.class)
class MapsStatusWriteIntegrationTest {

  private static final String PATH = "/support/maps/status";
  private static final String SET_DIFFICULTY = PATH + "/10/attribute/8800";
  private static final String CLEAR_ERA = PATH + "/10/attribute/3300";
  private static final String ADMIN_DISABLE = PATH + "/10/admin-disable";
  private static final String ADMIN_ENABLE = PATH + "/10/admin-enable";

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
  void setAttributeViaHtmxReturnsOnlyTheCellWithNewSelection() throws Exception {
    var response = postHtmx(SET_DIFFICULTY, "valueId=201"); // difficulty -> hard

    assertThat(response.statusCode()).isEqualTo(200);
    String body = response.body();
    assertThat(body).doesNotContain("<!DOCTYPE", "<html", "<table");
    assertThat(body).contains("id=\"map-attributes-10\"");
    assertThat(body).contains("value=\"201\" selected"); // hard is now the chosen option
    assertThat(body).doesNotContain("value=\"200\" selected"); // easy no longer chosen
  }

  @Test
  void clearAttributeViaHtmxLeavesNoValueSelected() throws Exception {
    var response = postHtmx(CLEAR_ERA, "valueId="); // era -> none

    assertThat(response.statusCode()).isEqualTo(200);
    String body = response.body();
    assertThat(body).contains("id=\"map-attributes-10\"");
    assertThat(body).doesNotContain("value=\"100\" selected"); // ancient no longer chosen
  }

  @Test
  void withoutHxRequestHeaderTheEndpointRedirects() throws Exception {
    var response = postNoHx(SET_DIFFICULTY, "valueId=201");

    assertThat(response.statusCode()).isEqualTo(303);
    assertThat(response.headers().firstValue("location").orElseThrow()).endsWith(PATH);
  }

  @Test
  void anonymousIsRejected() throws Exception {
    var request =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + SET_DIFFICULTY))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("valueId=201"))
            .build();

    assertThat(send(request).statusCode()).isEqualTo(401);
  }

  @Test
  void adminDisableViaHtmxReturnsCellShowingDisabledWithReason() throws Exception {
    var response = postHtmx(ADMIN_DISABLE, "reason=spam+map");

    assertThat(response.statusCode()).isEqualTo(200);
    String body = response.body();
    assertThat(body).doesNotContain("<!DOCTYPE", "<html", "<table");
    assertThat(body).contains("id=\"map-admin-10\"");
    assertThat(body).contains("Disabled");
    assertThat(body).contains("spam map"); // the admin's reason is shown
    assertThat(body).contains("/admin-enable"); // a disabled map now offers the Enable control
  }

  @Test
  void adminDisableWithoutReasonIsRejected() throws Exception {
    var response = postHtmx(ADMIN_DISABLE, "reason=");

    assertThat(response.statusCode()).isEqualTo(400);
  }

  @Test
  @DataSet(value = "map_status_admin_pending.yml", useSequenceFiltering = false)
  void adminEnableViaHtmxReturnsCellShowingApproved() throws Exception {
    var response = postHtmx(ADMIN_ENABLE, null);

    assertThat(response.statusCode()).isEqualTo(200);
    String body = response.body();
    assertThat(body).doesNotContain("<!DOCTYPE", "<html", "<table");
    assertThat(body).contains("id=\"map-admin-10\"");
    assertThat(body).contains("Approved");
    assertThat(body).contains("/admin-disable"); // an approved map now offers the Disable control
  }

  @Test
  void adminDisableWithoutHxRequestHeaderRedirects() throws Exception {
    var response = postNoHx(ADMIN_DISABLE, "reason=spam+map");

    assertThat(response.statusCode()).isEqualTo(303);
    assertThat(response.headers().firstValue("location").orElseThrow()).endsWith(PATH);
  }

  @Test
  void anonymousCannotAdminDisable() throws Exception {
    var request =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + ADMIN_DISABLE))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("reason=spam+map"))
            .build();

    assertThat(send(request).statusCode()).isEqualTo(401);
  }

  @Test
  void mapAdminWithoutCsrfTokenIsRejected() throws Exception {
    var request =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + SET_DIFFICULTY))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("X-Auth-Email", "mapadmin@example.com")
            .header("X-Auth-Groups", mapAdminGroup)
            .POST(HttpRequest.BodyPublishers.ofString("valueId=201"))
            .build();

    assertThat(send(request).statusCode()).isEqualTo(403);
  }

  // --- helpers: a MapAdmin POST carrying a valid double-submit CSRF token (cookie + field) ---

  private HttpResponse<String> postHtmx(String path, String formBody) throws Exception {
    return send(buildPost(path, formBody, true));
  }

  private HttpResponse<String> postNoHx(String path, String formBody) throws Exception {
    return send(buildPost(path, formBody, false));
  }

  private HttpRequest buildPost(String path, String formBody, boolean htmx) throws Exception {
    String token = csrfTokenFromGet();
    String body = "_csrf=" + token + (formBody == null ? "" : "&" + formBody);
    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Cookie", "csrf_token=" + token)
            .header("X-Auth-Email", "mapadmin@example.com")
            .header("X-Auth-Groups", mapAdminGroup)
            .POST(HttpRequest.BodyPublishers.ofString(body));
    if (htmx) {
      builder.header("HX-Request", "true");
    }
    return builder.build();
  }

  /// Issues a MapAdmin GET of the status page and pulls the `csrf_token` from its `Set-Cookie`.
  private String csrfTokenFromGet() throws Exception {
    HttpResponse<String> response =
        send(
            HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + PATH))
                .header("X-Auth-Email", "mapadmin@example.com")
                .header("X-Auth-Groups", mapAdminGroup)
                .GET()
                .build());
    assertThat(response.statusCode()).isEqualTo(200);
    return response.headers().allValues("set-cookie").stream()
        .filter(c -> c.startsWith("csrf_token="))
        .map(c -> c.substring("csrf_token=".length(), c.indexOf(';')))
        .findFirst()
        .orElseThrow(() -> new AssertionError("GET did not issue a csrf_token cookie"));
  }

  private HttpResponse<String> send(HttpRequest request) throws Exception {
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
