package org.triplea.services.maps.admin;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.triplea.http.client.lobby.maps.listing.MapDownloadItem;
import org.triplea.http.client.lobby.maps.listing.MapTag;

public record AdminMapView(
    String mapName,
    String description,
    String previewImageUrl,
    String lastModified,
    List<MapTag> mapTags) {

  private static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

  static AdminMapView of(MapDownloadItem item) {
    return new AdminMapView(
        item.getMapName(),
        item.getDescription(),
        item.getPreviewImageUrl(),
        FORMATTER.format(Instant.ofEpochMilli(item.getLastCommitDateEpochMilli())),
        item.getMapTags());
  }
}
