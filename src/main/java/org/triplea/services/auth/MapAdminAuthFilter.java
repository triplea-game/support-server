package org.triplea.services.auth;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/// Rejects any request to a [RequiresMapAdmin]-annotated resource whose caller is not a MapAdmin
/// of the authorizing team. Anonymous and authenticated-non-MapAdmin callers alike receive 401, a
/// single contract that Phase 7 can map to a login redirect for HTMX.
///
/// This is the server-side authorization gate: it holds independently of the UI (hidden buttons
/// are not authorization) and independently of any reverse-proxy configuration (defense in depth).
@Provider
@RequiresMapAdmin
@Priority(Priorities.AUTHORIZATION)
public class MapAdminAuthFilter implements ContainerRequestFilter {

  @Inject RequestIdentity requestIdentity;

  @Override
  public void filter(ContainerRequestContext requestContext) {
    if (!requestIdentity.get().isMapAdmin()) {
      requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
    }
  }
}
