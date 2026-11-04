package org.triplea.services.maps.status;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/// View model for one map row on the status page. `tagIds` marks the map's tags via [#hasTag].
public record MapStatusItem(
    long id,
    String mapName,
    String description,
    String previewImageUrl,
    String lastModified,
    boolean enabled,
    String disableReason,
    boolean adminEnabled,
    String adminDisableReason,
    String lastIndexed,
    Set<Integer> tagIds) {

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
        row.disableReason(),
        row.adminEnabled(),
        row.adminDisableReason(),
        row.lastIndexed() == null ? "Never" : FORMATTER.format(row.lastIndexed()),
        row.tagIds());
  }

  /// Human-readable label for the Status column: "Available" when enabled, else "Disabled".
  public String enabledLabel() {
    return enabled ? "Available" : "Disabled";
  }

  /// Human-readable label for the Admin column: "Approved" when admin-enabled, else "Disabled".
  public String adminEnabledLabel() {
    return adminEnabled ? "Approved" : "Disabled";
  }

  /// True when the map has the tag `tagId` (marks the chosen `<option>`).
  public boolean hasTag(int tagId) {
    return tagIds.contains(tagId);
  }
}
