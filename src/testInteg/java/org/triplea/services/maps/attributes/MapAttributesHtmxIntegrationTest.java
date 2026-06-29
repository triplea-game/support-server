package org.triplea.services.maps.attributes;

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

/// Verifies the HTMX behaviour of the attribute-catalog mutations: a POST carrying the `HX-Request`
/// header returns `200` with the *smallest correct* re-rendered fragment (a value row, a section,
/// or the whole list) and never a full HTML document, while the identical POST without the header
/// keeps the no-JavaScript `303` redirect.
///
/// Seeds a known catalog (`map_attribute_dao.yml`): attribute 3300 "era" with values 100/101/102,
/// and attribute 8800 "difficulty" with values 200 "easy" / 201 "hard". Every mutation re-seeds, so
/// the tests stay independent despite mutating rows.
@DataSet(value = "map_attribute_dao.yml", useSequenceFiltering = false)
@QuarkusTest
@ExtendWith(DbOnlyExtension.class)
@ExtendWith(DBUnitExtension.class)
class MapAttributesHtmxIntegrationTest {

  private static final String BASE = "/support/admin/map/attributes";

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
  void renameValueReturnsOnlyThatRow() throws Exception {
    HttpResponse<String> response = postHtmx("/value/100/rename", "value=stone-age");

    assertThat(response.statusCode()).isEqualTo(200);
    String body = response.body();
    assertThat(body).doesNotContain("<!DOCTYPE", "<html", "<section");
    assertThat(body).contains("id=\"value-100\"").contains("stone-age");
  }

  @Test
  void renameAttributeReturnsItsSection() throws Exception {
    HttpResponse<String> response = postHtmx("/attribute/3300/rename", "name=epoch");

    assertThat(response.statusCode()).isEqualTo(200);
    String body = response.body();
    assertThat(body).doesNotContain("<!DOCTYPE", "<html", "id=\"attribute-list\"");
    assertThat(body).contains("id=\"attribute-3300\"").contains("epoch");
  }

  @Test
  void createValueReturnsTheParentSection() throws Exception {
    HttpResponse<String> response = postHtmx("/attribute/8800/value", "value=medium");

    assertThat(response.statusCode()).isEqualTo(200);
    String body = response.body();
    assertThat(body).doesNotContain("<!DOCTYPE", "<html", "id=\"attribute-list\"");
    assertThat(body).contains("id=\"attribute-8800\"").contains("medium").contains("easy");
  }

  @Test
  void deleteValueReturnsTheParentSection() throws Exception {
    HttpResponse<String> response = postHtmx("/value/201/delete", null);

    assertThat(response.statusCode()).isEqualTo(200);
    String body = response.body();
    assertThat(body).doesNotContain("<!DOCTYPE", "<html", "id=\"attribute-list\"");
    assertThat(body).contains("id=\"attribute-8800\"").contains("easy").doesNotContain("hard");
  }

  @Test
  void deletingEveryValueLeavesTheSectionEmptyState() throws Exception {
    postHtmx("/value/200/delete", null);
    HttpResponse<String> response = postHtmx("/value/201/delete", null);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("id=\"attribute-8800\"").contains("No values yet");
  }

  @Test
  void createAttributeReturnsTheWholeList() throws Exception {
    HttpResponse<String> response = postHtmx("/attribute", "name=scale");

    assertThat(response.statusCode()).isEqualTo(200);
    String body = response.body();
    assertThat(body).doesNotContain("<!DOCTYPE", "<html");
    assertThat(body)
        .contains("id=\"attribute-list\"")
        .contains("id=\"attribute-3300\"")
        .contains("scale");
  }

  @Test
  void deletingEveryAttributeLeavesTheListEmptyState() throws Exception {
    postHtmx("/attribute/3300/delete", null);
    HttpResponse<String> response = postHtmx("/attribute/8800/delete", null);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body())
        .contains("id=\"attribute-list\"")
        .contains("No attributes defined yet");
  }

  @Test
  void withoutHxRequestHeaderTheEndpointStillRedirects() throws Exception {
    HttpResponse<String> response = postNoHx("/value/100/rename", "value=stone-age");

    assertThat(response.statusCode()).isEqualTo(303);
    // seeOther resolves SELF to an absolute URL (http://host:port/support/...); match the suffix.
    assertThat(response.headers().firstValue("location").orElseThrow()).endsWith(BASE);
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
            .uri(URI.create(baseUrl + BASE + path))
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

  /// Issues a MapAdmin GET and pulls the `csrf_token` value out of its `Set-Cookie`.
  private String csrfTokenFromGet() throws Exception {
    HttpResponse<String> response =
        send(
            HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + BASE))
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
