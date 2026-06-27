package org.triplea.services.auth;

import java.util.Set;

/**
 * The identity of the caller for a single request. Resolved once per request by {@link
 * IdentityProducer} from exactly one source (proxy headers in prod, {@code DEV_FAKE_AUTH} in dev).
 *
 * <p>Authorization is binary: a caller is either an anonymous reader or a member of the one GitHub
 * team that grants read/write. {@link #ANONYMOUS} is the null-object default used whenever no
 * identifying email is present.
 *
 * @param email the caller's email, or {@code null} when anonymous.
 * @param groups the groups/teams the caller belongs to (e.g. {@code "triplea-game:maintainers"}).
 * @param memberGroup the group whose presence in {@link #groups} grants membership; {@code null}
 *     for the anonymous null-object.
 */
public record Identity(String email, Set<String> groups, String memberGroup) {

  /** The read-only, unauthenticated default identity. */
  public static final Identity ANONYMOUS = new Identity(null, Set.of(), null);

  public Identity {
    groups = (groups == null) ? Set.of() : Set.copyOf(groups);
  }

  public boolean isAnonymous() {
    return email == null || email.isBlank();
  }

  /** True only for an authenticated caller belonging to the membership group. */
  public boolean isMember() {
    return !isAnonymous() && memberGroup != null && groups.contains(memberGroup);
  }
}
