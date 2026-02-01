package org.triplea.error.reporting;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;

import java.net.URI;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.server.Uri;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.triplea.http.client.LobbyHttpClientConfig;
import org.triplea.http.client.error.report.CanUploadRequest;
import org.triplea.http.client.error.report.ErrorReportClient;
import org.triplea.http.client.error.report.ErrorReportRequest;
import org.triplea.http.client.error.report.ErrorReportResponse;
import org.triplea.maps.IntegTestExtension;

@ExtendWith(IntegTestExtension.class)
class ErrorReportControllerIntegrationTest {
  private final ErrorReportClient client;

  ErrorReportControllerIntegrationTest(URI serverUri) {
    client = ErrorReportClient.newClient(serverUri);
  }

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
