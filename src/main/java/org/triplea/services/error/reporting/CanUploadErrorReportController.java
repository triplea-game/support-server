package org.triplea.services.error.reporting;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.function.Function;
import org.jdbi.v3.core.Jdbi;
import org.triplea.http.client.ServerPaths;
import org.triplea.http.client.error.report.CanUploadErrorReportResponse;
import org.triplea.http.client.error.report.CanUploadRequest;
import org.triplea.services.error.reporting.upload.CanUploadErrorReportStrategy;

/// Http controller for the pre-flight check that tells a client whether it may upload an error
/// report (eg: not a duplicate of an existing report).
///
/// The full path is declared on the class rather than `@Path("/")` + a method path: the
/// `ServerPaths` constants begin with a leading slash, so a class path of `/` would concatenate to
/// a double slash (`//support/error-report-check`) and never match. Split out from
/// [ErrorReportController] so each endpoint is its own single-path root resource.
@Path(ServerPaths.CAN_UPLOAD_ERROR_REPORT_PATH)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class CanUploadErrorReportController {

  @Inject Jdbi jdbi;

  private Function<CanUploadRequest, CanUploadErrorReportResponse> canReportModule;

  @PostConstruct
  void init() {
    canReportModule = CanUploadErrorReportStrategy.build(jdbi);
  }

  @POST
  public CanUploadErrorReportResponse canUploadErrorReport(CanUploadRequest canUploadRequest) {
    if (canUploadRequest == null
        || canUploadRequest.getErrorTitle() == null
        || canUploadRequest.getGameVersion() == null) {
      throw new IllegalArgumentException("Missing request attributes title or game version");
    }
    return canReportModule.apply(canUploadRequest);
  }
}
