package org.triplea.http.client.github;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** Represents request data to create a github issue. */
@ToString
@EqualsAndHashCode
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateIssueRequest {
  private String title;
  private String body;
  private String[] labels;

  public String getTitle() {
    final int maxLength = 125;
    return title == null ? null : truncate(title, maxLength);
  }

  public String getBody() {
    final int maxLength = 65536;
    return body == null ? null : truncate(body, maxLength);
  }

  private static String truncate(final String stringToTruncate, final int maxLength) {
    if (maxLength < "...".length()) {
      throw new IllegalArgumentException(
          String.format(
              "Illegal max length for truncate requested: %s, must be at least 3, the length of the ellipsis",
              maxLength));
    }
    final String s = stringToTruncate == null ? "" : stringToTruncate;
    if (s.length() <= maxLength) {
      return s;
    }
    final String ellipsis = "...";
    return s.substring(0, Math.max(0, maxLength - ellipsis.length())) + ellipsis;
  }
}
