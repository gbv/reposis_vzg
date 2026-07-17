<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:i18n="xalan://org.mycore.services.i18n.MCRTranslation"
                xmlns:mcrxml="xalan://org.mycore.common.xml.MCRXMLFunctions"
                xmlns:mods="http://www.loc.gov/mods/v3"
                xmlns:xlink="http://www.w3.org/1999/xlink"
                xmlns:exslt="http://exslt.org/common"
                version="1.0" exclude-result-prefixes="i18n exslt mcrxml mods xlink">

  <!-- VZG: overrides the MIR version, the transition to 'review' additionally requires an
       assigned license, the transition to 'published' additionally requires the editor or admin
       role, an uploaded main document, an assigned URN and an assigned license -->
  <xsl:template name="listStatusChangeOptions">
    <xsl:param name="class"/>

    <xsl:variable name="statusClassification" select="document('classification:metadata:-1:children:state')"/>
    <xsl:variable name="currentStatus" select="/mycoreobject/service/servstates/servstate[@classid='state']/@categid"/>
    <xsl:variable name="allowedNext"
                  select="normalize-space($statusClassification//category[@ID=$currentStatus]/label[@xml:lang='x-next']/@text)"/>
    <xsl:if test="string-length($allowedNext)&gt;0 and $allowedNext!='null'">
      <xsl:variable name="token">
        <xsl:call-template name="Tokenizer">
          <xsl:with-param name="string" select="$allowedNext"/>
          <xsl:with-param name="delimiter" select="','"/>
        </xsl:call-template>
      </xsl:variable>
      <xsl:variable name="id" select="/mycoreobject/@ID"/>
      <xsl:variable name="hasMainDoc"
                    select="boolean(/mycoreobject/structure/derobjects/derobject/maindoc[normalize-space()])"/>
      <xsl:variable name="hasURN"
                    select="boolean(/mycoreobject/metadata/def.modsContainer/modsContainer/mods:mods/mods:identifier[@type='urn'])"/>
      <xsl:variable name="hasLicense"
                    select="boolean(/mycoreobject/metadata/def.modsContainer/modsContainer/mods:mods
                            /mods:accessCondition[@type='use and reproduction']/@xlink:href[normalize-space()])"/>
      <xsl:variable name="mayReview" select="$hasLicense"/>
      <xsl:variable name="mayPublish"
                    select="(mcrxml:isCurrentUserInRole('editor') or mcrxml:isCurrentUserInRole('admin'))
                            and $hasMainDoc and $hasURN and $hasLicense"/>

      <xsl:for-each select="exslt:node-set($token)/token">
        <xsl:variable name="newStatus" select="text()"/>
        <xsl:if test="($newStatus!='published' and $newStatus!='review')
                      or ($newStatus='published' and $mayPublish) or ($newStatus='review' and $mayReview)">
          <li>
            <a class="{$class}" href="{$ServletsBaseURL}MIRStateServlet?newState={$newStatus}&amp;id={$id}"
               title="{i18n:translate(concat('mir.workflow.state.', $currentStatus, '2', $newStatus,'.message'))}">
              <xsl:value-of select="i18n:translate(concat('mir.workflow.state.', $currentStatus, '2', $newStatus))"/>
            </a>
          </li>
        </xsl:if>
      </xsl:for-each>
    </xsl:if>
  </xsl:template>

  <!-- renders a <select> that posts the chosen mir_licenses category to VZGLicenseServlet;
       each option carries the license description as a tooltip, and once a license is assigned
       a link to its official text (from the classification's url element) is shown -->
  <xsl:template name="licenseSelect">
    <xsl:variable name="id" select="/mycoreobject/@ID"/>
    <xsl:variable name="licenseClassification" select="document('classification:metadata:-1:children:mir_licenses')"/>
    <xsl:variable name="currentHref"
                  select="/mycoreobject/metadata/def.modsContainer/modsContainer/mods:mods
                          /mods:accessCondition[@type='use and reproduction']/@xlink:href"/>
    <xsl:variable name="currentLicense" select="substring-after($currentHref, '#')"/>
    <xsl:variable name="currentCategory" select="$licenseClassification//category[@ID=$currentLicense]"/>
    <li>
      <form method="post" action="{$ServletsBaseURL}VZGLicenseServlet"
            class="vzg-license-select d-flex align-items-center flex-wrap gap-2">
        <input type="hidden" name="id" value="{$id}"/>
        <label for="vzg-license-{$id}" class="form-label mb-0">
          <xsl:value-of select="i18n:translate('vzg.workflow.license.label')"/>
        </label>
        <select name="license" id="vzg-license-{$id}" class="form-control form-select form-select-sm w-auto"
                onchange="if(this.value){{this.form.submit()}}">
          <option value="">
            <xsl:value-of select="i18n:translate('vzg.workflow.license.choose')"/>
          </option>
          <xsl:for-each select="$licenseClassification/mycoreclass/categories/category">
            <xsl:choose>
              <xsl:when test="label[@xml:lang='x-group']/@text='true'">
                <optgroup label="{(label[@xml:lang=$CurrentLang]/@text|label[@xml:lang='de']/@text)[1]}">
                  <xsl:call-template name="licenseOptions">
                    <xsl:with-param name="categories" select="category"/>
                    <xsl:with-param name="currentLicense" select="$currentLicense"/>
                  </xsl:call-template>
                </optgroup>
              </xsl:when>
              <xsl:otherwise>
                <xsl:call-template name="licenseOptions">
                  <xsl:with-param name="categories" select="."/>
                  <xsl:with-param name="currentLicense" select="$currentLicense"/>
                </xsl:call-template>
              </xsl:otherwise>
            </xsl:choose>
          </xsl:for-each>
        </select>
        <xsl:if test="$currentCategory/url/@xlink:href">
          <a href="{$currentCategory/url/@xlink:href}" target="_blank" rel="noopener" class="small">
            <i class="fas fa-external-link-alt me-1"></i>
            <xsl:value-of select="i18n:translate('vzg.workflow.license.view')"/>
          </a>
        </xsl:if>
      </form>
    </li>
  </xsl:template>

  <xsl:template name="licenseOptions">
    <xsl:param name="categories"/>
    <xsl:param name="currentLicense"/>
    <xsl:for-each select="$categories">
      <xsl:variable name="description"
                    select="(label[@xml:lang=$CurrentLang]/@description|label[@xml:lang='de']/@description)[1]"/>
      <option value="{@ID}">
        <xsl:if test="@ID=$currentLicense">
          <xsl:attribute name="selected">selected</xsl:attribute>
        </xsl:if>
        <xsl:if test="string-length($description)&gt;0">
          <xsl:attribute name="title"><xsl:value-of select="$description"/></xsl:attribute>
        </xsl:if>
        <xsl:value-of select="(label[@xml:lang=$CurrentLang]/@text|label[@xml:lang='de']/@text)[1]"/>
      </option>
    </xsl:for-each>
  </xsl:template>

</xsl:stylesheet>
