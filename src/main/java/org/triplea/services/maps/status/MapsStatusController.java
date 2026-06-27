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
import org.triplea.services.auth.Identity;
import org.triplea.services.auth.RequestIdentity;
import org.triplea.services.maps.listing.MapsListingModule;

/// Renders the public map status page: a listing of all indexed maps and their current attributes.
/// The GET is public (no auth required) and stays unannotated.
///
/// The resolved [Identity] is passed to the template so it can conditionally render
/// write-enabled controls for team members (`identity.member`) and a logout link for any
/// authenticated caller (`!identity.anonymous`). The write controls themselves — and the
/// member-only POST endpoints they target (which would carry `@RequiresMember`) — are a
/// separate story; this only wires the identity through so those controls have what they need.
@Path("/support/maps/status")
@ApplicationScoped
public class MapsStatusController {

  @Inject Jdbi jdbi;
  @Inject RequestIdentity requestIdentity;

  private Supplier<List<MapDownloadItem>> mapListingSupplier;

  @PostConstruct
  void init() {
    mapListingSupplier = MapsListingModule.build(jdbi);
  }

  @CheckedTemplate
  public static class Templates {
    public static native TemplateInstance statusPage(List<MapsStatusView> maps, Identity identity);
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance statusPage() {
    var maps = mapListingSupplier.get().stream().map(MapsStatusView::of).toList();
    return Templates.statusPage(maps, requestIdentity.get());
  }
}
