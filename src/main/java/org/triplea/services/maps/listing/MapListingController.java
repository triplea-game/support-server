package org.triplea.services.maps.listing;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.function.Supplier;
import org.jdbi.v3.core.Jdbi;
import org.triplea.http.client.ServerPaths;
import org.triplea.http.client.lobby.maps.listing.MapDownloadItem;
import org.triplea.http.client.lobby.maps.listing.MapListingResponse;

@Path("")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class MapListingController {

  @Inject Jdbi jdbi;

  private Supplier<List<MapDownloadItem>> downloadListingSupplier;

  @PostConstruct
  void init() {
    downloadListingSupplier = MapsListingModule.build(jdbi);
  }

  /// Returns the full set of maps available for download.
  @GET
  @Path(ServerPaths.MAPS_LISTING_PATH)
  public MapListingResponse fetchAvailableMaps() {
    return MapListingResponse.builder().maps(downloadListingSupplier.get()).build();
  }
}
