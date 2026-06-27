package org.triplea.services.auth;

/// Resolves the [Identity] for a request from a single source. Exactly one implementation is
/// active per deployment, chosen at request time by [IdentityProducer]:
///
/// - [HeaderIdentityProvider] — production; reads proxy-injected headers.
/// - [DevFakeIdentityProvider] — local dev; synthesizes identity from an env var.
public interface IdentityProvider {

  Identity resolve(HeaderLookup headers);

  /// Minimal request-header accessor, decoupling providers (and their unit tests) from any HTTP
  /// framework type.
  @FunctionalInterface
  interface HeaderLookup {
    /// Returns the value of `name`, or `null` if absent. Multi-valued → comma-joined.
    String get(String name);
  }
}
