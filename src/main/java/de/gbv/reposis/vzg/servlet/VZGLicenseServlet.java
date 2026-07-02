package de.gbv.reposis.vzg.servlet;

import java.util.Map;
import java.util.Optional;

import org.jdom2.Element;
import org.mycore.access.MCRAccessManager;
import org.mycore.common.MCRConstants;
import org.mycore.datamodel.classifications2.MCRCategoryDAO;
import org.mycore.datamodel.classifications2.MCRCategoryDAOFactory;
import org.mycore.datamodel.classifications2.MCRCategoryID;
import org.mycore.datamodel.metadata.MCRMetadataManager;
import org.mycore.datamodel.metadata.MCRObject;
import org.mycore.datamodel.metadata.MCRObjectID;
import org.mycore.frontend.MCRFrontendUtil;
import org.mycore.frontend.servlets.MCRServlet;
import org.mycore.frontend.servlets.MCRServletJob;
import org.mycore.mods.MCRMODSWrapper;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Sets the "use and reproduction" access condition (license) of a MODS object, either creating
 * or updating it, and points it at a category of the {@code mir_licenses} classification. Used by
 * the license dropdown in the workflow box, mirrors the permission check and redirect behaviour of
 * {@code MIRStateServlet}.
 */
public class VZGLicenseServlet extends MCRServlet {

    private static final String LICENSE_CLASSIFICATION_ID = "mir_licenses";

    @Override
    protected void doGetPost(MCRServletJob job) throws Exception {
        final String id = job.getRequest().getParameter("id");
        final String license = job.getRequest().getParameter("license");

        if (id == null) {
            job.getResponse().sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing parameter id");
            return;
        }
        if (license == null || license.isBlank()) {
            job.getResponse().sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing parameter license");
            return;
        }

        final MCRObjectID objectID = MCRObjectID.getInstance(id);
        final boolean read = MCRAccessManager.checkPermission(objectID, MCRAccessManager.PERMISSION_READ);
        final boolean write = MCRAccessManager.checkPermission(objectID, MCRAccessManager.PERMISSION_WRITE);
        if (!read || !write) {
            job.getResponse().sendError(HttpServletResponse.SC_FORBIDDEN, "No permission to change!");
            return;
        }

        final MCRCategoryDAO categoryDAO = MCRCategoryDAOFactory.obtainInstance();
        final MCRCategoryID licenseID = new MCRCategoryID(LICENSE_CLASSIFICATION_ID, license);
        if (!categoryDAO.exist(licenseID)) {
            job.getResponse().sendError(HttpServletResponse.SC_BAD_REQUEST, licenseID + " doesnt exist");
            return;
        }

        final MCRCategoryID rootID = new MCRCategoryID(LICENSE_CLASSIFICATION_ID);
        final Optional<String> licensesURI = categoryDAO.getCategory(rootID, 0)
            .getLabel("x-uri")
            .map(label -> label.getText());
        if (licensesURI.isEmpty()) {
            job.getResponse().sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                rootID + " has no x-uri label");
            return;
        }

        final MCRObject object = MCRMetadataManager.retrieveMCRObject(objectID);
        final MCRMODSWrapper modsWrapper = new MCRMODSWrapper(object);
        final Element accessCondition = modsWrapper
            .setElement("accessCondition", null, Map.of("type", "use and reproduction"))
            .orElseThrow();
        accessCondition.setAttribute("href", licensesURI.get() + "#" + license, MCRConstants.XLINK_NAMESPACE);
        accessCondition.setAttribute("type", "simple", MCRConstants.XLINK_NAMESPACE);

        MCRMetadataManager.update(object);
        job.getResponse()
            .sendRedirect(MCRFrontendUtil.getBaseURL(job.getRequest()) + "receive/" + objectID.toString());
    }

}
