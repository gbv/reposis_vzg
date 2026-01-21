package de.gbv.reposis.vzg.service;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClientBase;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.mycore.common.MCRException;
import org.mycore.datamodel.classifications2.MCRCategoryID;
import org.mycore.datamodel.metadata.MCRMetaEnrichedLinkID;
import org.mycore.datamodel.metadata.MCRMetadataManager;
import org.mycore.datamodel.metadata.MCRObject;
import org.mycore.datamodel.metadata.MCRObjectID;
import org.mycore.datamodel.niofs.MCRPath;
import org.mycore.solr.MCRSolrCore;
import org.mycore.solr.auth.MCRSolrAuthenticationLevel;
import org.mycore.solr.auth.MCRSolrAuthenticationManager;

public record ResolveService(MCRSolrCore solrCore,
                             MCRSolrAuthenticationManager authenticationManager) {

  private static final Logger LOGGER = LogManager.getLogger();


  public MCRObject resolveMyCoreObjectByUrn(String urn) {
    MCRObjectID objectID =resolveMyCoReIdByUrn(urn);
    if(objectID == null) {
      return null;
    }
    return resolveMyCoReObjectByMCRObjectId(objectID);

  }

  public MCRObjectID resolveMyCoReIdByUrn(String urn) {
    HttpSolrClientBase client = solrCore().getClient();

    ModifiableSolrParams solrParams = new ModifiableSolrParams();
    solrParams.add(CommonParams.Q, "mods.identifier.type.urn:\"" + urn + "\"");
    solrParams.add(CommonParams.FL, "id");
    solrParams.add(CommonParams.ROWS, "1");

    QueryRequest queryRequest = new QueryRequest(solrParams);

    authenticationManager().applyAuthentication(queryRequest, MCRSolrAuthenticationLevel.SEARCH);
    try {
      QueryResponse resp = queryRequest.process(client);
      if (resp.getResults().isEmpty()) {
        return null;
      }

      String objectId = Optional.ofNullable(resp.getResults())
          .stream()
          .flatMap(Collection::stream)
          .findFirst()
          .map(result -> (String) result.get("id"))
          .orElse(null);

      if (objectId == null) {
        return null;
      }

      return MCRObjectID.getInstance(objectId);

    } catch (SolrServerException | IOException e) {
      throw new MCRException(e);
    }
  }

  public MCRObject resolveMyCoReObjectByMCRObjectId(MCRObjectID mycoreId) {
    if(!MCRMetadataManager.exists(mycoreId)){
      return null;
    }
    return MCRMetadataManager.retrieveMCRObject(mycoreId);
  }

  public Path resolveContentFilePathByUrn(String urn) {
    MCRObject mcrObject = resolveMyCoreObjectByUrn(urn);
    if (mcrObject == null) {
      return null;
    }

    MCRMetaEnrichedLinkID contentDerivate = findContentDerivate(mcrObject);

    if (contentDerivate == null) {
      return null;
    }

    MCRPath contentDerivatePath = MCRPath.getPath(contentDerivate.getXLinkHref(),
        contentDerivate.getMainDoc());

    if (!Files.exists(contentDerivatePath)) {
      LOGGER.warn("Content derivate path does not exist: {}", contentDerivatePath);
      return null;
    }

    return contentDerivatePath;
  }


  public Path resolveContentFilePathByMCRObjectId(MCRObjectID mycoreId) {
    MCRObject mcrObject = resolveMyCoReObjectByMCRObjectId(mycoreId);
    if (mcrObject == null) {
      return null;
    }

    MCRMetaEnrichedLinkID contentDerivate = findContentDerivate(mcrObject);

    if (contentDerivate == null) {
      return null;
    }

    MCRPath contentDerivatePath = MCRPath.getPath(contentDerivate.getXLinkHref(),
        contentDerivate.getMainDoc());

    if (!Files.exists(contentDerivatePath)) {
      LOGGER.warn("Content derivate path does not exist: {}", contentDerivatePath);
      return null;
    }

    return contentDerivatePath;
  }


  private MCRMetaEnrichedLinkID findContentDerivate(MCRObject mcrObject) {
    MCRCategoryID contentDerivateType = MCRCategoryID.ofString("derivate_types:content");
    return mcrObject.getStructure().getDerivates().stream()
        .filter(derivate -> derivate.getClassifications()
            .stream()
            .anyMatch(contentDerivateType::equals))
        .findFirst()
        .orElse(null);
  }
}
