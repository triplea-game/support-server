package org.triplea.maps.indexing;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.triplea.http.client.github.MapRepoListing;

@AllArgsConstructor
public class MapIndexDao {
  private final Jdbi jdbi;

  /// Upserts a map indexing result into the map_index table.
  void upsert(MapIndex mapIndex) {
    String query =
        "insert into map_index("
            + "    map_name, repo_url, default_branch, description, "
            + "    download_url, preview_image_url, download_size_bytes, last_commit_date)\n"
            + "values("
            + "     :mapName, :mapRepoUri, :defaultBranch, :description, "
            + "     :downloadUri, :previewImageUri, :mapDownloadSizeInBytes, :lastCommitDate)\n"
            + "on conflict(repo_url)\n"
            + "do update set\n"
            + "   map_name = :mapName,"
            + "   description = :description,"
            + "   default_branch = :defaultBranch,"
            + "   download_url = :downloadUri,"
            + "   preview_image_url = :previewImageUri,"
            + "   download_size_bytes = :mapDownloadSizeInBytes,"
            + "   last_commit_date = :lastCommitDate";
    jdbi.withHandle(handle -> handle.createUpdate(query).bindBean(mapIndex).execute());
  }

  /// Deletes maps that are not in the parameter list from the map_index table.
  int removeMapsNotIn(List<String> mapUriList) {
    String update = "delete from map_index where repo_url not in(<mapUriList>)";
    return jdbi.withHandle(
        handle -> handle.createUpdate(update).bindList("mapUriList", mapUriList).execute());
  }

  Optional<Instant> getLastCommitDate(@Bind("repoUrl") String repoUrl) {
    String select = "select last_commit_date from map_index where repo_url = :repoUrl";
    return jdbi.withHandle(
        handle ->
            handle.createQuery(select).bind("repoUrl", repoUrl).mapTo(Instant.class).findOne());
  }

  /// TODO: implement & test
  /// For implementation, we want to record the result of the indexing.
  /// This will be later used for debug information to show the user.
  /// We will want some way for users to see the history of map polling,
  /// something to help debug the case of "why has my map not shown up?
  /// Or, if there are errors indexing a map - the map maker would want to know.
  /// So.. we'll want a page that essentially indicates per map the indexing
  /// status, when it was indexed, the result, any error messages, file size detected, etc..
  void recordIndexingStatus(MapRepoListing listing, MapIndexingTaskRunner.IndexingResult status) {}
}
