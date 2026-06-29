package org.triplea.services.maps.indexing;

import static com.github.npathai.hamcrestopt.OptionalMatchers.isPresentAndIs;
import static org.hamcrest.MatcherAssert.assertThat;

import com.github.database.rider.core.api.dataset.DataSet;
import com.github.database.rider.core.api.dataset.ExpectedDataSet;
import com.github.database.rider.junit5.DBUnitExtension;
import com.github.npathai.hamcrestopt.OptionalMatchers;
import io.quarkus.test.junit.QuarkusTest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.triplea.DbOnlyExtension;
import org.triplea.TestData;

@DataSet(value = "map_index.yml", useSequenceFiltering = false)
@QuarkusTest
@ExtendWith(DbOnlyExtension.class)
@ExtendWith(DBUnitExtension.class)
class MapIndexDaoTest {

  private final MapIndexDao mapIndexDao;

  MapIndexDaoTest(Jdbi jdbi) {
    mapIndexDao = new MapIndexDao(jdbi);
  }

  @Test
  @ExpectedDataSet(value = "expected/map_index_upsert_updated.yml", orderBy = "id")
  void upsertUpdatesRecords() {
    mapIndexDao.upsert(
        TestData.mapIndex.toBuilder()
            .mapName("map-name-updated")
            .mapDownloadSizeInBytes(8000L)
            .build());
  }

  @Test
  @ExpectedDataSet("expected/map_index_post_disable.yml")
  void disableMapsNotInDisablesMissingMaps() {
    // the seeded map's repo is not in the list, so the row is disabled (flagged 'DELETED')
    // rather than deleted from the table.
    mapIndexDao.disableMapsNotIn(List.of("http-some-other-repo"));
  }

  @Test
  @DataSet(value = "map_index_disabled.yml", useSequenceFiltering = false)
  @ExpectedDataSet("expected/map_index_reenabled.yml")
  void upsertReenablesDisabledMap() {
    // upserting a previously-disabled map (e.g. a 'DELETED' repo that came back) re-enables it
    // and clears the disable reason; the CHECK constraint guarantees the cleared reason is null.
    mapIndexDao.upsert(TestData.mapIndex);
  }

  @Test
  @ExpectedDataSet("expected/map_index_post_disable_error.yml")
  void upsertDisabledDisablesExistingMapAndKeepsItsMetadata() {
    // an indexing error on an already-indexed map disables it with the error reason, but the
    // existing data columns (e.g. map_name) are preserved rather than overwritten with fallbacks.
    mapIndexDao.upsertDisabled(
        TestData.mapIndex.toBuilder().mapName("map-name-updated").build(),
        "could not read map.yml");
  }

  @Test
  @ExpectedDataSet("expected/map_indexing_status_error.yml")
  void recordIndexingStatusRecordsError() {
    mapIndexDao.recordIndexingStatus(
        TestData.mapRepoListing,
        new MapIndexingTaskRunner.IndexingResult(
            MapIndexingTaskRunner.IndexingResult.ResultCode.REPO_ERROR,
            List.of("could not read map.yml")));
  }

  @Test
  @ExpectedDataSet("expected/map_indexing_status_success.yml")
  void recordIndexingStatusRecordsSuccess() {
    mapIndexDao.recordIndexingStatus(
        TestData.mapRepoListing,
        new MapIndexingTaskRunner.IndexingResult(
            MapIndexingTaskRunner.IndexingResult.ResultCode.SUCCESSFULLY_INDEXED, List.of()));
  }

  @Test
  void getLastCommitDate() {
    assertThat(
        mapIndexDao.getLastCommitDate(TestData.mapIndex.getMapRepoUri()),
        isPresentAndIs(LocalDateTime.of(2000, 12, 1, 23, 59, 20).toInstant(ZoneOffset.UTC)));

    assertThat(
        "Map repo URL does not exist",
        mapIndexDao.getLastCommitDate("http://map-repo-url-DNE"),
        OptionalMatchers.isEmpty());
  }
}
