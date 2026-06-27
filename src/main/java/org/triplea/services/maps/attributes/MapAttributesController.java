package org.triplea.services.maps.attributes;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import org.jdbi.v3.core.Jdbi;
import org.triplea.services.auth.RequiresMember;

/**
 * Renders and edits the attribute catalog (the dimensions like "difficulty" and the allowed values
 * within each dimension). Mutations are HTML form posts that 303-redirect back to the GET page so
 * the browser history remains a series of GETs (post-redirect-get).
 *
 * <p>Fully gated: {@link RequiresMember} on the class makes the GET render and every POST
 * members-only, enforced server-side by {@code MemberAuthFilter}.
 */
@Path("/support/admin/map/attributes")
@ApplicationScoped
@RequiresMember
public class MapAttributesController {

  private static final URI SELF = URI.create("/support/admin/map/attributes");

  @Inject Jdbi jdbi;

  private MapAttributeDao dao;

  @PostConstruct
  void init() {
    dao = new MapAttributeDao(jdbi);
  }

  @CheckedTemplate
  public static class Templates {
    public static native TemplateInstance catalogPage(List<AttributeWithValues> attributes);
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance catalogPage() {
    return Templates.catalogPage(dao.listAttributes());
  }

  @POST
  @Path("/attribute")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response createAttribute(@FormParam("name") String name) {
    dao.createAttribute(name.trim());
    return redirectHome();
  }

  @POST
  @Path("/attribute/{id}/rename")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response renameAttribute(@PathParam("id") int id, @FormParam("name") String name) {
    dao.renameAttribute(id, name.trim());
    return redirectHome();
  }

  @POST
  @Path("/attribute/{id}/delete")
  public Response deleteAttribute(@PathParam("id") int id) {
    dao.deleteAttribute(id);
    return redirectHome();
  }

  @POST
  @Path("/attribute/{id}/move")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response moveAttribute(@PathParam("id") int id, @FormParam("direction") String direction) {
    dao.moveAttribute(id, parseDirection(direction));
    return redirectHome();
  }

  @POST
  @Path("/attribute/{id}/value")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response createValue(@PathParam("id") int id, @FormParam("value") String value) {
    dao.createValue(id, value.trim());
    return redirectHome();
  }

  @POST
  @Path("/value/{id}/rename")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response renameValue(@PathParam("id") int id, @FormParam("value") String value) {
    dao.renameValue(id, value.trim());
    return redirectHome();
  }

  @POST
  @Path("/value/{id}/delete")
  public Response deleteValue(@PathParam("id") int id) {
    dao.deleteValue(id);
    return redirectHome();
  }

  @POST
  @Path("/value/{id}/move")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response moveValue(@PathParam("id") int id, @FormParam("direction") String direction) {
    dao.moveValue(id, parseDirection(direction));
    return redirectHome();
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
