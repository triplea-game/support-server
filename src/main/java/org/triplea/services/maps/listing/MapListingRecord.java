package org.triplea.services.maps.listing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;
import org.triplea.http.client.lobby.maps.listing.MapDownloadItem;
import org.triplea.http.client.lobby.maps.listing.MapTag;

@Setter
@Getter
@Builder
@AllArgsConstructor
public class MapListingRecord {
  String mapName;
  String downloadUrl;
  String previewImageUrl;
  String description;
  Instant lastCommitDate;
  Long downloadSizeBytes;
  @Builder.Default List<MapTag> tags = new ArrayList<>();

  @SuppressWarnings("unused")
  @JdbiConstructor
  public MapListingRecord() {}

  List<MapTag> getTags() {
    if (tags == null) {
      tags = new ArrayList<>();
    }
    return tags;
  }

  MapDownloadItem toMapDownloadItem() {
    return MapDownloadItem.builder()
        .downloadUrl(downloadUrl)
        .downloadSizeInBytes(downloadSizeBytes)
        .previewImageUrl(previewImageUrl)
        .mapName(mapName)
        .lastCommitDateEpochMilli(lastCommitDate.toEpochMilli())
        .description(description)
        .mapTags(tags)
        .build();
  }
}
