package org.triplea.services.error.reporting;

import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jdbi.v3.core.Jdbi;
import org.triplea.http.client.HttpHeaders;
import org.triplea.http.client.ServerPaths;
import org.triplea.http.client.error.report.ErrorReportRequest;
import org.triplea.http.client.error.report.ErrorReportResponse;
import org.triplea.http.client.github.GithubClient;
import org.triplea.services.error.reporting.upload.CreateIssueParams;
import org.triplea.services.error.reporting.upload.ErrorReportModule;
import org.triplea.utils.IpAddressExtractor;

/// Http controller that binds the error upload endpoint with the error report upload handler.
///
/// The full path is declared on the class (not `@Path("/")` + a method-level path): the
/// `ServerPaths` constants begin with a leading slash, so a class path of `/` would concatenate to
/// a double slash (`//support/error-report`) and never match. See also [ErrorReportController]'s
/// sibling, the can-upload check controller, which follows the same single-path-per-class pattern.
@Path(ServerPaths.ERROR_REPORT_PATH)
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

  @PostConstruct
  void init() {
    var githubClient = GithubClient.build(githubApiToken.orElse(""), tripleaOrgName);
    errorReportIngestion = ErrorReportModule.build(githubClient, errorReportingRepo, jdbi);
  }

  /// Endpoint where users can submit an error report, the server will use an API token of a generic
  /// user to in turn create a GitHub issue using the data from the error report.
  @POST
  public ErrorReportResponse uploadErrorReport(
      @Context RoutingContext routingContext, ErrorReportRequest errorReport) {

    if (errorReport == null
        || errorReport.getBody() == null
        || errorReport.getTitle() == null
        || errorReport.getGameVersion() == null) {
      throw new IllegalArgumentException("Missing attribute, body, title, or game version");
    }

    return errorReportIngestion.createErrorReport(
        CreateIssueParams.builder()
            .ip(IpAddressExtractor.extractIpAddress(routingContext))
            .systemId(routingContext.request().getHeader(HttpHeaders.VERSION_HEADER))
            .errorReportRequest(errorReport)
            .build());
  }
}
