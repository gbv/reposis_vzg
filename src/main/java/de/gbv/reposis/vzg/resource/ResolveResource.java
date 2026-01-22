package de.gbv.reposis.vzg.resource;

import de.gbv.reposis.vzg.service.ResolveService;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import org.jdom2.Document;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.mycore.common.MCRException;
import org.mycore.datamodel.metadata.MCRMetadataManager;
import org.mycore.datamodel.metadata.MCRObject;
import org.mycore.datamodel.metadata.MCRObjectID;
import org.mycore.frontend.MCRFrontendUtil;
import org.mycore.solr.MCRSolrCoreManager;
import org.mycore.solr.auth.MCRSolrAuthenticationManager;

@Path("resolve/")
public class ResolveResource {

  public ResolveResource() {
    resolveService = new ResolveService(MCRSolrCoreManager.getMainSolrCore(),
        MCRSolrAuthenticationManager.obtainInstance());
  }

  private final ResolveService resolveService;

  @Path("urn/{urn}")
  @GET
  public Response resolveURN(@PathParam("urn") String urn) {
    MCRObjectID objectID = resolveService.resolveMyCoReIdByUrn(urn);
    if (objectID == null) {
      return Response.status(Response.Status.NOT_FOUND).entity("No object found for URN: " + urn)
          .type(MediaType.TEXT_PLAIN_TYPE).build();
    } else {
      URI redirectURL = getRedirectURL(objectID.toString());
      return Response.temporaryRedirect(redirectURL).build();
    }
  }

  @Path("urn/{urn}/metadata")
  @GET
  public Response resolveURNXML(@PathParam("urn") String urn) {
    MCRObject object = resolveService.resolveMyCoreObjectByUrn(urn);
    if (object == null) {
      return Response.status(Response.Status.NOT_FOUND).entity("No object found for URN: " + urn)
          .type(MediaType.TEXT_PLAIN_TYPE).build();
    } else {
      Document objectXML = object.createXML();
      String xmlAsString = new XMLOutputter(Format.getPrettyFormat()).outputString(objectXML);
      return Response.ok(xmlAsString).type(MediaType.TEXT_XML_TYPE).build();
    }
  }

  @Path("urn/{urn}/content")
  @GET
  public Response resolveURNContent(@PathParam("urn") String urn) {
    java.nio.file.Path path = resolveService.resolveContentFilePathByUrn(urn);
    if (path == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity("No content file found for URN: " + urn).type(MediaType.TEXT_PLAIN_TYPE).build();
    } else {
      return streamPath(path);
    }
  }

  @Path("id/{id}")
  @GET
  public Response resolveID(@PathParam("id") String id) {
    if (!MCRObjectID.isValid(id) || !MCRMetadataManager.exists(MCRObjectID.getInstance(id))) {
      return Response.status(Response.Status.NOT_FOUND).entity("No object found for ID: " + id)
          .type(MediaType.TEXT_PLAIN_TYPE).build();

    } else {
      URI redirectURL = getRedirectURL(id);
      return Response.temporaryRedirect(redirectURL).build();
    }
  }

  private static URI getRedirectURL(String id) {
    URI redirectURL = null;

    try {
      redirectURL = new URI(MCRFrontendUtil.getBaseURL() + "receive/" + id);
    } catch (URISyntaxException e) {
      throw new MCRException(e);
    }
    return redirectURL;
  }

  @Path("id/{id}/metadata")
  @GET
  public Response resolveIDXML(@PathParam("id") String id) {
    if (!MCRObjectID.isValid(id)) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Invalid MyCoRe Object ID: " + id)
          .type(MediaType.TEXT_PLAIN_TYPE).build();
    }
    MCRObjectID mcrObjectID = MCRObjectID.getInstance(id);
    MCRObject object = resolveService.resolveMyCoReObjectByMCRObjectId(mcrObjectID);
    if (object == null) {
      return Response.status(Response.Status.NOT_FOUND).entity("No object found for ID: " + id)
          .type(MediaType.TEXT_PLAIN_TYPE).build();
    } else {
      Document objectXML = object.createXML();
      String xmlAsString = new XMLOutputter(Format.getPrettyFormat()).outputString(objectXML);
      return Response.ok(xmlAsString).type(MediaType.TEXT_XML_TYPE).build();
    }
  }

  @Path("id/{id}/content")
  @GET
  public Response resolveIDContent(@PathParam("id") String id) {
    if (!MCRObjectID.isValid(id)) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Invalid MyCoRe Object ID: " + id)
          .type(MediaType.TEXT_PLAIN_TYPE).build();
    }
    MCRObjectID mcrObjectID = MCRObjectID.getInstance(id);
    java.nio.file.Path path = resolveService.resolveContentFilePathByMCRObjectId(mcrObjectID);
    if (path == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity("No content file found for ID: " + id).type(MediaType.TEXT_PLAIN_TYPE).build();
    } else {
      return streamPath(path);
    }
  }

  private static Response streamPath(java.nio.file.Path path) {
    long fileSize;
    try {
      fileSize = Files.size(path);
    } catch (IOException e) {
      throw new MCRException(e);
    }

    String contentType;
    try {
      contentType = Files.probeContentType(path);
    } catch (IOException e) {
      throw new MCRException(e);
    }

    if (contentType == null) {
      contentType = MediaType.APPLICATION_OCTET_STREAM;
    }
    // use streaming instead of file entity to avoid loading the whole file into memory
    StreamingOutput stream = os -> {
      try (java.io.InputStream in = Files.newInputStream(path)) {
        in.transferTo(os);
      }
    };

    String fileName = path.getFileName().toString();

    return Response.ok(stream).type(contentType).header("Content-Length", fileSize)
        .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"").build();
  }

}
