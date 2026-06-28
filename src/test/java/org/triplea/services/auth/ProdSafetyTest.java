package org.triplea.services.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.runtime.LaunchMode;
import org.junit.jupiter.api.Test;
import org.triplea.services.auth.IdentityProvider.HeaderLookup;

/// Prod-safety: the worst-case failure is "everyone silently a MapAdmin". A packaged production
/// build ([LaunchMode#NORMAL]) must resolve to the header provider — and therefore to anonymous
/// when no proxy headers are present — even when `DEV_FAKE_AUTH=mapadmin` is set.
///
/// This exercises the pure [RequestIdentity#select] gate directly so the guarantee is
/// verifiable without a packaged build (a `@QuarkusTest` runs in [LaunchMode#TEST]).
class ProdSafetyTest {

  private final IdentityProvider headerProvider = new HeaderIdentityProvider();
  private final IdentityProvider devProvider = new DevFakeIdentityProvider();

  @Test
  void prodIgnoresDevFakeAuthEvenWhenSetToMapAdmin() {
    var selected =
        RequestIdentity.select(LaunchMode.NORMAL, "mapadmin", headerProvider, devProvider);

    assertThat(selected).isSameAs(headerProvider);
  }

  @Test
  void prodWithNoHeadersIsAnonymous() {
    var selected =
        RequestIdentity.select(LaunchMode.NORMAL, "mapadmin", headerProvider, devProvider);

    // No X-Auth-* headers present -> anonymous, regardless of DEV_FAKE_AUTH.
    var anonymousHeaders = (HeaderLookup) name -> null;
    var provider = (HeaderIdentityProvider) selected;
    provider.emailHeader = "X-Auth-Email";
    provider.groupsHeader = "X-Auth-Groups";
    provider.mapAdminGroup = "triplea-maps:mapadmins";

    assertThat(provider.resolve(anonymousHeaders)).isEqualTo(Identity.ANONYMOUS);
  }

  @Test
  void devUsesFakeAuthWhenPresent() {
    assertThat(
            RequestIdentity.select(LaunchMode.DEVELOPMENT, "mapadmin", headerProvider, devProvider))
        .isSameAs(devProvider);
  }

  @Test
  void devWithoutFakeAuthUsesHeaderProvider() {
    // e.g. `make run` behind nginx: %dev profile but DEV_FAKE_AUTH unset -> real headers.
    assertThat(RequestIdentity.select(LaunchMode.DEVELOPMENT, "", headerProvider, devProvider))
        .isSameAs(headerProvider);
    assertThat(RequestIdentity.select(LaunchMode.DEVELOPMENT, null, headerProvider, devProvider))
        .isSameAs(headerProvider);
  }
}
