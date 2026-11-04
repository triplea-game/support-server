package org.triplea.services.maps.status;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import org.jdbi.v3.core.Jdbi;

/// Reads maps together with their tags for the status page, and writes a single map's tags in
/// `map_index_tag` and its admin approval.
@AllArgsConstructor
public class MapStatusDao {

  private final Jdbi jdbi;

  /// All maps (newest commit first), each with the ids of its assigned tags.
  public List<MapStatusRow> listMaps() {
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
                      s.last_indexing_attempt last_indexed,
                      array(select map_tag_id from map_index_tag where map_index_id = m.id) tag_ids
                    from map_index m
                    left join map_indexing_status s on s.repo_url = m.repo_url
                    order by m.last_commit_date desc, m.map_name
                    """)
                .map(
                    rowView ->
                        new MapStatusRow(
                            rowView.getColumn("map_id", Long.class),
                            rowView.getColumn("map_name", String.class),
                            rowView.getColumn("preview_image_url", String.class),
                            rowView.getColumn("description", String.class),
                            rowView.getColumn("last_commit_date", Instant.class),
                            rowView.getColumn("enabled", Boolean.class),
                            rowView.getColumn("disable_reason", String.class),
                            rowView.getColumn("admin_enabled", Boolean.class),
                            rowView.getColumn("admin_disable_reason", String.class),
                            rowView.getColumn("last_indexed", Instant.class),
                            Set.of(rowView.getColumn("tag_ids", Integer[].class))))
                .list());
  }

  /// Adds the tag named `tagName` (any case) to the map. A name missing from `map_tag` or a tag the
  /// map already has is a no-op.
  public void addTag(long mapId, String tagName) {
    jdbi.useHandle(
        handle ->
            handle
                .createUpdate(
                    """
                    insert into map_index_tag (map_index_id, map_tag_id)
                    select :mapId, id from map_tag where lower(name) = lower(:name) limit 1
                    on conflict do nothing
                    """)
                .bind("mapId", mapId)
                .bind("name", tagName)
                .execute());
  }

  /// Removes the tag `tagId` from the map (a no-op if the map does not have it).
  public void removeTag(long mapId, int tagId) {
    jdbi.useHandle(
        handle ->
            handle
                .createUpdate(
                    "delete from map_index_tag where map_index_id = :mapId and map_tag_id = :tagId")
                .bind("mapId", mapId)
                .bind("tagId", tagId)
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
