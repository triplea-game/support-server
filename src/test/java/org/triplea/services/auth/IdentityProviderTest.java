package org.triplea.services.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.triplea.services.auth.IdentityProvider.HeaderLookup;

/// Pure unit tests for the two identity sources — header parsing for prod and `DEV_FAKE_AUTH`
/// synthesis for dev. No Quarkus container; config fields are set directly.
class IdentityProviderTest {

  private static final String MAP_ADMIN_GROUP = "triplea-maps:mapadmins";
  private static final String EMAIL_HEADER = "X-Auth-Email";
  private static final String GROUPS_HEADER = "X-Auth-Groups";

  /// Header lookup backed by a fixed map; absent keys return null.
  private static HeaderLookup headers(Map<String, String> values) {
    return values::get;
  }

  @Nested
  class Header {

    private HeaderIdentityProvider newProvider() {
      var provider = new HeaderIdentityProvider();
      provider.emailHeader = EMAIL_HEADER;
      provider.groupsHeader = GROUPS_HEADER;
      provider.mapAdminGroup = MAP_ADMIN_GROUP;
      return provider;
    }

    @Test
    void mapAdminWhenGroupsContainMapAdminGroup() {
      var identity =
          newProvider()
              .resolve(
                  headers(
                      Map.of(
                          EMAIL_HEADER,
                          "user@example.com",
                          GROUPS_HEADER,
                          "triplea-maps:other, " + MAP_ADMIN_GROUP)));

      assertThat(identity.isAnonymous()).isFalse();
      assertThat(identity.isMapAdmin()).isTrue();
      assertThat(identity.email()).isEqualTo("user@example.com");
      assertThat(identity.groups()).contains(MAP_ADMIN_GROUP, "triplea-maps:other");
    }

    @Test
    void authenticatedButNotMapAdminWhenGroupAbsent() {
      var identity =
          newProvider()
              .resolve(
                  headers(
                      Map.of(
                          EMAIL_HEADER, "user@example.com", GROUPS_HEADER, "triplea-maps:other")));

      assertThat(identity.isAnonymous()).isFalse();
      assertThat(identity.isMapAdmin()).isFalse();
    }

    @Test
    void anonymousWhenNoEmailHeader() {
      var identity = newProvider().resolve(headers(Map.of(GROUPS_HEADER, MAP_ADMIN_GROUP)));

      assertThat(identity).isEqualTo(Identity.ANONYMOUS);
      assertThat(identity.isAnonymous()).isTrue();
      assertThat(identity.isMapAdmin()).isFalse();
    }

    @Test
    void anonymousWhenEmailHeaderBlank() {
      var identity = newProvider().resolve(headers(Map.of(EMAIL_HEADER, "  ")));

      assertThat(identity.isAnonymous()).isTrue();
    }

    @Test
    void mapAdminWithNoGroupsHeaderIsNotMapAdmin() {
      var identity = newProvider().resolve(headers(Map.of(EMAIL_HEADER, "user@example.com")));

      assertThat(identity.isAnonymous()).isFalse();
      assertThat(identity.isMapAdmin()).isFalse();
      assertThat(identity.groups()).isEmpty();
    }
  }

  @Nested
  class DevFake {

    private DevFakeIdentityProvider newProvider(String devFakeAuth) {
      var provider = new DevFakeIdentityProvider();
      provider.devFakeAuth = Optional.ofNullable(devFakeAuth);
      provider.mapAdminGroup = MAP_ADMIN_GROUP;
      return provider;
    }

    /// Dev provider ignores headers entirely.
    private static final HeaderLookup IGNORED = name -> "should-be-ignored";

    @Test
    void mapAdminSynthesizesMapAdminIdentity() {
      var identity = newProvider("mapadmin").resolve(IGNORED);

      assertThat(identity.isAnonymous()).isFalse();
      assertThat(identity.isMapAdmin()).isTrue();
      assertThat(identity.groups()).containsExactly(MAP_ADMIN_GROUP);
    }

    @Test
    void mapAdminIsCaseInsensitiveAndTrimmed() {
      assertThat(newProvider(" MapAdmin ").resolve(IGNORED).isMapAdmin()).isTrue();
    }

    @Test
    void anonValueSynthesizesAnonymous() {
      assertThat(newProvider("anon").resolve(IGNORED)).isEqualTo(Identity.ANONYMOUS);
    }

    @Test
    void blankOrUnknownValueSynthesizesAnonymous() {
      assertThat(newProvider("").resolve(IGNORED).isAnonymous()).isTrue();
      assertThat(newProvider("nonsense").resolve(IGNORED).isAnonymous()).isTrue();
    }
  }
}
