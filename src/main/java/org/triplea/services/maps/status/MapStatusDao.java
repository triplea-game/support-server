package org.triplea.services.maps.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import lombok.AllArgsConstructor;
import org.jdbi.v3.core.Jdbi;
import org.triplea.http.client.lobby.maps.listing.MapTag;

/// Reads maps together with their currently-assigned attribute values for the status page, and
/// writes a single map's attribute assignment in `map_index_attribute`.
///
/// Each map holds at most one value per attribute dimension (the table's primary key is
/// `(map_index_id, map_attribute_id)`), so a write is an upsert keyed on that pair and a clear is a
/// delete of that pair.
@AllArgsConstructor
public class MapStatusDao {

  private final Jdbi jdbi;

  /// All maps (newest commit first), each with its read-only tag list and its attribute->value
  /// selections. Maps with no attributes still appear, with empty tags and selections.
  public List<MapStatusRow> listMapsWithAttributes() {
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery(
                    """
                    select
                      m.id               map_id,
                      m.map_name,
                      m.preview_image_url,
                      m.description,
                      m.last_commit_date,
                      m.enabled,
                      m.disable_reason,
                      m.admin_enabled,
                      m.admin_disable_reason,
                      a.id               attribute_id,
                      a.name             attribute_name,
                      v.id               value_id,
                      v.value            value_text
                    from map_index m
                    left join map_index_attribute mia on mia.map_index_id = m.id
                    left join map_attribute a on a.id = mia.map_attribute_id
                    left join map_attribute_value v on v.id = mia.map_attribute_value_id
                    order by m.last_commit_date desc, m.map_name, a.display_order, v.display_order
                    """)
                .reduceRows(
                    new LinkedHashMap<Long, MapStatusRow>(),
                    (accumulator, rowView) -> {
                      var row =
                          accumulator.computeIfAbsent(
                              rowView.getColumn("map_id", Long.class),
                              id ->
                                  new MapStatusRow(
                                      id,
                                      rowView.getColumn("map_name", String.class),
                                      rowView.getColumn("preview_image_url", String.class),
                                      rowView.getColumn("description", String.class),
                                      rowView.getColumn("last_commit_date", Instant.class),
                                      rowView.getColumn("enabled", Boolean.class),
                                      rowView.getColumn("disable_reason", String.class),
                                      rowView.getColumn("admin_enabled", Boolean.class),
                                      rowView.getColumn("admin_disable_reason", String.class),
                                      new ArrayList<>(),
                                      new LinkedHashMap<>()));

                      Integer valueId = rowView.getColumn("value_id", Integer.class);
                      if (valueId != null) {
                        int attributeId = rowView.getColumn("attribute_id", Integer.class);
                        row.tags()
                            .add(
                                MapTag.builder()
                                    .name(rowView.getColumn("attribute_name", String.class))
                                    .value(rowView.getColumn("value_text", String.class))
                                    .build());
                        row.selections().put(attributeId, valueId);
                      }
                      return accumulator;
                    })
                .values()
                .stream()
                .toList());
  }

  /// Assigns `valueId` to `attributeId` for the given map, replacing any existing value for that
  /// dimension. The composite foreign key on `map_index_attribute` rejects a value that does not
  /// belong to the named attribute.
  public void setAttribute(long mapId, int attributeId, int valueId) {
    jdbi.useHandle(
        handle ->
            handle
                .createUpdate(
                    """
                    insert into map_index_attribute
                        (map_index_id, map_attribute_id, map_attribute_value_id)
                    values (:mapId, :attributeId, :valueId)
                    on conflict (map_index_id, map_attribute_id)
                    do update set map_attribute_value_id = excluded.map_attribute_value_id
                    """)
                .bind("mapId", mapId)
                .bind("attributeId", attributeId)
                .bind("valueId", valueId)
                .execute());
  }

  /// Removes the map's value for `attributeId` (a no-op if it had none).
  public void clearAttribute(long mapId, int attributeId) {
    jdbi.useHandle(
        handle ->
            handle
                .createUpdate(
                    """
                    delete from map_index_attribute
                    where map_index_id = :mapId and map_attribute_id = :attributeId
                    """)
                .bind("mapId", mapId)
                .bind("attributeId", attributeId)
                .execute());
  }

  /// Admin-approves the map: sets `admin_enabled` and clears its disable reason. Independent of the
  /// indexer's `enabled` flag, so an approved map is publicly listed only if it also indexes
  /// cleanly.
  public void approveMap(long mapId) {
    jdbi.useHandle(
        handle ->
            handle
                .createUpdate(
                    """
                    update map_index
                    set admin_enabled = true, admin_disable_reason = null
                    where id = :mapId
                    """)
                .bind("mapId", mapId)
                .execute());
  }

  /// Admin-disables the map with a required reason, hiding it from the public listing regardless of
  /// its indexer health. The not-null reason satisfies `map_index_admin_disable_reason_ck`.
  public void disableMap(long mapId, String reason) {
    jdbi.useHandle(
        handle ->
            handle
                .createUpdate(
                    """
                    update map_index
                    set admin_enabled = false, admin_disable_reason = :reason
                    where id = :mapId
                    """)
                .bind("mapId", mapId)
                .bind("reason", reason)
                .execute());
  }
}
