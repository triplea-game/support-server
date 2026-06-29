package org.triplea.services.maps.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.triplea.http.client.lobby.maps.listing.MapTag;

/// A map and its currently-assigned attributes, as read for the status page.
///
/// `tags` is the read-only `name: value` view (the values actually set). `selections` maps each
/// assigned attribute's id to its chosen value's id, which drives the admin edit dropdowns (a
/// dimension absent from the map is simply absent from the map).
public record MapStatusRow(
    long id,
    String mapName,
    String previewImageUrl,
    String description,
    Instant lastCommitDate,
    List<MapTag> tags,
    Map<Integer, Integer> selections) {}
