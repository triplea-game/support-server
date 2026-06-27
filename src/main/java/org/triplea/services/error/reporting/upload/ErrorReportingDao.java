package org.triplea.services.error.reporting.upload;

import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.customizer.Bind;

/// DAO class for error reporting functionality.
@RequiredArgsConstructor
public class ErrorReportingDao {
  private final Jdbi jdbi;

  /// Inserts a new record indicating a user has submitted an error report at a given date.
  public void insertHistoryRecord(InsertHistoryRecordParams insertHistoryRecordParams) {
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(
                    """
                        insert into error_report_history
                        (user_ip, system_id, report_title, game_version, created_issue_link)
                        values
                        (:ip, :systemId, :title, :gameVersion, :githubIssueLink)
                        """)
                .bind("ip", insertHistoryRecordParams.getIp())
                .bind("systemId", insertHistoryRecordParams.getSystemId())
                .bind("title", insertHistoryRecordParams.getTitle())
                .bind("gameVersion", insertHistoryRecordParams.getGameVersion())
                .bind("githubIssueLink", insertHistoryRecordParams.getGithubIssueLink())
                .execute());
  }

  /// Method to clean up old records from the error report history table. This is to avoid the table
  /// from growing very large.
  ///
  /// @param purgeSinceDate Any records older than this date will be removed.
  public void purgeOld(@Bind("purgeSinceDate") Instant purgeSinceDate) {
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(
                    "delete from error_report_history where date_created < :purgeSinceDate")
                .bind("purgeSinceDate", purgeSinceDate)
                .execute());
  }

  public Optional<String> getErrorReportLink(
      @Bind("reportTitle") String reportTitle, @Bind("gameVersion") String gameVersion) {
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery(
                    """
                        select created_issue_link
                          from error_report_history
                          where report_title = :reportTitle
                            and game_version = :gameVersion
                        """)
                .bind("reportTitle", reportTitle)
                .bind("gameVersion", gameVersion)
                .mapTo(String.class)
                .findOne());
  }
}
