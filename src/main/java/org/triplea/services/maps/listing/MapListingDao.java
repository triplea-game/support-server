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

  List<MapDownloadItem> fetchMapListings() {
    return jdbi.withHandle(
        handle ->
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
                          m.last_commit_date,
                          v.value attribute_value,
                          a.name  attribute_name
                        from map_index m
                        left join map_index_attribute mia on mia.map_index_id = m.id
                        left join map_attribute_value v on v.id = mia.map_attribute_value_id
                        left join map_attribute a on a.id = mia.map_attribute_id
                        where m.enabled
                        order by m.last_commit_date desc, m.map_name, a.display_order, v.display_order
                        """)
                .registerRowMapper(BeanMapper.factory(MapListingRecord.class))
                .registerRowMapper(BeanMapper.factory(MapTag.class))
                .reduceRows(
                    new LinkedHashMap<Long, MapListingRecord>(),
                    (accumulator, rowView) -> {
                      var listing =
                          accumulator.computeIfAbsent(
                              rowView.getColumn("id", Long.class),
                              id -> rowView.getRow(MapListingRecord.class));

                      if (rowView.getColumn("attribute_value", String.class) != null) {
                        listing
                            .getTags()
                            .add(
                                MapTag.builder()
                                    .name(rowView.getColumn("attribute_name", String.class))
                                    .value(rowView.getColumn("attribute_value", String.class))
                                    .build());
                      }
                      return accumulator;
                    })
                .values()
                .stream()
                .map(MapListingRecord::toMapDownloadItem)
                .toList());
  }
}
