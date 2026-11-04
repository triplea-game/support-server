package org.triplea.services.maps.status;

import java.time.Instant;
import java.util.Set;

/// A map and the ids of its tags from the `map_tag` list, as read for the status page.
///
/// `enabled`/`disableReason` are the indexer's health flag; `adminEnabled`/`adminDisableReason` are
/// the independent MapAdmin approval flag. A map is publicly listed only when both are enabled.
public record MapStatusRow(
    long id,
    String mapName,
    String previewImageUrl,
    String description,
    Instant lastCommitDate,
    boolean enabled,
    String disableReason,
    boolean adminEnabled,
    String adminDisableReason,
    Instant lastIndexed,
    Set<Integer> tagIds) {}
