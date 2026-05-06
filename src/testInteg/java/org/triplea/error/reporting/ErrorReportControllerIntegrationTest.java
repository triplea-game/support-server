package org.triplea.error.reporting;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import java.net.URI;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.triplea.http.client.LobbyHttpClientConfig;
import org.triplea.http.client.error.report.CanUploadRequest;
import org.triplea.http.client.error.report.ErrorReportClient;
import org.triplea.http.client.error.report.ErrorReportRequest;
import org.triplea.http.client.error.report.ErrorReportResponse;
import org.triplea.maps.IntegTestExtension;

@QuarkusTest
@ExtendWith(IntegTestExtension.class)
class ErrorReportControllerIntegrationTest {
  private ErrorReportClient client;

  @ConfigProperty(name = "quarkus.http.test-port", defaultValue = "8081")
  int testPort;

  @BeforeEach
  void setUp() {
    LobbyHttpClientConfig.setConfig(
        LobbyHttpClientConfig.builder().systemId("test-system-id").clientVersion("test").build());
    client = ErrorReportClient.newClient(URI.create("http://localhost:" + testPort));
  }

  @Disabled
  @Test
  void uploadErrorReport() {
    final ErrorReportResponse response =
        client.uploadErrorReport(
            ErrorReportRequest.builder()
                .body("body")
                .title("error-report-title-" + String.valueOf(Math.random()).substring(0, 10))
                .gameVersion("version")
                .build());

    assertThat(response.getGithubIssueLink(), is(notNullValue()));
  }

  @Test
  void canUploadErrorReport() {
    client.canUploadErrorReport(
        CanUploadRequest.builder().gameVersion("2.0").errorTitle("title").build());
  }
}
