<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:i18n="xalan://org.mycore.services.i18n.MCRTranslation"
                xmlns:mcrxml="xalan://org.mycore.common.xml.MCRXMLFunctions"
                xmlns:piUtil="xalan://org.mycore.pi.frontend.MCRIdentifierXSLUtils"
                xmlns:exslt="http://exslt.org/common"
                xmlns:mods="http://www.loc.gov/mods/v3"
                version="1.0" exclude-result-prefixes="i18n exslt mcrxml piUtil">

  <xsl:import href="xslImport:modsmeta:metadata/mir-workflow.xsl"/>
  <xsl:import href="xslImport:mirworkflow"/>
  <xsl:import href="mir-pdf-errorbox.xsl"/>
  <xsl:param name="MIR.Workflow.Box" select="'false'"/>
  <xsl:param name="MIR.Workflow.ReviewDerivateRequired" select="'true'"/>
  <xsl:param name="MIR.Sherpa.API.Key" select="''"/>
  <xsl:param name="CurrentUser"/>
  <xsl:param name="CurrentLang"/>
  <xsl:param name="WebApplicationBaseURL"/>
  <xsl:param name="ServletsBaseURL"/>

  <xsl:param name="MIR.Workflow.Debug" select="'false'"/>
  <xsl:param name="MIR.Workflow.PDFValidation" select="'false'"/>
  <xsl:key use="@id" name="rights" match="/mycoreobject/rights/right"/>
  <xsl:variable name="id" select="/mycoreobject/@ID"/>

  <xsl:include href="workflow-util.xsl"/>


  <xsl:template match="/">
    <xsl:if test="normalize-space($MIR.Workflow.Box)='true'">
      <div id="mir-workflow" class="col-sm-12">
        <xsl:variable name="statusClassification" select="document('classification:metadata:-1:children:state')"/>
        <xsl:variable name="currentStatus"
                      select="/mycoreobject/service/servstates/servstate[@classid='state']/@categid"/>
        <xsl:variable name="creator" select="/mycoreobject/service/servflags/servflag[@type='createdby']/text()"/>
        <!-- Write -->
        <xsl:choose>
          <xsl:when test="$currentStatus='submitted'">
            <xsl:choose>
              <xsl:when test="mcrxml:isCurrentUserInRole('editor') or mcrxml:isCurrentUserInRole('admin')">
                <xsl:apply-templates mode="editorSubmitted"/>
              </xsl:when>
              <xsl:when test="$CurrentUser=$creator">
                <xsl:apply-templates mode="creatorSubmitted"/>
              </xsl:when>
            </xsl:choose>
          </xsl:when>
          <xsl:when test="$currentStatus='review'">
            <xsl:choose>
              <xsl:when test="mcrxml:isCurrentUserInRole('editor') or mcrxml:isCurrentUserInRole('admin')">
                <xsl:apply-templates mode="editorReview"/>
              </xsl:when>
              <xsl:when test="$CurrentUser=$creator">
                <xsl:apply-templates mode="creatorReview"/>
              </xsl:when>
            </xsl:choose>
          </xsl:when>
        </xsl:choose>
      </div>
    </xsl:if>
    <xsl:apply-imports/>
  </xsl:template>


  <xsl:template match="mycoreobject" mode="creatorSubmitted">
    <xsl:if test="normalize-space($MIR.Workflow.Debug)='true'">
      <xsl:message>
        creatorSubmitted
        Nutzer Ersteller
        Dokument submitted
      </xsl:message>
    </xsl:if>

    <xsl:call-template name="submittedWorkflowBox">
      <xsl:with-param name="messageKey" select="'mir.workflow.creator.submitted'"/>
    </xsl:call-template>
  </xsl:template>


  <xsl:template match="mycoreobject" mode="editorSubmitted" priority="10">
    <xsl:if test="normalize-space($MIR.Workflow.Debug)='true'">
      <xsl:message>
        editorSubmitted
        Nutzer editor
        Dokument submitted
      </xsl:message>
    </xsl:if>

    <xsl:call-template name="submittedWorkflowBox">
      <xsl:with-param name="messageKey" select="'vzg.workflow.editor.submitted'"/>
    </xsl:call-template>
  </xsl:template>


  <xsl:template name="submittedWorkflowBox">
    <xsl:param name="messageKey"/>
    <xsl:if test="key('rights', @ID)/@write">
      <xsl:variable name="urnServices"
                    select="piUtil:getPIServiceInformation($id)[@type='dnbUrn' and @permission='true' and @inscribed='false']"/>
      <xsl:variable name="message">
        <p>
          <xsl:value-of select="i18n:translate($messageKey)"/>
          <ul>
            <xsl:if test="structure/derobjects/derobject/maindoc[normalize-space()]">
              <xsl:for-each select="$urnServices">
                <li>
                  <a href="#" data-type="{@type}" data-mycoreID="{$id}"
                     data-baseURL="{$WebApplicationBaseURL}" data-register-pi="{@id}">
                    <xsl:value-of select="i18n:translate(concat('component.pi.register.',@id))"/>
                  </a>
                </li>
              </xsl:for-each>
            </xsl:if>
            <xsl:apply-templates select="." mode="creatorSubmittedAdd" />
            <xsl:call-template name="licenseSelect"/>
            <xsl:choose>
              <xsl:when
                test="normalize-space($MIR.Workflow.ReviewDerivateRequired) = 'true' and not(structure/derobjects/derobject/maindoc[normalize-space()])">
                <li>
                  <a href="#">
                    <xsl:attribute name="onclick">document.querySelector('[data-upload-object]').scrollIntoView({behavior: 'smooth', block: 'center', inline: 'nearest'}); return false;</xsl:attribute>
                    <xsl:value-of select="i18n:translate('mir.workflow.creator.submitted.require.derivate')"/>
                  </a>
                </li>
              </xsl:when>
              <xsl:otherwise>
                <xsl:call-template name="listStatusChangeOptions">
                  <xsl:with-param name="class" select="''"/>
                </xsl:call-template>
              </xsl:otherwise>
            </xsl:choose>
            <xsl:if test="string-length($MIR.Sherpa.API.Key)&gt;0">
              <xsl:variable name="issn"
                            select="/mycoreobject/metadata/def.modsContainer/modsContainer/mods:mods/mods:relatedItem/mods:identifier[@type='issn']/text()"/>
              <xsl:if test="string-length($issn)&gt;0">
                <li data-sherpainfo-issn="{$issn}">
                  <xsl:value-of select="i18n:translate('mir.workflow.sherpa.loading')" /><span class="spinner-grow spinner-grow-sm" role="status"></span>
                </li>
              </xsl:if>
            </xsl:if>
            <xsl:if test="key('rights', @ID)/@delete">
              <li>
                <a href="{$ServletsBaseURL}object/delete?id={$id}" class="confirm_deletion text-danger"
                   data-text="{i18n:translate('mir.confirm.text')}">
                  <xsl:value-of select="i18n:translate('object.delObject')"/>
                </a>
              </li>
            </xsl:if>
          </ul>
        </p>
      </xsl:variable>
      <xsl:call-template name="buildLayout">
        <xsl:with-param name="content" select="exslt:node-set($message)"/>
        <xsl:with-param name="heading" select="''"/>
      </xsl:call-template>
      <xsl:if test="normalize-space($MIR.Workflow.PDFValidation)='true'">
        <xsl:apply-templates select="." mode="displayPdfError"/>
      </xsl:if>
      <!-- the action menu (mir-edit.xsl) renders this modal for admins only, so provide it here for other roles -->
      <xsl:if
        test="$urnServices and structure/derobjects/derobject/maindoc[normalize-space()] and not(mcrxml:isCurrentUserInRole('admin'))">
        <div class="modal fade" id="modal-pi" tabindex="-1" role="dialog" data-backdrop="static">
          <div class="modal-dialog">
            <div class="modal-content">
              <div class="modal-header">
                <h4 class="modal-title" data-i18n="component.pi.register."></h4>
              </div>
              <div class="modal-body">
                <div class="row">
                  <div class="col-md-2">
                    <i class="fas fa-question-circle"></i>
                  </div>
                  <div class="col-md-10" data-i18n="component.pi.register.modal.text."></div>
                </div>
              </div>
              <div class="modal-footer">
                <button type="button" class="btn btn-secondary modal-pi-cancel" data-bs-dismiss="modal">
                  <xsl:value-of select="i18n:translate('component.pi.register.modal.abort')" />
                </button>
                <button type="button" class="btn btn-danger" id="modal-pi-add"
                        data-i18n="component.pi.register.">
                </button>
              </div>
            </div>
          </div>
        </div>
      </xsl:if>
    </xsl:if>
  </xsl:template>


  <xsl:template match="mycoreobject" mode="creatorReview" priority="10">
    <xsl:if test="normalize-space($MIR.Workflow.Debug)='true'">
      <xsl:message>
        creatorReview
        Nutzer creator
        Dokument review
      </xsl:message>
    </xsl:if>
    <xsl:variable name="message">
      <p>
        <xsl:value-of select="i18n:translate('mir.workflow.creator.review')"/>
      </p>
    </xsl:variable>
    <xsl:call-template name="buildLayout">
      <xsl:with-param name="content" select="exslt:node-set($message)"/>
      <xsl:with-param name="heading" select="''"/>
    </xsl:call-template>
  </xsl:template>

  <xsl:template match="mycoreobject" mode="editorReview" priority="10">
    <xsl:if test="normalize-space($MIR.Workflow.Debug)='true'">
      <xsl:message>
        editorReview
        Nutzer editor
        Dokument review
      </xsl:message>
    </xsl:if>
    <xsl:variable name="message">
      <p>
        <xsl:value-of select="i18n:translate('mir.workflow.editor.review')" />
        <ul>
          <xsl:apply-templates select="." mode="editorReviewAdd" />
          <xsl:call-template name="licenseSelect"/>
          <xsl:call-template name="listStatusChangeOptions">
            <xsl:with-param name="class" select="''" />
          </xsl:call-template>
        </ul>
      </p>
    </xsl:variable>
    <xsl:call-template name="buildLayout">
      <xsl:with-param name="content" select="exslt:node-set($message)" />
      <xsl:with-param name="heading" select="''" />
    </xsl:call-template>
  </xsl:template>


  <xsl:template name="buildLayout" priority="10">
    <xsl:param name="heading"/>
    <xsl:param name="content"/>
    <div class="workflow-box">
      <xsl:if test="string-length(normalize-space($heading))&gt;0">
        <h1>
          <xsl:value-of select="$heading"/>
        </h1>
      </xsl:if>
      <xsl:copy-of select="$content"/>
    </div>
  </xsl:template>
</xsl:stylesheet>
