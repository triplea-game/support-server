package org.triplea.services.auth;

import io.quarkus.runtime.LaunchMode;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/// Request-scoped resolver for the caller's [Identity]. Inject it into any bean (including
/// singletons, via the request-scoped client proxy) and call [#get()] to obtain the identity
/// for the current request, resolved once and memoized.
///
/// This is the single place the dev/prod identity-source selection gate lives (see [#select]):
///
/// - A packaged production build ([LaunchMode#NORMAL]) ALWAYS uses [HeaderIdentityProvider],
///   even if `DEV_FAKE_AUTH` is set in the environment — the prod-safety guarantee.
/// - Otherwise (dev/test), the [DevFakeIdentityProvider] is used when `DEV_FAKE_AUTH` is
///   present, and the header provider when it is absent (e.g. `make run` behind nginx).
///
/// Gating on `DEV_FAKE_AUTH` presence rather than the `%dev` profile is deliberate:
/// both `make dev` and `make run` run in `%dev`, so a profile gate would let
/// dev-fake-auth clobber the real proxy headers under `make run`.
///
/// `Identity` is a (final) record value type, so it cannot itself be a normal-scoped CDI
/// bean — hence this resolver rather than a `@Produces Identity`.
@RequestScoped
public class RequestIdentity {

  @Inject HeaderIdentityProvider headerIdentityProvider;
  @Inject DevFakeIdentityProvider devFakeIdentityProvider;

  // The current request's headers. RoutingContext is an injectable request-scoped CDI bean in
  // Quarkus, whereas @Context HttpHeaders is not populated in a plain CDI bean.
  @Inject RoutingContext routingContext;

  @ConfigProperty(name = "app.dev-fake-auth")
  Optional<String> devFakeAuth;

  private Identity resolved;

  /// The caller's identity for the current request, resolved once and memoized.
  public Identity get() {
    if (resolved == null) {
      resolved =
          select(
                  LaunchMode.current(),
                  devFakeAuth.orElse(null),
                  headerIdentityProvider,
                  devFakeIdentityProvider)
              .resolve(routingContext.request()::getHeader);
    }
    return resolved;
  }

  /// Pure selection function. In [LaunchMode#NORMAL] (packaged prod) the dev provider is never
  /// selectable, regardless of `devFakeAuth` — this is exercised by the prod-safety test.
  static IdentityProvider select(
      LaunchMode mode,
      String devFakeAuth,
      IdentityProvider headerProvider,
      IdentityProvider devProvider) {
    if (mode == LaunchMode.NORMAL) {
      return headerProvider;
    }
    return (devFakeAuth != null && !devFakeAuth.isBlank()) ? devProvider : headerProvider;
  }
}
