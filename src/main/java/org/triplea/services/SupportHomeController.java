package org.triplea.services;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.triplea.services.auth.Identity;
import org.triplea.services.auth.RequestIdentity;

/// Renders the public support landing page: links onward to the maps status page and, for
/// MapAdmins, the admin tools. The login control lives in the shared nav header.
@Path("/support")
@ApplicationScoped
public class SupportHomeController {

  @Inject RequestIdentity requestIdentity;

  @CheckedTemplate
  public static class Templates {
    public static native TemplateInstance indexPage(Identity identity);
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance indexPage() {
    return Templates.indexPage(requestIdentity.get());
  }
}
