package org.triplea.services.maps.listing;

import java.util.LinkedHashMap;
import java.util.List;
import lombok.AllArgsConstructor;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.reflect.BeanMapper;
import org.triplea.http.client.lobby.maps.listing.MapDownloadItem;
import org.triplea.http.client.lobby.maps.listing.MapTag;

@AllArgsConstructor
public class MapListingDao {
  private final Jdbi jdbi;

  /// Enabled maps (newest commit first), each with its tags of the tag list, in tag-list order,
  /// each as a `MapTag` named "Tags".
  List<MapDownloadItem> fetchMapListings() {
    return jdbi.withHandle(
        handle -> {
          var listings =
              handle
                  .createQuery(
                      """
                      select
                        m.id,
                        m.map_name,
                        m.download_url,
                        m.download_size_bytes,
                        m.preview_image_url,
                        m.description,
                        m.last_commit_date
                      from map_index m
                      where m.enabled and m.admin_enabled
                      order by m.last_commit_date desc, m.map_name
                      """)
                  .registerRowMapper(BeanMapper.factory(MapListingRecord.class))
                  .reduceRows(
                      new LinkedHashMap<Long, MapListingRecord>(),
                      (accumulator, rowView) -> {
                        accumulator.put(
                            rowView.getColumn("id", Long.class),
                            rowView.getRow(MapListingRecord.class));
                        return accumulator;
                      });

          handle
              .createQuery(
                  """
                  select mit.map_index_id, t.name, t.display_order
                  from map_index_tag mit
                  join map_tag t on t.id = mit.map_tag_id
                  order by t.display_order, t.id
                  """)
              .reduceRows(
                  listings,
                  (accumulator, rowView) -> {
                    var listing = accumulator.get(rowView.getColumn("map_index_id", Long.class));
                    if (listing != null) { // null: the map is not listed
                      listing
                          .getTags()
                          .add(
                              MapTag.builder()
                                  .name("Tags")
                                  .value(rowView.getColumn("name", String.class))
                                  .displayOrder(rowView.getColumn("display_order", Integer.class))
                                  .build());
                    }
                    return accumulator;
                  });

          return listings.values().stream().map(MapListingRecord::toMapDownloadItem).toList();
        });
  }
}
