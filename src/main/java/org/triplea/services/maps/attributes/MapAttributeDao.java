package org.triplea.services.maps.attributes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import lombok.AllArgsConstructor;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;

/**
 * CRUD + reorder operations for the attribute catalog: {@code map_attribute} (the dimensions, e.g.
 * "difficulty") and {@code map_attribute_value} (the allowed values within a dimension, e.g.
 * "easy"). Reordering is a swap of {@code display_order} with the neighbor in the current ordering.
 */
@AllArgsConstructor
public class MapAttributeDao {

  public enum Direction {
    UP,
    DOWN
  }

  private final Jdbi jdbi;

  public List<AttributeWithValues> listAttributes() {
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery(
                    """
                        select
                          a.id            attribute_id,
                          a.name          attribute_name,
                          a.display_order attribute_display_order,
                          v.id            value_id,
                          v.value         value_text,
                          v.display_order value_display_order
                        from map_attribute a
                        left join map_attribute_value v on v.map_attribute_id = a.id
                        order by a.display_order, a.id, v.display_order, v.id
                        """)
                .reduceRows(
                    new LinkedHashMap<Integer, AttributeWithValues>(),
                    (accumulator, rowView) -> {
                      int attributeId = rowView.getColumn("attribute_id", Integer.class);
                      var entry =
                          accumulator.computeIfAbsent(
                              attributeId,
                              id ->
                                  new AttributeWithValues(
                                      id,
                                      rowView.getColumn("attribute_name", String.class),
                                      rowView.getColumn("attribute_display_order", Integer.class),
                                      new ArrayList<>()));
                      Integer valueId = rowView.getColumn("value_id", Integer.class);
                      if (valueId != null) {
                        entry
                            .values()
                            .add(
                                new AttributeValueRow(
                                    valueId,
                                    attributeId,
                                    rowView.getColumn("value_text", String.class),
                                    rowView.getColumn("value_display_order", Integer.class)));
                      }
                      return accumulator;
                    })
                .values()
                .stream()
                .toList());
  }

  public void createAttribute(String name) {
    jdbi.useHandle(
        handle ->
            handle
                .createUpdate(
                    """
                    insert into map_attribute (name, display_order)
                    values (:name, coalesce((select max(display_order) + 10 from map_attribute), 0))
                    """)
                .bind("name", name)
                .execute());
  }

  public void renameAttribute(int id, String name) {
    jdbi.useHandle(
        handle ->
            handle
                .createUpdate("update map_attribute set name = :name where id = :id")
                .bind("name", name)
                .bind("id", id)
                .execute());
  }

  public void deleteAttribute(int id) {
    jdbi.useHandle(
        handle ->
            handle
                .createUpdate("delete from map_attribute where id = :id")
                .bind("id", id)
                .execute());
  }

  public void moveAttribute(int id, Direction direction) {
    jdbi.useTransaction(
        handle ->
            swapWithNeighbor(
                handle,
                "map_attribute",
                /* scopeFilter= */ "",
                /* scopeBindings= */ List.of(),
                id,
                direction));
  }

  public void createValue(int attributeId, String value) {
    jdbi.useHandle(
        handle ->
            handle
                .createUpdate(
                    """
                    insert into map_attribute_value (map_attribute_id, value, display_order)
                    values (:attributeId, :value,
                            coalesce(
                              (select max(display_order) + 10 from map_attribute_value
                               where map_attribute_id = :attributeId),
                              0))
                    """)
                .bind("attributeId", attributeId)
                .bind("value", value)
                .execute());
  }

  public void renameValue(int id, String value) {
    jdbi.useHandle(
        handle ->
            handle
                .createUpdate("update map_attribute_value set value = :value where id = :id")
                .bind("value", value)
                .bind("id", id)
                .execute());
  }

  public void deleteValue(int id) {
    jdbi.useHandle(
        handle ->
            handle
                .createUpdate("delete from map_attribute_value where id = :id")
                .bind("id", id)
                .execute());
  }

  public void moveValue(int id, Direction direction) {
    jdbi.useTransaction(
        handle -> {
          Integer attributeId =
              handle
                  .createQuery("select map_attribute_id from map_attribute_value where id = :id")
                  .bind("id", id)
                  .mapTo(Integer.class)
                  .findOne()
                  .orElse(null);
          if (attributeId == null) {
            return;
          }
          swapWithNeighbor(
              handle,
              "map_attribute_value",
              "map_attribute_id = :attributeId and ",
              List.of(new Binding("attributeId", attributeId)),
              id,
              direction);
        });
  }

  private record Binding(String name, Object value) {}

  /**
   * Swaps display_order between {@code id} and its neighbor in the current ordering. {@code
   * scopeFilter} (if non-empty) restricts the neighbor search to a sub-collection (e.g. values
   * belonging to the same attribute) and must end with {@code "and "}.
   */
  private static void swapWithNeighbor(
      Handle handle,
      String table,
      String scopeFilter,
      List<Binding> scopeBindings,
      int id,
      Direction direction) {
    var currentOrderQuery =
        handle.createQuery("select display_order from " + table + " where id = :id").bind("id", id);
    Integer currentOrder = currentOrderQuery.mapTo(Integer.class).findOne().orElse(null);
    if (currentOrder == null) {
      return;
    }

    String comparator = direction == Direction.UP ? "<" : ">";
    String orderBy = direction == Direction.UP ? "desc" : "asc";
    var neighborQuery =
        handle
            .createQuery(
                "select id, display_order from "
                    + table
                    + " where "
                    + scopeFilter
                    + "(display_order "
                    + comparator
                    + " :order or (display_order = :order and id "
                    + comparator
                    + " :id)) "
                    + "order by display_order "
                    + orderBy
                    + ", id "
                    + orderBy
                    + " limit 1")
            .bind("order", currentOrder)
            .bind("id", id);
    for (var b : scopeBindings) {
      neighborQuery.bind(b.name(), b.value());
    }
    var neighbor =
        neighborQuery
            .map((rs, ctx) -> new int[] {rs.getInt("id"), rs.getInt("display_order")})
            .findOne()
            .orElse(null);
    if (neighbor == null) {
      return;
    }

    handle
        .createUpdate("update " + table + " set display_order = :o where id = :id")
        .bind("o", neighbor[1])
        .bind("id", id)
        .execute();
    handle
        .createUpdate("update " + table + " set display_order = :o where id = :id")
        .bind("o", currentOrder)
        .bind("id", neighbor[0])
        .execute();
  }
}
