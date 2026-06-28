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

  private static final String MEMBER_GROUP = "triplea-maps:mapadmins";
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
      provider.memberGroup = MEMBER_GROUP;
      return provider;
    }

    @Test
    void memberWhenGroupsContainMemberGroup() {
      var identity =
          newProvider()
              .resolve(
                  headers(
                      Map.of(
                          EMAIL_HEADER,
                          "user@example.com",
                          GROUPS_HEADER,
                          "triplea-game:other, " + MEMBER_GROUP)));

      assertThat(identity.isAnonymous()).isFalse();
      assertThat(identity.isMember()).isTrue();
      assertThat(identity.email()).isEqualTo("user@example.com");
      assertThat(identity.groups()).contains(MEMBER_GROUP, "triplea-game:other");
    }

    @Test
    void authenticatedButNotMemberWhenGroupAbsent() {
      var identity =
          newProvider()
              .resolve(
                  headers(
                      Map.of(
                          EMAIL_HEADER, "user@example.com", GROUPS_HEADER, "triplea-game:other")));

      assertThat(identity.isAnonymous()).isFalse();
      assertThat(identity.isMember()).isFalse();
    }

    @Test
    void anonymousWhenNoEmailHeader() {
      var identity = newProvider().resolve(headers(Map.of(GROUPS_HEADER, MEMBER_GROUP)));

      assertThat(identity).isEqualTo(Identity.ANONYMOUS);
      assertThat(identity.isAnonymous()).isTrue();
      assertThat(identity.isMember()).isFalse();
    }

    @Test
    void anonymousWhenEmailHeaderBlank() {
      var identity = newProvider().resolve(headers(Map.of(EMAIL_HEADER, "  ")));

      assertThat(identity.isAnonymous()).isTrue();
    }

    @Test
    void memberWithNoGroupsHeaderIsNotMember() {
      var identity = newProvider().resolve(headers(Map.of(EMAIL_HEADER, "user@example.com")));

      assertThat(identity.isAnonymous()).isFalse();
      assertThat(identity.isMember()).isFalse();
      assertThat(identity.groups()).isEmpty();
    }
  }

  @Nested
  class DevFake {

    private DevFakeIdentityProvider newProvider(String devFakeAuth) {
      var provider = new DevFakeIdentityProvider();
      provider.devFakeAuth = Optional.ofNullable(devFakeAuth);
      provider.memberGroup = MEMBER_GROUP;
      return provider;
    }

    /// Dev provider ignores headers entirely.
    private static final HeaderLookup IGNORED = name -> "should-be-ignored";

    @Test
    void memberSynthesizesMemberIdentity() {
      var identity = newProvider("member").resolve(IGNORED);

      assertThat(identity.isAnonymous()).isFalse();
      assertThat(identity.isMember()).isTrue();
      assertThat(identity.groups()).containsExactly(MEMBER_GROUP);
    }

    @Test
    void memberIsCaseInsensitiveAndTrimmed() {
      assertThat(newProvider(" Member ").resolve(IGNORED).isMember()).isTrue();
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
