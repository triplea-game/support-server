package org.triplea.services.auth;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Rejects any request to a {@link RequiresMember}-annotated resource whose caller is not a member
 * of the authorizing team. Anonymous and authenticated-non-member callers alike receive 401, a
 * single contract that Phase 7 can map to a login redirect for HTMX.
 *
 * <p>This is the server-side authorization gate: it holds independently of the UI (hidden buttons
 * are not authorization) and independently of any reverse-proxy configuration (defense in depth).
 */
@Provider
@RequiresMember
@Priority(Priorities.AUTHORIZATION)
public class MemberAuthFilter implements ContainerRequestFilter {

  @Inject RequestIdentity requestIdentity;

  @Override
  public void filter(ContainerRequestContext requestContext) {
    if (!requestIdentity.get().isMember()) {
      requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
    }
  }
}
