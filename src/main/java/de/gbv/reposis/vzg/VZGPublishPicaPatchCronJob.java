package de.gbv.reposis.vzg;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.jdom2.Element;
import org.mycore.access.MCRAccessException;
import org.mycore.common.MCRException;
import org.mycore.common.MCRSystemUserInformation;
import org.mycore.common.config.annotation.MCRProperty;
import org.mycore.datamodel.classifications2.MCRCategoryID;
import org.mycore.datamodel.metadata.MCRMetaEnrichedLinkID;
import org.mycore.datamodel.metadata.MCRMetadataManager;
import org.mycore.datamodel.metadata.MCRObject;
import org.mycore.datamodel.metadata.MCRObjectID;
import org.mycore.frontend.MCRFrontendUtil;
import org.mycore.mcr.cronjob.MCRCronjob;
import org.mycore.mods.MCRMODSWrapper;
import org.mycore.solr.MCRSolrCoreManager;
import org.mycore.solr.auth.MCRSolrAuthenticationLevel;
import org.mycore.solr.auth.MCRSolrAuthenticationManager;
import org.mycore.util.concurrent.MCRFixedUserRunnable;

public class VZGPublishPicaPatchCronJob extends MCRCronjob {

  private static final Logger LOGGER = LogManager.getLogger();

  public static final String FULLTEXT_PLACEHOLDER = "%FULLTEXT%";
  public static final String URN_PLACEHOLDER = "%URN%";
  public static final String PPN_PLACEHOLDER = "%PPN%";

  private static final String TEMPLATE =
      " 003@ $0" + PPN_PLACEHOLDER + "\n" + "+ 004U $0" + URN_PLACEHOLDER + "\n" + "+ 017C $u"
          + FULLTEXT_PLACEHOLDER + "$xD$3Volltext$4LF$534$ADE-Ha91\n";


  public static final String PICA_PATCH_PUBLISHED_SERVFLAG_TYPE = "picaPatchPublished";


  private String shareToken;
  private String nextcloudURL;
  private Set<MCRCategoryID> publishableDerivateCategories;

  @Override
  public void runJob() {
    new MCRFixedUserRunnable(() -> {
      /*
       * find all published documents with at least one Derivate and an urn and without VZG PICA+
       * patch flag
       */
      List<MCRObjectID> publishableDocuments = findPublishableDocuments();
      List<MCRObjectID> toBePublishedDocuments = new ArrayList<>();
      StringBuilder picaPatchBuilder = new StringBuilder();
      for (MCRObjectID docId : publishableDocuments) {
        MCRObject mcrObject = MCRMetadataManager.retrieveMCRObject(docId);
        if (this.checkIfPublishable(mcrObject)) {
          String picaPatchEntry = convertToPicaPatchEntry(mcrObject);
          toBePublishedDocuments.add(docId);
          picaPatchBuilder.append(picaPatchEntry);
        } else {
          LOGGER.warn(
              "[Safeguard] Document {} is not publishable. Skipping PICA+ patch publishing.",
              docId);
        }
      }

      boolean success = publishPatchFile(picaPatchBuilder.toString());

      if (success) {
        // set servflag for published documents
        for (MCRObjectID docId : toBePublishedDocuments) {
          LOGGER.info("Set {} servflag to true for document {}.",
              PICA_PATCH_PUBLISHED_SERVFLAG_TYPE, docId);
          MCRObject mcrObject = MCRMetadataManager.retrieveMCRObject(docId);
          mcrObject.getService().addFlag(PICA_PATCH_PUBLISHED_SERVFLAG_TYPE, "true");
          try {
            MCRMetadataManager.update(mcrObject);
          } catch (MCRAccessException e) {
            throw new MCRException(e);
          }
        }
      }

    }, MCRSystemUserInformation.JANITOR).run();
  }

