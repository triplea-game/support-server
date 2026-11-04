package org.triplea.services.maps.tags;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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

/// Renders and edits the list of map tags.
///
/// Each mutating endpoint serves two clients from one method, keyed off the `HX-Request` header
/// HTMX sets on its requests:
///   - **HTMX** -> `200` with the re-rendered fragment (the tag row for rename, else the whole
///     list), swapped into the page in place.
///   - **No JavaScript** -> the classic POST-redirect-GET (`303` back to [#SELF]).
@Path("support/admin/map/tags")
@ApplicationScoped
@RequiresMapAdmin
@CsrfProtected
public class MapTagsController {

  private static final URI SELF = URI.create("/support/admin/map/tags");

  @Inject Jdbi jdbi;
  @Inject CsrfTokenProvider csrfTokenProvider;
  @Inject RequestIdentity requestIdentity;

  private MapTagDao dao;

  @PostConstruct
  void init() {
    dao = new MapTagDao(jdbi);
  }

  @CheckedTemplate
  public static class Templates {
    public static native TemplateInstance tagsPage(
        List<MapTagRow> tags, String csrfToken, Identity identity);

    /// Whole tag list (incl. its `#tag-list` wrapper and empty-state) — the swap unit for add /
    /// delete / move.
    public static native TemplateInstance tagsPage$tagList(List<MapTagRow> tags, String csrfToken);

    /// A single tag's `<form>` row — the swap unit for rename.
    public static native TemplateInstance tagsPage$tagRow(MapTagRow tag, String csrfToken);
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance tagsPage() {
    return Templates.tagsPage(dao.listTags(), csrfTokenProvider.token(), requestIdentity.get());
  }

  @POST
  @Path("tag")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response createTag(
      @FormParam("name") String name,
      @HeaderParam("HX-Request") @DefaultValue("") String hxRequest) {
    dao.createTag(name.trim());
    return hxRequest.isBlank() ? redirectHome() : listFragment();
  }

  @POST
  @Path("tag/{id}/rename")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response renameTag(
      @PathParam("id") int id,
      @FormParam("name") String name,
      @HeaderParam("HX-Request") @DefaultValue("") String hxRequest) {
    dao.renameTag(id, name.trim());
    return hxRequest.isBlank()
        ? redirectHome()
        : fragment(Templates.tagsPage$tagRow(findTag(id), csrfTokenProvider.token()));
  }

  @POST
  @Path("tag/{id}/delete")
  public Response deleteTag(
      @PathParam("id") int id, @HeaderParam("HX-Request") @DefaultValue("") String hxRequest) {
    dao.deleteTag(id);
    return hxRequest.isBlank() ? redirectHome() : listFragment();
  }

  @POST
  @Path("tag/{id}/move")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response moveTag(
      @PathParam("id") int id,
      @FormParam("direction") String direction,
      @HeaderParam("HX-Request") @DefaultValue("") String hxRequest) {
    dao.moveTag(
        id, "up".equalsIgnoreCase(direction) ? MapTagDao.Direction.UP : MapTagDao.Direction.DOWN);
    return hxRequest.isBlank() ? redirectHome() : listFragment();
  }

  private Response listFragment() {
    return fragment(Templates.tagsPage$tagList(dao.listTags(), csrfTokenProvider.token()));
  }

  private static Response fragment(TemplateInstance fragment) {
    return Response.ok(fragment).type(MediaType.TEXT_HTML).build();
  }

  private MapTagRow findTag(int id) {
    return dao.listTags().stream()
        .filter(t -> t.id() == id)
        .findFirst()
        .orElseThrow(NotFoundException::new);
  }

  private static Response redirectHome() {
    return Response.seeOther(SELF).build();
  }
}
