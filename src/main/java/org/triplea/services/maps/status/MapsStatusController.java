package org.triplea.services.maps.status;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.function.Supplier;
import org.jdbi.v3.core.Jdbi;
import org.triplea.http.client.lobby.maps.listing.MapDownloadItem;
import org.triplea.services.maps.listing.MapsListingModule;

/**
 * Renders the public map status page: a read-only listing of all indexed maps and their current
 * attributes. Public path (no auth required); write controls will later be shown conditionally for
 * authenticated team members.
 */
@Path("/support/maps/status")
@ApplicationScoped
public class MapsStatusController {

  @Inject Jdbi jdbi;

  private Supplier<List<MapDownloadItem>> mapListingSupplier;

  @PostConstruct
  void init() {
    mapListingSupplier = MapsListingModule.build(jdbi);
  }

  @CheckedTemplate
  public static class Templates {
    public static native TemplateInstance statusPage(List<MapsStatusView> maps);
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance statusPage() {
    return Templates.statusPage(mapListingSupplier.get().stream().map(MapsStatusView::of).toList());
  }
}
