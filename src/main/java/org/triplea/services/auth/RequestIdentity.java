package org.triplea.services.auth;

import io.quarkus.runtime.LaunchMode;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Request-scoped resolver for the caller's {@link Identity}. Inject it into any bean (including
 * singletons, via the request-scoped client proxy) and call {@link #get()} to obtain the identity
 * for the current request, resolved once and memoized.
 *
 * <p>This is the single place the dev/prod identity-source selection gate lives (see {@link
 * #select}):
 *
 * <ul>
 *   <li>A packaged production build ({@link LaunchMode#NORMAL}) ALWAYS uses {@link
 *       HeaderIdentityProvider}, even if {@code DEV_FAKE_AUTH} is set in the environment — the
 *       prod-safety guarantee.
 *   <li>Otherwise (dev/test), the {@link DevFakeIdentityProvider} is used when {@code
 *       DEV_FAKE_AUTH} is present, and the header provider when it is absent (e.g. {@code make run}
 *       behind nginx).
 * </ul>
 *
 * <p>Gating on {@code DEV_FAKE_AUTH} presence rather than the {@code %dev} profile is deliberate:
 * both {@code make dev} and {@code make run} run in {@code %dev}, so a profile gate would let
 * dev-fake-auth clobber the real proxy headers under {@code make run}.
 *
 * <p>{@code Identity} is a (final) record value type, so it cannot itself be a normal-scoped CDI
 * bean — hence this resolver rather than a {@code @Produces Identity}.
 */
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

  /** The caller's identity for the current request, resolved once and memoized. */
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

  /**
   * Pure selection function. In {@link LaunchMode#NORMAL} (packaged prod) the dev provider is never
   * selectable, regardless of {@code devFakeAuth} — this is exercised by the prod-safety test.
   */
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
