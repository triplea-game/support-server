package org.triplea.services.maps.indexing;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.triplea.http.client.github.MapRepoListing;

@AllArgsConstructor
public class MapIndexDao {
  /// Both `map_index.disable_reason` and `map_indexing_status.error_message` are `varchar(4000)`;
  /// indexing error text is truncated to fit.
  private static final int MAX_ERROR_TEXT_LENGTH = 4000;

  private final Jdbi jdbi;

  /// Upserts a map indexing result into the map_index table.
  void upsert(MapIndex mapIndex) {
    String query =
        "insert into map_index("
            + "    map_name, repo_url, default_branch, description, "
            + "    download_url, preview_image_url, download_size_bytes, last_commit_date, "
            // A brand-new repo starts unapproved ("pending approval"), hidden from the public
            // listing until a MapAdmin approves it, no matter its indexing health. The ON CONFLICT
            // update below never names the admin columns, so an existing map's approval is kept.
            + "    admin_enabled, admin_disable_reason)\n"
            + "values("
            + "     :mapName, :mapRepoUri, :defaultBranch, :description, "
            + "     :downloadUri, :previewImageUri, :mapDownloadSizeInBytes, :lastCommitDate, "
            + "     false, 'pending approval')\n"
            + "on conflict(repo_url)\n"
            + "do update set\n"
            + "   map_name = :mapName,"
            + "   description = :description,"
            + "   default_branch = :defaultBranch,"
            + "   download_url = :downloadUri,"
            + "   preview_image_url = :previewImageUri,"
            + "   download_size_bytes = :mapDownloadSizeInBytes,"
            + "   last_commit_date = :lastCommitDate,"
            // A successful (re-)index re-enables the map and clears any disable reason, so a repo
            // that was disabled (e.g. marked 'DELETED' after vanishing from Github) comes back
            // enabled the moment it indexes cleanly again.
            + "   enabled = true,"
            + "   disable_reason = null";
    jdbi.withHandle(handle -> handle.createUpdate(query).bindBean(mapIndex).execute());
  }

  /// Upserts a *disabled* map_index row carrying an indexing error in `disable_reason`. For a
  /// brand-new repo this inserts a placeholder row from the values the caller could still derive
  /// (repo name as map name, size 0, the commit date already fetched from Github). For a repo that
  /// was previously indexed, the existing data columns are preserved and only `enabled`/
  /// `disable_reason` are flipped, so a map's real metadata is not overwritten by an error.
  void upsertDisabled(MapIndex mapIndex, String disableReason) {
    String reason = truncate(disableReason);
    String query =
        "insert into map_index("
            + "    map_name, repo_url, default_branch, description, "
            + "    download_url, preview_image_url, download_size_bytes, last_commit_date, "
            + "    enabled, disable_reason, "
            // As in upsert(): a brand-new repo starts unapproved, independent of its indexing
            // health. The ON CONFLICT update leaves the admin columns alone.
            + "    admin_enabled, admin_disable_reason)\n"
            + "values("
            + "     :mapName, :mapRepoUri, :defaultBranch, :description, "
            + "     :downloadUri, :previewImageUri, :mapDownloadSizeInBytes, :lastCommitDate, "
            + "     false, :disableReason, "
            + "     false, 'pending approval')\n"
            + "on conflict(repo_url)\n"
            + "do update set\n"
            + "   enabled = false,"
            + "   disable_reason = :disableReason,"
            + "   date_updated = now()";
    jdbi.withHandle(
        handle ->
            handle.createUpdate(query).bindBean(mapIndex).bind("disableReason", reason).execute());
  }

  /// Disables maps whose repo is no longer present on Github (not in the parameter list) by
  /// setting `enabled = false` with reason 'DELETED'. The row is kept rather than deleted so a
  /// repo that later reappears can be re-enabled by {@link #upsert}. Only currently-enabled rows
  /// are touched, leaving an existing disable reason (e.g. an admin disable) intact.
  int disableMapsNotIn(List<String> mapUriList) {
    String update =
        "update map_index"
            + " set enabled = false, disable_reason = 'DELETED', date_updated = now()"
            + " where repo_url not in(<mapUriList>) and enabled";
    return jdbi.withHandle(
        handle -> handle.createUpdate(update).bindList("mapUriList", mapUriList).execute());
  }

  Optional<Instant> getLastCommitDate(@Bind("repoUrl") String repoUrl) {
    String select = "select last_commit_date from map_index where repo_url = :repoUrl";
    return jdbi.withHandle(
        handle ->
            handle.createQuery(select).bind("repoUrl", repoUrl).mapTo(Instant.class).findOne());
  }

  /// Records the outcome of an indexing pass for one repo in `map_indexing_status` (one row per
  /// repo, latest result only). `last_indexing_attempt` advances on every call; `last_success`
  /// only advances on a successful index; `error_message` holds the joined error text, or null
  /// when there were no errors. This is the audit trail behind "why has my map not shown up?".
  void recordIndexingStatus(MapRepoListing listing, MapIndexingTaskRunner.IndexingResult status) {
    boolean success =
        status.resultCode == MapIndexingTaskRunner.IndexingResult.ResultCode.SUCCESSFULLY_INDEXED;
    String errorMessage =
        status.errorDetails.isEmpty() ? null : truncate(String.join("\n\n", status.errorDetails));
    String upsert =
        "insert into map_indexing_status("
            + "    repo_url, repo_name, last_indexing_attempt, last_success, "
            + "    result_code, error_message)\n"
            + "values("
            + "    :repoUrl, :repoName, now(),"
            + "    case when :success then now() else null end,"
            + "    :resultCode, :errorMessage)\n"
            + "on conflict(repo_url)\n"
            + "do update set\n"
            + "    repo_name = excluded.repo_name,"
            + "    last_indexing_attempt = now(),"
            + "    last_success = case when :success then now()"
            + "                        else map_indexing_status.last_success end,"
            + "    result_code = excluded.result_code,"
            + "    error_message = excluded.error_message,"
            + "    date_updated = now()";
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(upsert)
                .bind("repoUrl", listing.getUri().toString())
                .bind("repoName", listing.getName())
                .bind("success", success)
                .bind("resultCode", status.resultCode.name())
                .bind("errorMessage", errorMessage)
                .execute());
  }

  /// Caps error text at the column width, preserving null.
  private static String truncate(String text) {
    return (text == null || text.length() <= MAX_ERROR_TEXT_LENGTH)
        ? text
        : text.substring(0, MAX_ERROR_TEXT_LENGTH);
  }
}
