package org.triplea.services.maps.attributes;

import java.util.List;

public record AttributeWithValues(
    int id, String name, int displayOrder, List<AttributeValueRow> values) {}
