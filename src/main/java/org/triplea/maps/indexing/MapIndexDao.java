package org.triplea.maps.indexing;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import lombok.AllArgsConstructor;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
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

  // TODO: implement & test
  void recordIndexingStatus(MapRepoListing listing, MapIndexingTaskRunner.IndexingResult status) {}
}
