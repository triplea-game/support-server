package org.triplea.services.auth;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Production identity source: derives identity from the {@code X-Auth-*} headers injected by the
 * oauth2-proxy / nginx reverse proxy. Absent the email header, the request is {@link
 * Identity#ANONYMOUS}.
 *
 * <p>This is the only identity path in a real deployment. nginx always overwrites the {@code
 * X-Auth-*} headers from the auth subrequest, so a client cannot spoof identity by sending its own.
 */
@ApplicationScoped
public class HeaderIdentityProvider implements IdentityProvider {

  @ConfigProperty(name = "app.auth.email-header", defaultValue = "X-Auth-Email")
  String emailHeader;

  @ConfigProperty(name = "app.auth.groups-header", defaultValue = "X-Auth-Groups")
  String groupsHeader;

  @ConfigProperty(name = "app.auth.member-group", defaultValue = "triplea-game:maintainers")
  String memberGroup;

  @Override
  public Identity resolve(HeaderLookup headers) {
    String email = headers.get(emailHeader);
    if (email == null || email.isBlank()) {
      return Identity.ANONYMOUS;
    }
    Set<String> groups = parseGroups(headers.get(groupsHeader));
    return new Identity(email.trim(), groups, memberGroup);
  }

  private static Set<String> parseGroups(String rawGroups) {
    if (rawGroups == null || rawGroups.isBlank()) {
      return Set.of();
    }
    return Arrays.stream(rawGroups.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toUnmodifiableSet());
  }
}
