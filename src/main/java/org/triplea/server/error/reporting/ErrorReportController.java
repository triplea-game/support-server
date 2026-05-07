package org.triplea.server.error.reporting;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import java.util.Optional;
import java.util.function.Function;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jdbi.v3.core.Jdbi;
import org.triplea.http.client.HttpHeaders;
import org.triplea.http.client.ServerPaths;
import org.triplea.http.client.error.report.CanUploadErrorReportResponse;
import org.triplea.http.client.error.report.CanUploadRequest;
import org.triplea.http.client.error.report.ErrorReportRequest;
import org.triplea.http.client.error.report.ErrorReportResponse;
import org.triplea.http.client.github.GithubClient;
import org.triplea.server.IpAddressExtractor;
import org.triplea.server.error.reporting.upload.CanUploadErrorReportStrategy;
import org.triplea.server.error.reporting.upload.CreateIssueParams;
import org.triplea.server.error.reporting.upload.ErrorReportModule;

/** Http controller that binds the error upload endpoint with the error report upload handler. */
@Path("/")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class ErrorReportController {

  @Inject Jdbi jdbi;

  @ConfigProperty(name = "app.github-api-token")
  Optional<String> githubApiToken;

  @ConfigProperty(name = "app.triplea-org-name", defaultValue = "triplea-game")
  String tripleaOrgName;

  @ConfigProperty(name = "app.error-reporting-repo", defaultValue = "triplea")
  String errorReportingRepo;

  private ErrorReportModule errorReportIngestion;
  private Function<CanUploadRequest, CanUploadErrorReportResponse> canReportModule;

  @PostConstruct
  void init() {
    var githubClient = GithubClient.build(githubApiToken.orElse(""), tripleaOrgName);
    errorReportIngestion = ErrorReportModule.build(githubClient, errorReportingRepo, jdbi);
    canReportModule = CanUploadErrorReportStrategy.build(jdbi);
  }

  @POST
  @Path(ServerPaths.CAN_UPLOAD_ERROR_REPORT_PATH)
  public CanUploadErrorReportResponse canUploadErrorReport(CanUploadRequest canUploadRequest) {
    if (canUploadRequest == null
        || canUploadRequest.getErrorTitle() == null
        || canUploadRequest.getGameVersion() == null) {
      throw new IllegalArgumentException("Missing request attributes title or game version");
    }
    return canReportModule.apply(canUploadRequest);
  }

  /**
   * Endpoint where users can submit an error report, the server will use an API token of a generic
   * user to in turn create a GitHub issue using the data from the error report.
   */
  @POST
  @Path(ServerPaths.ERROR_REPORT_PATH)
  public ErrorReportResponse uploadErrorReport(
      @Context HttpServletRequest request, ErrorReportRequest errorReport) {

    if (errorReport == null
        || errorReport.getBody() == null
        || errorReport.getTitle() == null
        || errorReport.getGameVersion() == null) {
      throw new IllegalArgumentException("Missing attribute, body, title, or game version");
    }

    return errorReportIngestion.createErrorReport(
        CreateIssueParams.builder()
            .ip(IpAddressExtractor.extractIpAddress(request))
            .systemId(request.getHeader(HttpHeaders.VERSION_HEADER))
            .errorReportRequest(errorReport)
            .build());
  }
}
