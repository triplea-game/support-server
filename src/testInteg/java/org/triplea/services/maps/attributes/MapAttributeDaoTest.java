package org.triplea.services.maps.attributes;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.database.rider.core.api.dataset.DataSet;
import com.github.database.rider.junit5.DBUnitExtension;
import io.quarkus.test.junit.QuarkusTest;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.triplea.DbOnlyExtension;

@DataSet(value = "map_attribute_dao.yml", useSequenceFiltering = false)
@QuarkusTest
@ExtendWith(DbOnlyExtension.class)
@ExtendWith(DBUnitExtension.class)
class MapAttributeDaoTest {

  private final MapAttributeDao dao;

  MapAttributeDaoTest(final Jdbi jdbi) {
    this.dao = new MapAttributeDao(jdbi);
  }

  @Test
  void listAttributesReturnsAttributesInDisplayOrderWithNestedValues() {
    var attributes = dao.listAttributes();

    assertThat(attributes)
        .extracting(AttributeWithValues::name)
        .containsExactly("era", "difficulty");

    var era = attributes.get(0);
    assertThat(era.values())
        .extracting(AttributeValueRow::value)
        .containsExactly("ancient", "medieval", "modern");

    var difficulty = attributes.get(1);
    assertThat(difficulty.values())
        .extracting(AttributeValueRow::value)
        .containsExactly("easy", "hard");
  }

  @Test
  void createAttributeAppendsAfterExistingMax() {
    dao.createAttribute("scale");

    var names = dao.listAttributes().stream().map(AttributeWithValues::name).toList();
    assertThat(names).containsExactly("era", "difficulty", "scale");
  }

  @Test
  void renameAttributeChangesName() {
    dao.renameAttribute(3300, "epoch");

    assertThat(dao.listAttributes().get(0).name()).isEqualTo("epoch");
  }

  @Test
  void deleteAttributeCascadesValues() {
    dao.deleteAttribute(3300);

    var attributes = dao.listAttributes();
    assertThat(attributes).extracting(AttributeWithValues::name).containsExactly("difficulty");
  }

  @Test
  void moveAttributeDownSwapsWithNeighbor() {
    dao.moveAttribute(3300, MapAttributeDao.Direction.DOWN);

    assertThat(dao.listAttributes())
        .extracting(AttributeWithValues::name)
        .containsExactly("difficulty", "era");
  }

  @Test
  void moveAttributeUpAtTopIsNoOp() {
    dao.moveAttribute(3300, MapAttributeDao.Direction.UP);

    assertThat(dao.listAttributes())
        .extracting(AttributeWithValues::name)
        .containsExactly("era", "difficulty");
  }

  @Test
  void moveAttributeDownAtBottomIsNoOp() {
    dao.moveAttribute(8800, MapAttributeDao.Direction.DOWN);

    assertThat(dao.listAttributes())
        .extracting(AttributeWithValues::name)
        .containsExactly("era", "difficulty");
  }

  @Test
  void createValueAppendsWithinAttributeScope() {
    dao.createValue(8800, "medium");

    var difficulty = dao.listAttributes().get(1);
    assertThat(difficulty.values())
        .extracting(AttributeValueRow::value)
        .containsExactly("easy", "hard", "medium");
  }

  @Test
  void renameValueChangesValue() {
    dao.renameValue(100, "stone-age");

    var era = dao.listAttributes().get(0);
    assertThat(era.values())
        .extracting(AttributeValueRow::value)
        .containsExactly("stone-age", "medieval", "modern");
  }

  @Test
  void deleteValueRemovesOnlyThatRow() {
    dao.deleteValue(101);

    var era = dao.listAttributes().get(0);
    assertThat(era.values())
        .extracting(AttributeValueRow::value)
        .containsExactly("ancient", "modern");
  }

  @Test
  void moveValueDownSwapsWithNeighborInSameAttribute() {
    dao.moveValue(100, MapAttributeDao.Direction.DOWN);

    var era = dao.listAttributes().get(0);
    assertThat(era.values())
        .extracting(AttributeValueRow::value)
        .containsExactly("medieval", "ancient", "modern");
  }

  @Test
  void moveValueUpAtTopOfItsAttributeIsNoOp() {
    dao.moveValue(100, MapAttributeDao.Direction.UP);

    var era = dao.listAttributes().get(0);
    assertThat(era.values())
        .extracting(AttributeValueRow::value)
        .containsExactly("ancient", "medieval", "modern");
  }

  @Test
  void moveValueDoesNotCrossAttributeBoundary() {
    // 102 ("modern") is the last value in era. Moving down should NOT promote it into difficulty.
    dao.moveValue(102, MapAttributeDao.Direction.DOWN);

    var attributes = dao.listAttributes();
    assertThat(attributes.get(0).values())
        .extracting(AttributeValueRow::value)
        .containsExactly("ancient", "medieval", "modern");
    assertThat(attributes.get(1).values())
        .extracting(AttributeValueRow::value)
        .containsExactly("easy", "hard");
  }

  @Test
  void listAttributesIncludesAttributeWithoutValues() {
    dao.createAttribute("scale"); // no values added

    var attributes = dao.listAttributes();
    var scale = attributes.stream().filter(a -> a.name().equals("scale")).findFirst().orElseThrow();
    assertThat(scale.values()).isEmpty();
  }
}
