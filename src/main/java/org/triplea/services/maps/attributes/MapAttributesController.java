package org.triplea.services.maps.attributes;

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

/// Renders and edits the attribute catalog (the dimensions like "difficulty" and the allowed values
/// within each dimension).
///
/// Each mutating endpoint serves two clients from one method, keyed off the `HX-Request` header
/// HTMX sets on its requests:
///   - **HTMX** -> `200` with the smallest correct re-rendered fragment (a value row, a section,
///     or the whole list), swapped into the page in place.
///   - **No JavaScript** -> the classic POST-redirect-GET (`303` back to [#SELF]).
@Path("/support/admin/map/attributes")
@ApplicationScoped
@RequiresMapAdmin
@CsrfProtected
public class MapAttributesController {

  private static final URI SELF = URI.create("/support/admin/map/attributes");

  @Inject Jdbi jdbi;
  @Inject CsrfTokenProvider csrfTokenProvider;
  @Inject RequestIdentity requestIdentity;

  private MapAttributeDao dao;

  @PostConstruct
  void init() {
    dao = new MapAttributeDao(jdbi);
  }

  @CheckedTemplate
  public static class Templates {
    public static native TemplateInstance catalogPage(
        List<AttributeWithValues> attributes, String csrfToken, Identity identity);

    /// Whole attribute list (incl. its `#attribute-list` wrapper and empty-state) — the swap unit
    /// for add / delete / move attribute.
    public static native TemplateInstance catalogPage$attributeList(
        List<AttributeWithValues> attributes, String csrfToken);

    /// One attribute's `<section>` (incl. its values and "no values" empty-state) — the swap unit
    /// for rename attribute and add / delete / move value.
    public static native TemplateInstance catalogPage$attributeSection(
        AttributeWithValues attr, String csrfToken);

    /// A single value's `<form>` row — the swap unit for rename value.
    public static native TemplateInstance catalogPage$valueRow(
        AttributeValueRow v, String csrfToken);
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance catalogPage() {
    return Templates.catalogPage(
        dao.listAttributes(), csrfTokenProvider.token(), requestIdentity.get());
  }

  @POST
  @Path("/attribute")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response createAttribute(
      @FormParam("name") String name,
      @HeaderParam("HX-Request") @DefaultValue("") String hxRequest) {
    dao.createAttribute(name.trim());
    return hxRequest.isBlank() ? redirectHome() : listFragment();
  }

  @POST
  @Path("/attribute/{id}/rename")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response renameAttribute(
      @PathParam("id") int id,
      @FormParam("name") String name,
      @HeaderParam("HX-Request") @DefaultValue("") String hxRequest) {
    dao.renameAttribute(id, name.trim());
    return hxRequest.isBlank() ? redirectHome() : sectionFragment(id);
  }

  @POST
  @Path("/attribute/{id}/delete")
  public Response deleteAttribute(
      @PathParam("id") int id, @HeaderParam("HX-Request") @DefaultValue("") String hxRequest) {
    dao.deleteAttribute(id);
    return hxRequest.isBlank() ? redirectHome() : listFragment();
  }

  @POST
  @Path("/attribute/{id}/move")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response moveAttribute(
      @PathParam("id") int id,
      @FormParam("direction") String direction,
      @HeaderParam("HX-Request") @DefaultValue("") String hxRequest) {
    dao.moveAttribute(id, parseDirection(direction));
    return hxRequest.isBlank() ? redirectHome() : listFragment();
  }

  @POST
  @Path("/attribute/{id}/value")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response createValue(
      @PathParam("id") int id,
      @FormParam("value") String value,
      @HeaderParam("HX-Request") @DefaultValue("") String hxRequest) {
    dao.createValue(id, value.trim());
    return hxRequest.isBlank() ? redirectHome() : sectionFragment(id);
  }

  @POST
  @Path("/value/{id}/rename")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response renameValue(
      @PathParam("id") int id,
      @FormParam("value") String value,
      @HeaderParam("HX-Request") @DefaultValue("") String hxRequest) {
    dao.renameValue(id, value.trim());
    return hxRequest.isBlank()
        ? redirectHome()
        : fragment(Templates.catalogPage$valueRow(findValue(id), csrfTokenProvider.token()));
  }

  @POST
  @Path("/value/{id}/delete")
  public Response deleteValue(
      @PathParam("id") int id, @HeaderParam("HX-Request") @DefaultValue("") String hxRequest) {
    if (hxRequest.isBlank()) {
      dao.deleteValue(id);
      return redirectHome();
    }
    // The value's parent must be resolved before the row is gone, to re-render its section.
    int attributeId = findValue(id).attributeId();
    dao.deleteValue(id);
    return sectionFragment(attributeId);
  }

  @POST
  @Path("/value/{id}/move")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response moveValue(
      @PathParam("id") int id,
      @FormParam("direction") String direction,
      @HeaderParam("HX-Request") @DefaultValue("") String hxRequest) {
    var parsedDirection = parseDirection(direction);
    if (hxRequest.isBlank()) {
      dao.moveValue(id, parsedDirection);
      return redirectHome();
    }
    int attributeId = findValue(id).attributeId();
    dao.moveValue(id, parsedDirection);
    return sectionFragment(attributeId);
  }

  private Response listFragment() {
    return fragment(
        Templates.catalogPage$attributeList(dao.listAttributes(), csrfTokenProvider.token()));
  }

  private Response sectionFragment(int attributeId) {
    return fragment(
        Templates.catalogPage$attributeSection(
            findAttribute(attributeId), csrfTokenProvider.token()));
  }

  private static Response fragment(TemplateInstance fragment) {
    return Response.ok(fragment).type(MediaType.TEXT_HTML).build();
  }

  /// Resolves one attribute (with its values) from the catalog read, for a section-level swap.
  private AttributeWithValues findAttribute(int id) {
    return dao.listAttributes().stream()
        .filter(a -> a.id() == id)
        .findFirst()
        .orElseThrow(NotFoundException::new);
  }

  /// Resolves one value from the catalog read, for a row-level swap or to find its parent section.
  private AttributeValueRow findValue(int id) {
    return dao.listAttributes().stream()
        .flatMap(a -> a.values().stream())
        .filter(v -> v.id() == id)
        .findFirst()
        .orElseThrow(NotFoundException::new);
  }

  private static MapAttributeDao.Direction parseDirection(String direction) {
    return "up".equalsIgnoreCase(direction)
        ? MapAttributeDao.Direction.UP
        : MapAttributeDao.Direction.DOWN;
  }

  private static Response redirectHome() {
    return Response.seeOther(SELF).build();
  }
}
