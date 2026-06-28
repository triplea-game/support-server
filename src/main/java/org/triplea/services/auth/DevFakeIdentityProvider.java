package org.triplea.services.auth;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/// Local-development identity source: synthesizes an identity from the `DEV_FAKE_AUTH` value
/// (`mapadmin` | `anon`), ignoring all request headers. This lets a newcomer exercise both
/// the MapAdmin and anonymous postures with zero proxy/GitHub setup.
///
/// It is selected only outside a packaged production build — see [IdentityProducer#select];
/// it can never activate in prod even if `DEV_FAKE_AUTH` is present in the environment.
@ApplicationScoped
public class DevFakeIdentityProvider implements IdentityProvider {

  static final String MAP_ADMIN = "mapadmin";
  static final String DEV_EMAIL = "dev-mapadmin@localhost";

  // Optional rather than a String default: an empty value (DEV_FAKE_AUTH unset) is converted to
  // Optional.empty by SmallRye, whereas an empty-String default is rejected as null at startup.
  @ConfigProperty(name = "app.dev-fake-auth")
  Optional<String> devFakeAuth;

  @ConfigProperty(name = "app.auth.map-admin-group")
  String mapAdminGroup;

  @Override
  public Identity resolve(HeaderLookup headers) {
    if (MAP_ADMIN.equalsIgnoreCase(devFakeAuth.map(String::trim).orElse(""))) {
      return new Identity(DEV_EMAIL, Set.of(mapAdminGroup), mapAdminGroup);
    }
    return Identity.ANONYMOUS;
  }
}
