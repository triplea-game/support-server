package org.triplea.services.maps.status;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import org.jdbi.v3.core.Jdbi;
import org.triplea.services.auth.CsrfProtected;
import org.triplea.services.auth.CsrfTokenProvider;
import org.triplea.services.auth.Identity;
import org.triplea.services.auth.RequestIdentity;
import org.triplea.services.auth.RequiresMapAdmin;
import org.triplea.services.maps.tags.MapTagDao;
import org.triplea.services.maps.tags.MapTagRow;

/// Renders the public map status page: a listing of all indexed maps and their tags.
///
/// The GET render is public; the per-map edit controls (tag chips with a remove button, an add-tag
/// input and the admin approval) are shown only to a MapAdmin. Each change runs through a MapAdmin
/// mutation below, which, keyed off the `HX-Request` header HTMX sets, answers HTMX with the
/// re-rendered cell and a no-JavaScript client with the classic POST-redirect-GET.
///
/// The class is `@CsrfProtected` so the GET issues the CSRF cookie the edit forms need; the GET
/// itself is a safe method and so bypasses the CSRF check.
@Path("support/maps/status")
@ApplicationScoped
@CsrfProtected
public class MapsStatusController {

  private static final URI SELF = URI.create("/support/maps/status");

  @Inject Jdbi jdbi;
  @Inject CsrfTokenProvider csrfTokenProvider;
  @Inject RequestIdentity requestIdentity;

  private MapStatusDao statusDao;
  private MapTagDao tagDao;

  @PostConstruct
  void init() {
    statusDao = new MapStatusDao(jdbi);
    tagDao = new MapTagDao(jdbi);
  }

  @CheckedTemplate
  public static class Templates {
    public static native TemplateInstance statusPage(
        List<MapStatusItem> maps, List<MapTagRow> tags, String csrfToken, Identity identity);

    /// One map's admin-approval `<td>` — the swap unit after an admin enable/disable.
    public static native TemplateInstance statusPage$adminStatus(
        MapStatusItem map, String csrfToken, Identity identity);

    /// One map's tags `<td>` — the swap unit after its tags are saved.
    public static native TemplateInstance statusPage$mapTags(
        MapStatusItem map, List<MapTagRow> tags, String csrfToken, Identity identity);
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance statusPage() {
    var identity = requestIdentity.get();
    return Templates.statusPage(loadMaps(), tagDao.listTags(), csrfToken(identity), identity);
  }

  /// Adds the tag named `tagName` (trimmed, any case) to the map; an unknown name is ignored.
  @POST
  @Path("{mapId}/tags/add")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @RequiresMapAdmin
  public Response addMapTag(
      @PathParam("mapId") long mapId,
      @FormParam("tagName") @DefaultValue("") String tagName,
      @HeaderParam("HX-Request") @DefaultValue("") String hxRequest) {
    statusDao.addTag(mapId, tagName.trim());
    return hxRequest.isBlank() ? redirectHome() : tagsFragment(mapId);
  }

  @POST
  @Path("{mapId}/tags/{tagId}/remove")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @RequiresMapAdmin
  public Response removeMapTag(
      @PathParam("mapId") long mapId,
      @PathParam("tagId") int tagId,
      @HeaderParam("HX-Request") @DefaultValue("") String hxRequest) {
    statusDao.removeTag(mapId, tagId);
    return hxRequest.isBlank() ? redirectHome() : tagsFragment(mapId);
  }

  /// Admin-disables a map. A reason is required (it's shown to map makers explaining why the map is
  /// not public, and the DB constraint rejects a disabled map without one).
  @POST
  @Path("{mapId}/admin-disable")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @RequiresMapAdmin
  public Response adminDisableMap(
      @PathParam("mapId") long mapId,
      @FormParam("reason") @DefaultValue("") String reason,
      @HeaderParam("HX-Request") @DefaultValue("") String hxRequest) {
    if (reason.isBlank()) {
      throw new BadRequestException("A reason is required to disable a map.");
    }
    statusDao.disableMap(mapId, reason.trim());
    return hxRequest.isBlank() ? redirectHome() : adminFragment(mapId);
  }

  /// Admin-approves a map. No reason needed; this clears any "pending approval"/disable reason.
  @POST
  @Path("{mapId}/admin-enable")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @RequiresMapAdmin
  public Response adminEnableMap(
      @PathParam("mapId") long mapId,
      @HeaderParam("HX-Request") @DefaultValue("") String hxRequest) {
    statusDao.approveMap(mapId);
    return hxRequest.isBlank() ? redirectHome() : adminFragment(mapId);
  }

  /// Re-renders just the map's admin-approval cell for an HTMX `outerHTML` swap.
  private Response adminFragment(long mapId) {
    var identity = requestIdentity.get();
    var map =
        loadMaps().stream()
            .filter(m -> m.id() == mapId)
            .findFirst()
            .orElseThrow(NotFoundException::new);
    return Response.ok(Templates.statusPage$adminStatus(map, csrfToken(identity), identity))
        .type(MediaType.TEXT_HTML)
        .build();
  }

  /// Re-renders just the map's tags cell for an HTMX `outerHTML` swap.
  private Response tagsFragment(long mapId) {
    var identity = requestIdentity.get();
    var map =
        loadMaps().stream()
            .filter(m -> m.id() == mapId)
            .findFirst()
            .orElseThrow(NotFoundException::new);
    return Response.ok(
            Templates.statusPage$mapTags(map, tagDao.listTags(), csrfToken(identity), identity))
        .type(MediaType.TEXT_HTML)
        .build();
  }

  private List<MapStatusItem> loadMaps() {
    return statusDao.listMaps().stream().map(MapStatusItem::of).toList();
  }

  /// A CSRF token (and thus the issued cookie) only for MapAdmins, who are the only callers that
  /// render the edit forms; anonymous visitors of the public page get neither.
  private String csrfToken(Identity identity) {
    return identity.isMapAdmin() ? csrfTokenProvider.token() : "";
  }

  private static Response redirectHome() {
    return Response.seeOther(SELF).build();
  }
}
