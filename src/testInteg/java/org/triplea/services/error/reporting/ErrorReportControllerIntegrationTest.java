package org.triplea.services.error.reporting;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;

import com.google.gson.Gson;
import io.quarkus.test.junit.QuarkusTest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.triplea.IntegTestExtension;
import org.triplea.http.client.HttpHeaders;
import org.triplea.http.client.ServerPaths;
import org.triplea.http.client.error.report.CanUploadRequest;
import org.triplea.http.client.error.report.ErrorReportRequest;
import org.triplea.http.client.error.report.ErrorReportResponse;

@QuarkusTest
@ExtendWith(IntegTestExtension.class)
class ErrorReportControllerIntegrationTest {
  private static final Gson GSON = new Gson();

  private HttpClient httpClient;
  private String baseUrl;

  @ConfigProperty(name = "quarkus.http.test-port", defaultValue = "8081")
  int testPort;

  @BeforeEach
  void setUp() {
    httpClient = HttpClient.newHttpClient();
    baseUrl = "http://localhost:" + testPort;
  }

  @Disabled
  @Test
  void uploadErrorReport() throws IOException, InterruptedException {
    var requestBody =
        ErrorReportRequest.builder()
            .body("body")
            .title("error-report-title-" + String.valueOf(Math.random()).substring(0, 10))
            .gameVersion("version")
            .build();

    var httpRequest =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + ServerPaths.ERROR_REPORT_PATH))
            .header("Content-Type", "application/json")
            .header(HttpHeaders.SYSTEM_ID_HEADER, "test-system-id")
            .header(HttpHeaders.VERSION_HEADER, "test")
            .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)))
            .build();

    var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
    var errorReportResponse = GSON.fromJson(response.body(), ErrorReportResponse.class);

    assertThat(errorReportResponse.getGithubIssueLink(), is(notNullValue()));
  }

  @Test
  void canUploadErrorReport() throws IOException, InterruptedException {
    var requestBody = CanUploadRequest.builder().gameVersion("2.0").errorTitle("title").build();

    var httpRequest =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + ServerPaths.CAN_UPLOAD_ERROR_REPORT_PATH))
            .header("Content-Type", "application/json")
            .header(HttpHeaders.SYSTEM_ID_HEADER, "test-system-id")
            .header(HttpHeaders.VERSION_HEADER, "test")
            .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)))
            .build();

    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
  }
}
