package org.triplea.services.maps.tags;

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

/// Verifies the HTMX behaviour of the tag-list mutations: a POST carrying the `HX-Request` header
/// returns `200` with a re-rendered fragment (the row for rename, else the whole list) and never a
/// full HTML document, while the identical POST without the header keeps the no-JavaScript `303`.
///
/// Seeds `map_tag_dao.yml`: 7200 "fantasy", 7100 "naval", 7300 "world war 2", in that order.
@DataSet(value = "map_tag_dao.yml", useSequenceFiltering = false)
@QuarkusTest
@ExtendWith(DbOnlyExtension.class)
@ExtendWith(DBUnitExtension.class)
class MapTagsHtmxIntegrationTest {

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
  void createTagReturnsTheWholeList() throws Exception {
    HttpResponse<String> response = post("/tag", "name=modern", true);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body())
        .doesNotContain("<!DOCTYPE", "<html")
        .contains("id=\"tag-list\"", "id=\"tag-7100\"", "modern");
  }

  @Test
  void renameTagReturnsOnlyThatRow() throws Exception {
    HttpResponse<String> response = post("/tag/7100/rename", "name=sea", true);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body())
        .doesNotContain("<!DOCTYPE", "<html", "id=\"tag-list\"", "id=\"tag-7200\"")
        .contains("id=\"tag-7100\"", "sea");
  }

  @Test
  void moveTagReturnsTheReorderedList() throws Exception {
    HttpResponse<String> response = post("/tag/7100/move", "direction=up", true);

    assertThat(response.statusCode()).isEqualTo(200);
    String body = response.body();
    assertThat(body).contains("id=\"tag-list\"");
    assertThat(body.indexOf("id=\"tag-7100\"")).isLessThan(body.indexOf("id=\"tag-7200\""));
  }

  @Test
  void deletingEveryTagLeavesTheListEmptyState() throws Exception {
    post("/tag/7100/delete", null, true);
    post("/tag/7200/delete", null, true);
    HttpResponse<String> response = post("/tag/7300/delete", null, true);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("id=\"tag-list\"", "No tags defined yet");
  }

  @Test
  void withoutHxRequestHeaderTheEndpointStillRedirects() throws Exception {
    HttpResponse<String> response = post("/tag/7100/rename", "name=sea", false);

    assertThat(response.statusCode()).isEqualTo(303);
    // seeOther resolves SELF to an absolute URL (http://host:port/support/...); match the suffix.
    assertThat(response.headers().firstValue("location").orElseThrow()).endsWith(BASE);
  }

  /// A MapAdmin POST carrying a valid double-submit CSRF token (cookie + field).
  private HttpResponse<String> post(String path, String formBody, boolean htmx) throws Exception {
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
    return send(builder.build());
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
