package org.triplea.services.maps.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import com.github.database.rider.core.api.dataset.DataSet;
import com.github.database.rider.junit5.DBUnitExtension;
import io.quarkus.test.junit.QuarkusTest;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.triplea.DbOnlyExtension;
import org.triplea.http.client.lobby.maps.listing.MapTag;
import org.triplea.services.maps.tags.MapTagDao;

/// Read/write tests for the status page's map-attribute access. Seeds one map (id 10) assigned
/// era=ancient (attribute 3300 -> value 100) and difficulty=easy (attribute 8800 -> value 200);
/// the catalog also holds era values 101/102 and difficulty value 201 to switch between. The tag
/// tests add `map_index_tags.yml`: map 10 has tags 7100 and 7200, tag 7300 is unassigned.
@DataSet(value = "map_status.yml", useSequenceFiltering = false)
@QuarkusTest
@ExtendWith(DbOnlyExtension.class)
@ExtendWith(DBUnitExtension.class)
class MapStatusDaoTest {

  private final MapStatusDao dao;
  private final MapTagDao tagDao;

  MapStatusDaoTest(final Jdbi jdbi) {
    this.dao = new MapStatusDao(jdbi);
    this.tagDao = new MapTagDao(jdbi);
  }

  @Test
  void listReturnsMapWithTagsAndSelections() {
    var maps = dao.listMapsWithAttributes();

    assertThat(maps).hasSize(1);
    var map = maps.get(0);
    assertThat(map.id()).isEqualTo(10L);
    assertThat(map.mapName()).isEqualTo("map-name");
    assertThat(map.enabled()).isTrue();
    assertThat(map.disableReason()).isNull();
    assertThat(map.adminEnabled()).isTrue();
    assertThat(map.adminDisableReason()).isNull();
    assertThat(map.tags())
        .containsExactlyInAnyOrder(
            MapTag.builder().name("era").value("ancient").build(),
            MapTag.builder().name("difficulty").value("easy").build());
    assertThat(map.selections()).containsOnly(entry(3300, 100), entry(8800, 200));
  }

  @Test
  @DataSet(value = "map_status_disabled.yml", useSequenceFiltering = false)
  void listSurfacesDisabledMapWithReason() {
    var map = dao.listMapsWithAttributes().get(0);

    assertThat(map.enabled()).isFalse();
    assertThat(map.disableReason()).isEqualTo("could not read map.yml");
  }

  @Test
  void setAttributeReplacesExistingValueForThatDimension() {
    dao.setAttribute(10, 8800, 201); // difficulty: easy -> hard

    var map = dao.listMapsWithAttributes().get(0);
    assertThat(map.selections()).containsOnly(entry(3300, 100), entry(8800, 201));
    assertThat(map.tags()).contains(MapTag.builder().name("difficulty").value("hard").build());
  }

  @Test
  void clearAttributeRemovesOnlyThatDimension() {
    dao.clearAttribute(10, 3300); // clear era, keep difficulty

    var map = dao.listMapsWithAttributes().get(0);
    assertThat(map.selections()).containsOnly(entry(8800, 200));
    assertThat(map.tags())
        .containsExactly(MapTag.builder().name("difficulty").value("easy").build());
  }

  @Test
  void clearAttributeIsNoOpWhenNoneAssigned() {
    dao.clearAttribute(10, 8800);
    dao.clearAttribute(10, 8800); // second clear is harmless

    var map = dao.listMapsWithAttributes().get(0);
    assertThat(map.selections()).containsOnly(entry(3300, 100));
  }

  @Test
  void disableMapSetsAdminFlagAndReason() {
    dao.disableMap(10, "spam map");

    var map = dao.listMapsWithAttributes().get(0);
    assertThat(map.adminEnabled()).isFalse();
    assertThat(map.adminDisableReason()).isEqualTo("spam map");
    assertThat(map.enabled()).isTrue(); // indexer flag is independent and untouched
  }

  @Test
  void approveMapEnablesAndClearsReason() {
    dao.disableMap(10, "spam map");
    dao.approveMap(10);

    var map = dao.listMapsWithAttributes().get(0);
    assertThat(map.adminEnabled()).isTrue();
    assertThat(map.adminDisableReason()).isNull();
  }

  @Test
  void mapWithoutTagsHasNoTagIds() {
    assertThat(dao.listMapsWithAttributes().get(0).tagIds()).isEmpty();
  }

  @Test
  @DataSet(value = "map_status.yml,map_index_tags.yml", useSequenceFiltering = false)
  void listReturnsTagIds() {
    assertThat(dao.listMapsWithAttributes().get(0).tagIds()).containsExactlyInAnyOrder(7100, 7200);
  }

  @Test
  @DataSet(value = "map_status.yml,map_index_tags.yml", useSequenceFiltering = false)
  void addTagFindsTheTagByNameInAnyCase() {
    dao.addTag(10, "World War 2");

    assertThat(dao.listMapsWithAttributes().get(0).tagIds())
        .containsExactlyInAnyOrder(7100, 7200, 7300);
  }

  @Test
  @DataSet(value = "map_status.yml,map_index_tags.yml", useSequenceFiltering = false)
  void addTagWithUnknownNameIsNoOp() {
    dao.addTag(10, "no such tag");

    assertThat(dao.listMapsWithAttributes().get(0).tagIds()).containsExactlyInAnyOrder(7100, 7200);
  }

  @Test
  @DataSet(value = "map_status.yml,map_index_tags.yml", useSequenceFiltering = false)
  void addingATagTwiceKeepsOne() {
    dao.addTag(10, "world war 2");
    dao.addTag(10, "world war 2");

    assertThat(dao.listMapsWithAttributes().get(0).tagIds())
        .containsExactlyInAnyOrder(7100, 7200, 7300);
  }

  @Test
  @DataSet(value = "map_status.yml,map_index_tags.yml", useSequenceFiltering = false)
  void removeTagRemovesOnlyThatTag() {
    dao.removeTag(10, 7100);

    assertThat(dao.listMapsWithAttributes().get(0).tagIds()).containsExactly(7200);
  }

  @Test
  @DataSet(value = "map_status.yml,map_index_tags.yml", useSequenceFiltering = false)
  void deletingATagRemovesItFromMaps() {
    tagDao.deleteTag(7100);

    assertThat(dao.listMapsWithAttributes().get(0).tagIds()).containsExactly(7200);
  }
}