  /**
   * Publish the PICA+ patch file to the VZG Nextcloud
   *
   * @param picaPatchFile the PICA+ patch file content
   * @return true if successful, false otherwise
   */
  protected boolean publishPatchFile(String picaPatchFile) {
    if (picaPatchFile == null || picaPatchFile.isBlank()) {
      LOGGER.info("No PICA+ patch content to publish.");
      return true;
    }

    String fileName = "pica_patch_" + System.currentTimeMillis() + ".txt";

    // Nextcloud Public WebDAV Upload URL
    // Format: https://nextcloud.example.com/public.php/dav/files/{share_token}/{filename}
    String baseUrl = this.nextcloudURL;
    if (baseUrl.endsWith("/")) {
      baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
    }
    String uploadUrl = baseUrl + "/public.php/dav/files/" + this.shareToken + "/" + fileName;

    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
      HttpPut httpPut = new HttpPut(uploadUrl);

      /*
      String auth = "anonymous:";
      String encodedAuth = java.util.Base64.getEncoder()
          .encodeToString(auth.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      httpPut.setHeader("Authorization", "Basic " + encodedAuth);
      */

      httpPut.setEntity(new StringEntity(picaPatchFile,
          ContentType.TEXT_PLAIN.withCharset(java.nio.charset.StandardCharsets.UTF_8)));

      return httpClient.execute(httpPut, response -> {
        int statusCode = response.getCode();

        // WebDAV PUT gibt 201 Created oder 204 No Content zurück
        if (statusCode == 201 || statusCode == 204) {
          LOGGER.info("Successfully published PICA+ patch file to Nextcloud: {}", fileName);
          return true;
        } else {
          LOGGER.error("Failed to publish PICA+ patch file. HTTP Status: {}", statusCode);
          return false;
        }
      });
    } catch (IOException e) {
      LOGGER.error("Error publishing PICA+ patch file to Nextcloud", e);
      return false;
    }
  }

  /**
   * Check if a document is publishable to the Catalogue. It's a save guard method to double-check
   * the publishable ids retrieved by the Solr query.
   *
   * @param obj the mycore object to check
   * @return true if publishable, false otherwise
   */
  protected boolean checkIfPublishable(MCRObject obj) {
    MCRMODSWrapper modsObject = new MCRMODSWrapper(obj);

    // check if it has urn identifier
    if (!this.hasUrnIdentifier(modsObject)) {
      LOGGER.warn("[Safeguard] Document {} has no URN identifier.", obj.getId());
      return false;
    }

    if (!this.hasPPNIdentifier(modsObject)) {
      LOGGER.warn("[Safeguard] Document {} has no PPN identifier.", obj.getId());
      return false;
    }

    // check if picaPatchPublished servflag is false
    if (this.checkPicaPatchPublishedServflag(obj)) {
      LOGGER.warn("[Safeguard] Document {} has {} servflag set to true.", obj.getId(),
          PICA_PATCH_PUBLISHED_SERVFLAG_TYPE);
      return false;
    }

    // check if it has at least one derivate
    List<MCRMetaEnrichedLinkID> derivates = obj.getStructure().getDerivates();
    boolean hasDerivate = derivates.stream().anyMatch(this::isPublishableDerivate);
    if (!hasDerivate) {
      LOGGER.warn("[Safeguard] Document {} has no publishable derivate.", obj.getId());
    }
    return hasDerivate;
  }

  /**
   * Check if a derivate contains a file which is publishable to the Catalogue
   *
   * @param derLink the derivate link to check
   * @return true if publishable, false otherwise
   */
  protected boolean isPublishableDerivate(MCRMetaEnrichedLinkID derLink) {
    if (derLink.getMainDoc() == null) {
      return false;
    }
    Set<MCRCategoryID> categories = this.getPublishableDerivateCategories();
    return derLink.getClassifications().stream().anyMatch(categories::contains);
  }

  /**
   * Check if the MODS object has an urn identifier
   *
   * @param modsObj the MODS object to check
   * @return true if it has an urn identifier, false otherwise
   */
  protected boolean hasUrnIdentifier(MCRMODSWrapper modsObj) {
    return !getURNS(modsObj).isEmpty();
  }

  protected boolean hasPPNIdentifier(MCRMODSWrapper modsObj) {
    return getPPN(modsObj).isPresent();
  }

  /**
   * Get all URN identifiers from the MODS object
   *
   * @param modsObj the MODS object
   * @return list of URN identifiers
   */
  protected List<String> getURNS(MCRMODSWrapper modsObj) {
    return modsObj.getElements("mods:identifier[@type='urn']").stream().map(Element::getTextTrim)
        .filter(Predicate.not(String::isBlank)).toList();
  }

  protected Optional<String> getPPN(MCRMODSWrapper modsObj) {
    return Optional.ofNullable(modsObj.getElement("mods:identifier[@type='uri']"))
        .map(Element::getTextNormalize).filter(uri -> uri.contains("gvk:ppn:"))
        .map(uri -> uri.substring(uri.indexOf("gvk:ppn:") + "gbk:ppn:".length()));
  }

  /**
   * Get the URL of the PDF derivate from the mycore object.
   *
   * @param modsWrapper the mods wrapper of the mycore object
   * @return the URL of the content derivate derivate
   */
  protected URL getPDFURL(MCRMODSWrapper modsWrapper) {
    try {
      List<String> urns = getURNS(modsWrapper);
      Optional<String> urn = urns.stream().findFirst();
      if (urn.isPresent()) {
        return new URI(
            MCRFrontendUtil.getBaseURL() + "rsc/resolve/urn/" + urn.get() + "/content").toURL();
      } else {
        String objId = modsWrapper.getMCRObject().getId().toString();
        return new URI(
            MCRFrontendUtil.getBaseURL() + "rsc/resolve/id/" + objId + "/content").toURL();
      }

    } catch (MalformedURLException | URISyntaxException e) {
      throw new MCRException(e);
    }
  }

  /**
   * Check if the picaPatchPublished servflag is set to true
   *
   * @param obj the mycore object to check
   * @return true if the flag is set to true, false otherwise
   */
  protected boolean checkPicaPatchPublishedServflag(MCRObject obj) {
    List<String> picaPatchPublished = obj.getService().getFlags(PICA_PATCH_PUBLISHED_SERVFLAG_TYPE);
    return picaPatchPublished != null && !picaPatchPublished.isEmpty() && Boolean.parseBoolean(
        picaPatchPublished.getFirst());
  }

  /**
   * Find all documents which are publishable to the Catalogue
   *
   * @return list of publishable document IDs
   */
  protected List<MCRObjectID> findPublishableDocuments() {
    SolrClient client = MCRSolrCoreManager.getMainSolrClient();

    SolrQuery query = getPublishableDocumentsQuery();
    QueryRequest queryRequest = new QueryRequest(query);

    MCRSolrAuthenticationManager.obtainInstance()
        .applyAuthentication(queryRequest, MCRSolrAuthenticationLevel.SEARCH);

    ArrayList<MCRObjectID> idList;
    try {
      QueryResponse response = queryRequest.process(client);
      SolrDocumentList results = response.getResults();
      idList = new ArrayList<>(results.size());

      LOGGER.info("Found {} documents to publish PICA+ patches for VZG.", results.size());

      results.forEach(result -> {
        String idStr = result.getFieldValue("id").toString();
        MCRObjectID objId = MCRObjectID.getInstance(idStr);
        idList.add(objId);
      });
    } catch (SolrServerException | IOException e) {
      throw new MCRException(e);
    }

    return idList;
  }

  /**
   * Get the Solr query to find all publishable documents
   * @return the Solr query
   */
  private SolrQuery getPublishableDocumentsQuery() {
    SolrQuery query = new SolrQuery();
    ModifiableSolrParams params = new ModifiableSolrParams();
    params.add(CommonParams.FQ, "derCount:[1 TO *]");
    params.add(CommonParams.FQ, "mods.identifier.type.urn:*");
    params.add(CommonParams.FQ, "mods.identifier.type.uri:*");
    params.add(CommonParams.Q, "objectType:mods");
    params.add(CommonParams.FL, "id");
    params.add(CommonParams.FQ, "*:* AND NOT(servflag.type." + PICA_PATCH_PUBLISHED_SERVFLAG_TYPE
        + ":true)");
    query.add(params);
    query.setRows(Integer.MAX_VALUE);
    return query;
  }

  /**
   * Convert a mycore object to a PICA+ patch entry
   *
   * @param obj the mycore object
   * @return the PICA+ patch entry as string
   */
  public String convertToPicaPatchEntry(MCRObject obj) {
    MCRMODSWrapper modsWrapper = new MCRMODSWrapper(obj);

    URL pdfurl = this.getPDFURL(modsWrapper);
    String firstUrn = this.getURNS(modsWrapper).stream().findFirst().orElseThrow();
    String ppn = this.getPPN(modsWrapper).orElseThrow();

    return TEMPLATE.replace(FULLTEXT_PLACEHOLDER, pdfurl.toString())
        .replace(URN_PLACEHOLDER, firstUrn).replace(PPN_PLACEHOLDER, ppn);
  }

  @Override
  public String getDescription() {
    return "Publishes PICA+ patches to the VZG";
  }


  @MCRProperty(name = "DerivateCategory")
  public void setPublishableDerivateCategories(String publishableDerivateCategories) {
    this.publishableDerivateCategories = new HashSet<>();
    if (publishableDerivateCategories != null) {
      String[] categories = publishableDerivateCategories.split(",");
      for (String category : categories) {
        this.publishableDerivateCategories.add(MCRCategoryID.ofString(category.trim()));
      }
    }
  }

  public Set<MCRCategoryID> getPublishableDerivateCategories() {
    return Collections.unmodifiableSet(publishableDerivateCategories);
  }


  public String getNextcloudURL() {
    return nextcloudURL;
  }

  @MCRProperty(name = "NextcloudURL")
  public void setNextcloudURL(String nextcloudURL) {
    this.nextcloudURL = nextcloudURL;
  }

  public String getShareToken() {
    return shareToken;
  }

  @MCRProperty(name = "NextcloudShareToken")
  public void setShareToken(String shareToken) {
    this.shareToken = shareToken;
  }
}
