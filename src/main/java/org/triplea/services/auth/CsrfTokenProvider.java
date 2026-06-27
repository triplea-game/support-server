package org.triplea.services.auth;

import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Request-scoped source of the caller's CSRF token, implementing the <em>double-submit cookie</em>
 * pattern. The token lives in a cookie (set by the browser on every same-site request) and is also
 * embedded as a hidden form field rendered server-side; a forged cross-site POST can submit the
 * cookie (sent automatically) but cannot read it to forge a matching field, so the two won't agree.
 *
 * <p>Within a single request the token is resolved once and memoized: the existing {@code
 * csrf_token} cookie if present, otherwise a freshly generated value. {@link #isGenerated()} lets
 * {@link CsrfCookieResponseFilter} know whether it must emit a {@code Set-Cookie}.
 *
 * <p>Header/cookie access uses the injectable Vert.x {@link RoutingContext} rather than
 * {@code @Context HttpHeaders}, which is null in a plain CDI bean (same rationale as {@link
 * RequestIdentity}).
 */
@RequestScoped
public class CsrfTokenProvider {

  /** Cookie carrying the token; compared against the form field by {@link CsrfRequestFilter}. */
  public static final String COOKIE_NAME = "csrf_token";

  /** Hidden form field name the token is submitted under. */
  public static final String FORM_FIELD = "_csrf";

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int TOKEN_BYTES = 32;

  @Inject RoutingContext routingContext;

  private String token;
  private boolean generated;

  /** The token for this request — the existing cookie value, or a newly generated one. */
  public String token() {
    if (token == null) {
      var cookie = routingContext.request().getCookie(COOKIE_NAME);
      if (cookie != null && cookie.getValue() != null && !cookie.getValue().isBlank()) {
        token = cookie.getValue();
      } else {
        token = generate();
        generated = true;
      }
    }
    return token;
  }

  /** True when {@link #token()} had to mint a new token (no usable cookie was present). */
  public boolean isGenerated() {
    token();
    return generated;
  }

  private static String generate() {
    byte[] bytes = new byte[TOKEN_BYTES];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
