package org.triplea.services.maps.tags;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.database.rider.core.api.dataset.DataSet;
import com.github.database.rider.junit5.DBUnitExtension;
import io.quarkus.test.junit.QuarkusTest;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.triplea.DbOnlyExtension;

/// Seeds `map_tag_dao.yml`: 7200 "fantasy" (order 10), 7100 "naval" (20), 7300 "world war 2" (30).
@DataSet(value = "map_tag_dao.yml", useSequenceFiltering = false)
@QuarkusTest
@ExtendWith(DbOnlyExtension.class)
@ExtendWith(DBUnitExtension.class)
class MapTagDaoTest {

  private final MapTagDao dao;

  MapTagDaoTest(final Jdbi jdbi) {
    this.dao = new MapTagDao(jdbi);
  }

  @Test
  void listTagsIsOrderedByDisplayOrderNotId() {
    assertThat(dao.listTags())
        .extracting(MapTagRow::name)
        .containsExactly("fantasy", "naval", "world war 2");
  }

  @Test
  void createTagAppendsAfterExistingMax() {
    dao.createTag("modern");

    var last = dao.listTags().getLast();
    assertThat(last.name()).isEqualTo("modern");
    assertThat(last.displayOrder()).isEqualTo(40);
  }

  @Test
  void createTagOnEmptyListStartsAtZero() {
    dao.listTags().forEach(tag -> dao.deleteTag(tag.id()));

    dao.createTag("modern");

    assertThat(dao.listTags()).singleElement().extracting(MapTagRow::displayOrder).isEqualTo(0);
  }

  @Test
  void renameTagChangesName() {
    dao.renameTag(7100, "sea");

    assertThat(dao.listTags())
        .extracting(MapTagRow::name)
        .containsExactly("fantasy", "sea", "world war 2");
  }

  @Test
  void deleteTagRemovesOnlyThatTag() {
    dao.deleteTag(7100);

    assertThat(dao.listTags())
        .extracting(MapTagRow::name)
        .containsExactly("fantasy", "world war 2");
  }

  @Test
  void moveTagUpSwapsWithNeighbor() {
    dao.moveTag(7100, MapTagDao.Direction.UP);

    assertThat(dao.listTags())
        .extracting(MapTagRow::name)
        .containsExactly("naval", "fantasy", "world war 2");
  }

  @Test
  void moveTagDownSwapsWithNeighbor() {
    dao.moveTag(7100, MapTagDao.Direction.DOWN);

    assertThat(dao.listTags())
        .extracting(MapTagRow::name)
        .containsExactly("fantasy", "world war 2", "naval");
  }

  @Test
  void movingPastEitherEndIsNoOp() {
    dao.moveTag(7200, MapTagDao.Direction.UP);
    dao.moveTag(7300, MapTagDao.Direction.DOWN);

    assertThat(dao.listTags())
        .extracting(MapTagRow::name)
        .containsExactly("fantasy", "naval", "world war 2");
  }
}
