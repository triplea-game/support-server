package org.triplea.services.maps.indexing;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import lombok.AllArgsConstructor;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.triplea.DbOnlyExtension;
import org.triplea.TestData;
import org.triplea.http.client.github.GithubClient;
import org.triplea.http.client.github.MapRepoListing;

@AllArgsConstructor
@QuarkusTest
@ExtendWith(DbOnlyExtension.class)
class MapIndexingTaskRunnerTest {
  final Jdbi jdbi;

  /// Run the indexing task loop and validate that we get a successful index result, then repeat and
  /// verify we get a 'map index is up to date'
  ///
  /// Uses a real database, a mock map indexer and a mock Github client
  @Test
  void repeatedIndexingResults() {
    MapRepoListing listing = TestData.mapRepoListing;

    // set up a mock github client that always returns the 'last' commit date for a repo.
    GithubClient mockClient = Mockito.mock(GithubClient.class);

    when(mockClient.getLatestCommitDate(anyString(), anyString()))
        .thenReturn(TestData.mapIndex.getLastCommitDate());
    MapIndexer mapIndexer = Mockito.mock(MapIndexer.class);
    when(mapIndexer.apply(listing)).thenReturn(TestData.mapIndex);

    MapIndexingTaskRunner runner =
        new MapIndexingTaskRunner(new MapIndexDao(jdbi), mockClient, mapIndexer);

    MapIndexingTaskRunner.IndexingResult result = runner.index(listing);

    assertThat(result.resultCode)
        .isEqualTo(MapIndexingTaskRunner.IndexingResult.ResultCode.SUCCESSFULLY_INDEXED);

    result = runner.index(listing);

    assertThat(result.resultCode)
        .isEqualTo(MapIndexingTaskRunner.IndexingResult.ResultCode.INDEXING_IS_UP_TO_DATE);
  }

  /// An indexing error creates a disabled map_index row carrying the error as its disable reason,
  /// so a never-successfully-indexed map still surfaces (disabled, with the error) on the status
  /// page. Uses a real database with a mock Github client and a mock indexer that throws.
  @Test
  void indexingErrorCreatesDisabledRowWithErrorReason() {
    MapRepoListing listing = TestData.mapRepoListing;

    GithubClient mockClient = Mockito.mock(GithubClient.class);
    when(mockClient.getLatestCommitDate(anyString(), anyString()))
        .thenReturn(TestData.mapIndex.getLastCommitDate());
    MapIndexer mapIndexer = Mockito.mock(MapIndexer.class);
    when(mapIndexer.apply(listing))
        .thenThrow(new MapIndexer.IndexingException(List.of("could not read map.yml")));

    MapIndexingTaskRunner runner =
        new MapIndexingTaskRunner(new MapIndexDao(jdbi), mockClient, mapIndexer);

    MapIndexingTaskRunner.IndexingResult result = runner.index(listing);

    assertThat(result.resultCode)
        .isEqualTo(MapIndexingTaskRunner.IndexingResult.ResultCode.REPO_ERROR);

    Boolean enabled =
        jdbi.withHandle(
            handle ->
                handle
                    .createQuery("select enabled from map_index where repo_url = :url")
                    .bind("url", listing.getUri().toString())
                    .mapTo(Boolean.class)
                    .one());
    String disableReason =
        jdbi.withHandle(
            handle ->
                handle
                    .createQuery("select disable_reason from map_index where repo_url = :url")
                    .bind("url", listing.getUri().toString())
                    .mapTo(String.class)
                    .one());

    assertThat(enabled).isFalse();
    assertThat(disableReason).isEqualTo("could not read map.yml");
  }
}
