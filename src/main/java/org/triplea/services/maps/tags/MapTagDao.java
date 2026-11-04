package org.triplea.services.maps.tags;

import java.util.List;
import lombok.AllArgsConstructor;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;

/// CRUD + reorder operations for the `map_tag` list. Reordering is a swap of `display_order` with
/// the neighbor in the current ordering.
@AllArgsConstructor
public class MapTagDao {

  public enum Direction {
    UP,
    DOWN
  }

  private final Jdbi jdbi;

  public List<MapTagRow> listTags() {
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery(
                    "select id, name, display_order from map_tag order by display_order, id")
                .map(
                    (rs, ctx) ->
                        new MapTagRow(
                            rs.getInt("id"), rs.getString("name"), rs.getInt("display_order")))
                .list());
  }

  public void createTag(String name) {
    jdbi.useHandle(
        handle ->
            handle
                .createUpdate(
                    """
                    insert into map_tag (name, display_order)
                    values (:name, coalesce((select max(display_order) + 10 from map_tag), 0))
                    """)
                .bind("name", name)
                .execute());
  }

  public void renameTag(int id, String name) {
    jdbi.useHandle(
        handle ->
            handle
                .createUpdate("update map_tag set name = :name where id = :id")
                .bind("name", name)
                .bind("id", id)
                .execute());
  }

  public void deleteTag(int id) {
    jdbi.useHandle(
        handle ->
            handle.createUpdate("delete from map_tag where id = :id").bind("id", id).execute());
  }

  public void moveTag(int id, Direction direction) {
    jdbi.useTransaction(handle -> swapWithNeighbor(handle, id, direction));
  }

  /// Swaps display_order between `id` and its neighbor in the current ordering.
  private static void swapWithNeighbor(Handle handle, int id, Direction direction) {
    Integer currentOrder =
        handle
            .createQuery("select display_order from map_tag where id = :id")
            .bind("id", id)
            .mapTo(Integer.class)
            .findOne()
            .orElse(null);
    if (currentOrder == null) {
      return;
    }

    String comparator = direction == Direction.UP ? "<" : ">";
    String orderBy = direction == Direction.UP ? "desc" : "asc";
    var neighbor =
        handle
            .createQuery(
                "select id, display_order from map_tag where (display_order "
                    + comparator
                    + " :order or (display_order = :order and id "
                    + comparator
                    + " :id)) order by display_order "
                    + orderBy
                    + ", id "
                    + orderBy
                    + " limit 1")
            .bind("order", currentOrder)
            .bind("id", id)
            .map((rs, ctx) -> new int[] {rs.getInt("id"), rs.getInt("display_order")})
            .findOne()
            .orElse(null);
    if (neighbor == null) {
      return;
    }

    handle
        .createUpdate("update map_tag set display_order = :o where id = :id")
        .bind("o", neighbor[1])
        .bind("id", id)
        .execute();
    handle
        .createUpdate("update map_tag set display_order = :o where id = :id")
        .bind("o", currentOrder)
        .bind("id", neighbor[0])
        .execute();
  }
}
