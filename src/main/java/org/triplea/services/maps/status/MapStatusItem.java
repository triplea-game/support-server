package org.triplea.services.maps.status;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.triplea.http.client.lobby.maps.listing.MapTag;

/// View model for one map row on the status page.
///
/// `mapAttributes` is the read-only `name: value` list shown to everyone; `selections` (attribute
/// id -> value id) backs the MapAdmin edit dropdowns via [#isSelected] / [#hasAttribute].
public record MapStatusItem(
    long id,
    String mapName,
    String description,
    String previewImageUrl,
    String lastModified,
    boolean enabled,
    List<MapTag> mapAttributes,
    Map<Integer, Integer> selections) {

  private static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

  static MapStatusItem of(MapStatusRow row) {
    return new MapStatusItem(
        row.id(),
        row.mapName(),
        row.description(),
        row.previewImageUrl(),
        FORMATTER.format(row.lastCommitDate()),
        row.enabled(),
        row.tags(),
        row.selections());
  }

  /// Human-readable label for the Status column: "Available" when enabled, else "Disabled".
  public String enabledLabel() {
    return enabled ? "Available" : "Disabled";
  }

  /// True when this map's value for `attributeId` is `valueId` (marks the chosen `<option>`).
  public boolean isSelected(int attributeId, int valueId) {
    return selections.getOrDefault(attributeId, Integer.MIN_VALUE) == valueId;
  }

  /// True when this map has any value for `attributeId` (i.e. the "— none —" option is not chosen).
  public boolean hasAttribute(int attributeId) {
    return selections.containsKey(attributeId);
  }
}
