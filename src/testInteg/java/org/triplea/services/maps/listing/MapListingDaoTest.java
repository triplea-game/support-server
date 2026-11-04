package org.triplea.services.maps.listing;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.database.rider.core.api.dataset.DataSet;
import com.github.database.rider.junit5.DBUnitExtension;
import io.quarkus.test.junit.QuarkusTest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.triplea.DbOnlyExtension;
import org.triplea.http.client.lobby.maps.listing.MapTag;

@DataSet(value = "map_index.yml", useSequenceFiltering = false)
@QuarkusTest
@ExtendWith(DbOnlyExtension.class)
@ExtendWith(DBUnitExtension.class)
class MapListingDaoTest {

  private final MapListingDao mapListingDao;

  MapListingDaoTest(final Jdbi jdbi) {
    this.mapListingDao = new MapListingDao(jdbi);
  }

  @Test
  void verifySelect() {
    final var results = mapListingDao.fetchMapListings();
    assertThat(results).hasSize(1);
    final var mapDownloadListing = results.get(0);
    assertThat(mapDownloadListing.getMapName()).isEqualTo("map-name");
    assertThat(mapDownloadListing.getDownloadUrl())
        .isEqualTo("http-map-repo-url/archives/master.zip");
    assertThat(mapDownloadListing.getDownloadSizeInBytes()).isEqualTo(4000L);
    assertThat(mapDownloadListing.getPreviewImageUrl()).isEqualTo("http-preview-image-url");
    assertThat(mapDownloadListing.getDescription()).isEqualTo("description-repo-1");
    assertThat(mapDownloadListing.getLastCommitDateEpochMilli())
        .isEqualTo(
            LocalDateTime.of(2000, 12, 1, 23, 59, 20).toInstant(ZoneOffset.UTC).toEpochMilli());
    assertThat(mapDownloadListing.getMapTags()).isEmpty();
  }

  @Test
  @DataSet(value = "map_index_admin_disabled.yml", useSequenceFiltering = false)
  void adminDisabledMapIsExcluded() {
    // The map is indexer-enabled but not admin-approved, so the listing (which requires both)
    // returns nothing.
    assertThat(mapListingDao.fetchMapListings()).isEmpty();
  }

  @Test
  @DataSet(value = "map_index.yml,map_index_tags.yml", useSequenceFiltering = false)
  void tagsAreSentInTagListOrder() {
    var mapTags = mapListingDao.fetchMapListings().get(0).getMapTags();

    assertThat(mapTags)
        .containsExactly(
            MapTag.builder().name("Tags").value("fantasy").displayOrder(10).build(),
            MapTag.builder().name("Tags").value("naval").displayOrder(20).build());
  }
}
