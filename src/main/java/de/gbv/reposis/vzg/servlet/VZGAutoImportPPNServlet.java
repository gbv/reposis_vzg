package de.gbv.reposis.vzg.servlet;


import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.xml.transform.TransformerException;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.jdom2.Document;
import org.jdom2.Element;
import org.mycore.access.MCRAccessManager;
import org.mycore.common.MCRException;
import org.mycore.common.MCRSessionMgr;
import org.mycore.common.config.MCRConfiguration2;
import org.mycore.common.content.MCRJDOMContent;
import org.mycore.common.xml.MCRLayoutService;
import org.mycore.common.xml.MCRURIResolver;
import org.mycore.datamodel.metadata.MCRMetadataManager;
import org.mycore.datamodel.metadata.MCRObject;
import org.mycore.datamodel.metadata.MCRObjectID;
import org.mycore.frontend.MCRFrontendUtil;
import org.mycore.frontend.servlets.MCRServlet;
import org.mycore.frontend.servlets.MCRServletJob;
import org.mycore.mods.MCRMODSWrapper;
import org.mycore.solr.MCRSolrCoreManager;
import org.mycore.solr.MCRSolrUtils;
import org.mycore.solr.auth.MCRSolrAuthenticationLevel;
import org.mycore.solr.auth.MCRSolrAuthenticationManager;
import org.xml.sax.SAXException;

public class VZGAutoImportPPNServlet extends MCRServlet {


  public static final String PPN_IMPORT_OBJECT_SESSION_KEY = "PPN_IMPORT_OBJECT";

  @Override
  protected void doGetPost(MCRServletJob job) throws Exception {
    final var req = job.getRequest();
    String step = req.getParameter("step");

    if(!MCRAccessManager.checkPermission("create-mods") && !MCRAccessManager.checkPermission("create-mir_mods")){
      job.getResponse().sendError(403, "User does not have permission to create MODS objects");
      return;
    }

    if (step == null || step.isBlank()) {
      // send invalid request response
      job.getResponse().sendError(400, "Missing 'step' parameter");
      return;
    }

    switch (step) {
      case "enter_ppn" -> startImport(job);
      case "confirm_no_duplicate" -> confirmImport(job);
      default -> {
        job.getResponse().sendError(400, "Invalid 'step' parameter value");
      }
    }

  }

  private void startImport(MCRServletJob job)
      throws IOException, TransformerException, SAXException {
    var req = job.getRequest();
    var resp = job.getResponse();

    String ppn = req.getParameter("ppn");
    if (ppn == null || ppn.isBlank()) {
      // send invalid request response
      resp.sendError(400, "Missing 'ppn' parameter");
      return;
    }

    clearSessionObject();

    Element mods = MCRURIResolver.obtainInstance().resolve(
        "xslTransform:pica2mods:https://unapi.k10plus.de/?id=gvk:ppn:" + ppn + "&format=picaxml");

    String nameOfProject = MCRConfiguration2.getStringOrThrow("MCR.NameOfProject");
    MCRObject mcrObject = MCRMODSWrapper.wrapMODSDocument(mods, nameOfProject);
    MCRMODSWrapper modsWrapper = new MCRMODSWrapper(mcrObject);
    storeSessionObject(mcrObject);

    List<MCRObjectID> possibleDuplicatesId = searchForDuplicates(modsWrapper, ppn);

    Element confirmImport = new Element("confirmImport");
    confirmImport.setAttribute("ppn", ppn);

    Element importMods = new Element("importMods");
    confirmImport.addContent(importMods);
    Element clonedMods = mcrObject.createXML().detachRootElement();
    importMods.addContent(clonedMods);

    Element duplicates = new Element("possibleDuplicates");

    possibleDuplicatesId.stream().map(MCRMetadataManager::retrieveMCRObject)
        .map(MCRObject::createXML).map(Document::detachRootElement).forEach(duplicates::addContent);

    confirmImport.addContent(duplicates);

    MCRLayoutService.obtainInstance().doLayout(req, resp, new MCRJDOMContent(confirmImport));
  }

  private List<MCRObjectID> searchForDuplicates(MCRMODSWrapper modsWrapper, String ppn) {
    // extract title
    List<String> titles = modsWrapper.getElements("mods:titleInfo/mods:title").stream()
        .map(Element::getTextNormalize).toList();

    List<String> queryParts = titles.stream().map(MCRSolrUtils::escapeSearchValue)
        .map(t -> "mods.title:\"" + t + "\"").collect(Collectors.toList());

    queryParts.add("mods.identifier:" + MCRSolrUtils.escapeSearchValue(
        "https://uri.gbv.de/document/gvk:ppn:" + ppn));

    String solrQueryParam = String.join(" OR ", queryParts);

    SolrClient client = MCRSolrCoreManager.getMainSolrClient();

    ModifiableSolrParams params = new ModifiableSolrParams();
    params.add(CommonParams.Q, solrQueryParam);
    params.add(CommonParams.FL, "id");
    params.add(CommonParams.ROWS, "10");
    params.add(CommonParams.FQ, "objectType:mods");

    QueryRequest queryRequest = new QueryRequest(params);

    MCRSolrAuthenticationManager.obtainInstance()
        .applyAuthentication(queryRequest, MCRSolrAuthenticationLevel.SEARCH);

    try {
      var response = queryRequest.process(client);
      return response.getResults().stream().map(
              doco -> Optional.ofNullable(doco).map(doc -> doc.getFieldValue("id"))
                  .filter(String.class::isInstance).map(String.class::cast)).filter(Optional::isPresent)
          .map(Optional::get).filter(MCRObjectID::isValid).map(MCRObjectID::getInstance).toList();
    } catch (Exception e) {
      throw new RuntimeException("Error querying Solr for duplicates", e);
    }
  }

  private void clearSessionObject() {
    var sessionObj = (MCRObject) MCRSessionMgr.getCurrentSession()
        .get(PPN_IMPORT_OBJECT_SESSION_KEY);
    if (sessionObj != null) {
      MCRSessionMgr.getCurrentSession().deleteObject(PPN_IMPORT_OBJECT_SESSION_KEY);
    }
  }

  private void storeSessionObject(MCRObject mcrObject) {
    MCRSessionMgr.getCurrentSession().put(PPN_IMPORT_OBJECT_SESSION_KEY, mcrObject);
  }

  private MCRObject getSessionObject() {
    return (MCRObject) MCRSessionMgr.getCurrentSession().get(PPN_IMPORT_OBJECT_SESSION_KEY);
  }


  private void confirmImport(MCRServletJob job) {
    var req = job.getRequest();
    var resp = job.getResponse();

    var obj = getSessionObject();
    if (obj == null) {
      try {
        resp.sendError(400, "No import object in session. Please start import again.");
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      return;
    }

    try {
      MCRMetadataManager.create(obj);
    } catch (Exception e) {
      throw new RuntimeException("Error storing imported object", e);
    }

    try {
      resp.sendRedirect(MCRFrontendUtil.getBaseURL() + "receive/" + obj.getId().toString());
    } catch (IOException e) {
      throw new MCRException(e);
    }
  }


}
