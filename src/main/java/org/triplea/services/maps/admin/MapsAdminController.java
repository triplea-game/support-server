package org.triplea.services.maps.admin;

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
 * Renders the maps admin page, map admin page can be used to manage maps, namely to add/remove
 * attributes. Auth is intended to be handled by NGINX.
 */
@Path("/support/admin/map/listing")
@ApplicationScoped
public class MapsAdminController {

  @Inject Jdbi jdbi;

  private Supplier<List<MapDownloadItem>> mapListingSupplier;

  @PostConstruct
  void init() {
    mapListingSupplier = MapsListingModule.build(jdbi);
  }

  @CheckedTemplate
  public static class Templates {
    public static native TemplateInstance adminPage(List<AdminMapView> maps);
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance adminPage() {
    return Templates.adminPage(mapListingSupplier.get().stream().map(AdminMapView::of).toList());
  }
}
