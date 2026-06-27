package org.triplea.services.auth;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/// Local-development identity source: synthesizes an identity from the `DEV_FAKE_AUTH` value
/// (`member` | `anon`), ignoring all request headers. This lets a newcomer exercise both
/// the member and anonymous postures with zero proxy/GitHub setup.
///
/// It is selected only outside a packaged production build — see [IdentityProducer#select];
/// it can never activate in prod even if `DEV_FAKE_AUTH` is present in the environment.
@ApplicationScoped
public class DevFakeIdentityProvider implements IdentityProvider {

  static final String MEMBER = "member";
  static final String DEV_EMAIL = "dev-member@localhost";

  // Optional rather than a String default: an empty value (DEV_FAKE_AUTH unset) is converted to
  // Optional.empty by SmallRye, whereas an empty-String default is rejected as null at startup.
  @ConfigProperty(name = "app.dev-fake-auth")
  Optional<String> devFakeAuth;

  @ConfigProperty(name = "app.auth.member-group", defaultValue = "triplea-game:maintainers")
  String memberGroup;

  @Override
  public Identity resolve(HeaderLookup headers) {
    if (MEMBER.equalsIgnoreCase(devFakeAuth.map(String::trim).orElse(""))) {
      return new Identity(DEV_EMAIL, Set.of(memberGroup), memberGroup);
    }
    return Identity.ANONYMOUS;
  }
}
