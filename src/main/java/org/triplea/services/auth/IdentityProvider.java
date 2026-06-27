package org.triplea.services.auth;

/**
 * Resolves the {@link Identity} for a request from a single source. Exactly one implementation is
 * active per deployment, chosen at request time by {@link IdentityProducer}:
 *
 * <ul>
 *   <li>{@link HeaderIdentityProvider} — production; reads proxy-injected headers.
 *   <li>{@link DevFakeIdentityProvider} — local dev; synthesizes identity from an env var.
 * </ul>
 */
public interface IdentityProvider {

  Identity resolve(HeaderLookup headers);

  /**
   * Minimal request-header accessor, decoupling providers (and their unit tests) from any HTTP
   * framework type.
   */
  @FunctionalInterface
  interface HeaderLookup {
    /**
     * Returns the value of {@code name}, or {@code null} if absent. Multi-valued → comma-joined.
     */
    String get(String name);
  }
}
