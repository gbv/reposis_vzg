<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:mods="http://www.loc.gov/mods/v3"
  xmlns:xlink="http://www.w3.org/1999/xlink"
  xmlns:i18n="xalan://org.mycore.services.i18n.MCRTranslation"
  xmlns:iview2="xalan://org.mycore.iview2.services.MCRIView2Tools"
  xmlns:str="http://exslt.org/strings"
  exclude-result-prefixes="xlink mods i18n iview2 str">

  <xsl:include href="MyCoReLayout.xsl" />

  <xsl:variable name="PageTitle" select="i18n:translate('vzg.ppn.import.heading')" />

  <xsl:template match="/confirmImport">
    <div class="container my-2">
      <div class="row">
        <div class="col-12">
          <h1>
            <xsl:value-of select="i18n:translate('vzg.ppn.import.heading')" />
          </h1>
          <p class="text-muted">
            <xsl:value-of select="i18n:translate('vzg.ppn.import.ppn_label')" />
            <xsl:text>: </xsl:text>
            <strong><xsl:value-of select="@ppn" /></strong>
          </p>
        </div>
      </div>

      <!-- Zu importierendes Dokument -->
      <div class="row mt-2">
        <div class="col-12">
          <h2>
            <xsl:value-of select="i18n:translate('vzg.ppn.import.confirm_document')" />
          </h2>
          <xsl:for-each select="importMods//mods:mods">
            <div class="card mb-3">
              <div class="card-body">
                <!-- Title -->
                <h4 class="card-text card-title mb-1">
                  <xsl:if test="mods:titleInfo/mods:nonSort">
                    <xsl:value-of select="mods:titleInfo/mods:nonSort" />
                    <xsl:text> </xsl:text>
                  </xsl:if>
                  <xsl:value-of select="mods:titleInfo/mods:title" />
                  <xsl:if test="mods:titleInfo/mods:subTitle">
                    <xsl:text> : </xsl:text>
                    <xsl:value-of select="mods:titleInfo/mods:subTitle" />
                  </xsl:if>
                </h4>

                <!-- Author -->
                <xsl:if test="mods:name[@type='personal']">
                  <div class="card-text hit_author ps-0">
                    <xsl:for-each select="mods:name[@type='personal'][position() &lt;= 3]">
                      <xsl:if test="position() > 1">
                        <xsl:text> / </xsl:text>
                      </xsl:if>
                      <xsl:choose>
                        <xsl:when test="mods:namePart[@type='family'] and mods:namePart[@type='given']">
                          <xsl:value-of select="mods:namePart[@type='family']" />
                          <xsl:text>, </xsl:text>
                          <xsl:value-of select="mods:namePart[@type='given']" />
                        </xsl:when>
                        <xsl:when test="mods:displayForm">
                          <xsl:value-of select="mods:displayForm" />
                        </xsl:when>
                        <xsl:otherwise>
                          <xsl:value-of select="mods:namePart" />
                        </xsl:otherwise>
                      </xsl:choose>
                    </xsl:for-each>
                    <xsl:if test="count(mods:name[@type='personal']) > 3">
                      <xsl:text> / et.al.</xsl:text>
                    </xsl:if>
                  </div>
                </xsl:if>

                <!-- Publisher/Date -->
                <xsl:if test="mods:originInfo/mods:publisher">
                  <div class="card-text hit_pub_name ps-0">
                    <xsl:variable name="date" select="mods:originInfo/mods:dateIssued" />
                    <xsl:variable name="place" select="mods:originInfo/mods:place/mods:placeTerm" />
                    <xsl:if test="string-length($place) &gt; 0">
                      <xsl:value-of select="concat($place, ': ')" />
                    </xsl:if>
                    <xsl:value-of select="mods:originInfo/mods:publisher" />
                    <xsl:if test="string-length($date) &gt; 0">
                      <xsl:value-of select="concat(', ', $date)" />
                    </xsl:if>
                  </div>
                </xsl:if>

                <!-- Abstract -->
                <xsl:if test="mods:abstract">
                  <p class="card-text">
                    <xsl:value-of select="mods:abstract[1]" />
                  </p>
                </xsl:if>
              </div>
            </div>
          </xsl:for-each>
        </div>
      </div>

      <!-- Mögliche Duplikate -->
      <xsl:if test="possibleDuplicates//mods:mods">
        <div class="row mt-2">
          <div class="col-12">
            <h2>
              <xsl:value-of select="i18n:translate('vzg.ppn.import.confirm_possible_duplicate')" />
            </h2>
            <div class="alert alert-warning">
              <i class="fas fa-exclamation-triangle me-2"></i>
              <xsl:value-of select="i18n:translate('vzg.ppn.import.duplicate_warning')" />
            </div>
            <div class="result_body">
              <div class="result_list">
                <div id="hit_list">
                  <xsl:for-each select="possibleDuplicates/mycoreobject">
                    <xsl:variable name="objID" select="@ID" />
                    <xsl:variable name="hitItemClass">
                      <xsl:choose>
                        <xsl:when test="position() mod 2 = 1">odd</xsl:when>
                        <xsl:otherwise>even</xsl:otherwise>
                      </xsl:choose>
                    </xsl:variable>

                    <div id="hit_{position()}" class="hit_item {$hitItemClass}">
                      <!-- hit head -->
                      <div class="row hit_item_head">
                        <div class="col-12">
                          <!-- hit number -->
                          <div class="hit_counter">
                            <xsl:value-of select="position()" />
                          </div>
                        </div>
                      </div>

                      <!-- hit body -->
                      <div class="row hit_item_body">
                        <div class="col-12">
                          <xsl:for-each select=".//mods:mods">
                            <xsl:call-template name="showMiniDescription">
                              <xsl:with-param name="objectID" select="$objID" />
                            </xsl:call-template>
                          </xsl:for-each>
                        </div>
                      </div>
                    </div>
                  </xsl:for-each>
                </div>
              </div>
            </div>
          </div>
        </div>
      </xsl:if>

      <!-- Aktionen -->
      <div class="row mt-2">
        <div class="col-12">
          <form method="post" action="{$WebApplicationBaseURL}servlets/VZGAutoImportPPNServlet">
            <input type="hidden" name="step" value="confirm_no_duplicate" />
            <input type="hidden" name="ppn" value="{@ppn}" />
            <div class="d-flex gap-2 justify-content-end">
              <a href="javascript:history.back()" class="btn btn-secondary">
                <i class="fas fa-times me-2"></i>
                <xsl:value-of select="i18n:translate('vzg.ppn.import.button.cancel')" />
              </a>
              <button type="submit" class="btn btn-primary">
                <i class="fas fa-check me-2"></i>
                <xsl:value-of select="i18n:translate('vzg.ppn.import.button.confirm')" />
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  </xsl:template>

  <xsl:template name="showMiniDescription">
    <xsl:param name="objectID" select="''" />

    <!-- document preview / icon (only for duplicates with objectID) -->
    <xsl:if test="string-length($objectID) &gt; 0">
      <div class="hit_download_box">
        <!-- find content derivates -->
        <xsl:variable name="contentDerivates" select="ancestor::mycoreobject/structure/derobjects/derobject[classification[@classid='derivate_types'][@categid='content']]" />
        <xsl:variable name="derivateID" select="$contentDerivates[1]/@xlink:href" />
        <xsl:variable name="maindoc" select="$contentDerivates[1]/maindoc" />

        <!-- check if maindoc is PDF -->
        <xsl:variable name="isPDF" select="translate(str:tokenize($maindoc,'.')[position()=last()],'PDF','pdf') = 'pdf'" />

        <!-- check for supported main file -->
        <xsl:variable name="supportedFile">
          <xsl:if test="string-length($derivateID) &gt; 0">
            <xsl:value-of select="iview2:getSupportedMainFile(string($derivateID))" />
          </xsl:if>
        </xsl:variable>

        <xsl:variable name="imageElement">
          <xsl:choose>
            <!-- when derivate has PDF or supported iview file, use IIIF thumbnail -->
            <xsl:when test="($isPDF and string-length($maindoc) &gt; 0) or string-length($supportedFile) &gt; 0">
              <div class="hit_icon">
                <xsl:attribute name="style">
                  <xsl:variable name="apos">'</xsl:variable>
                  <xsl:value-of select="concat('background-image: url(', $apos, $WebApplicationBaseURL, 'api/iiif/image/v2/thumbnail/', $objectID, '/full/!300,300/0/default.jpg', $apos, ')')"/>
                </xsl:attribute>
              </div>
            </xsl:when>
            <!-- when there is no content derivate, use disabled icon -->
            <xsl:when test="string-length($derivateID) = 0">
              <img class="hit_icon" src="{$WebApplicationBaseURL}images/icons/icon_common_disabled.png"/>
            </xsl:when>
            <!-- otherwise use default icon -->
            <xsl:otherwise>
              <img class="hit_icon" src="{$WebApplicationBaseURL}images/icons/icon_common.png"/>
            </xsl:otherwise>
          </xsl:choose>
        </xsl:variable>

        <a class="hit_option hit_download" href="{$WebApplicationBaseURL}receive/{$objectID}" title="{mods:genre}">
          <xsl:copy-of select="$imageElement" />
        </a>
      </div>
    </xsl:if>

    <!-- hit type / genre -->
    <div class="hit_tnd_container">
      <div class="hit_tnd_content mir-badge-container">
        <!-- OA Badge -->
        <xsl:choose>
          <xsl:when test="string-length($objectID) = 0">
            <span class="badge mir-badge-oa-false" data-bs-toggle="tooltip" title="{i18n:translate('mir.response.openAccess.false')}">
              <i class="fas fa-lock"></i>
            </span>
          </xsl:when>
          <xsl:otherwise>
            <!-- Check if derivate exists with OA classification -->
            <xsl:variable name="hasOA" select="ancestor::mycoreobject/structure/derobjects/derobject[classification[@classid='derivate_types'][@categid='content']]" />
            <xsl:choose>
              <xsl:when test="$hasOA">
                <span class="badge mir-badge-oa-true" data-bs-toggle="tooltip" title="{i18n:translate('mir.response.openAccess.true')}">
                  <i class="fas fa-lock-open"></i>
                </span>
              </xsl:when>
              <xsl:otherwise>
                <span class="badge mir-badge-oa-false" data-bs-toggle="tooltip" title="{i18n:translate('mir.response.openAccess.false')}">
                  <i class="fas fa-lock"></i>
                </span>
              </xsl:otherwise>
            </xsl:choose>
          </xsl:otherwise>
        </xsl:choose>

        <!-- Genre Badge -->
        <xsl:if test="mods:genre">
          <xsl:variable name="genre" select="mods:genre" />
          <span class="badge mir-badge-genre-{$genre}" data-bs-toggle="tooltip" title="{i18n:translate('mir.help.genre')}">
            <xsl:value-of select="$genre" />
          </span>
        </xsl:if>

        <!-- Date Badge -->
        <xsl:if test="mods:originInfo/mods:dateIssued">
          <span class="badge mir-badge-date" data-bs-toggle="tooltip" title="{i18n:translate('mir.date.published')}">
            <xsl:value-of select="mods:originInfo/mods:dateIssued" />
          </span>
        </xsl:if>
      </div>
    </div>

    <!-- hit headline / title -->
    <h3 class="hit_title">
      <xsl:variable name="titleText">
        <xsl:choose>
          <xsl:when test="mods:titleInfo/mods:nonSort">
            <xsl:value-of select="mods:titleInfo/mods:nonSort" />
            <xsl:text> </xsl:text>
          </xsl:when>
        </xsl:choose>
        <xsl:value-of select="mods:titleInfo/mods:title" />
        <xsl:if test="mods:titleInfo/mods:subTitle">
          <xsl:text> : </xsl:text>
          <xsl:value-of select="mods:titleInfo/mods:subTitle" />
        </xsl:if>
      </xsl:variable>

      <xsl:choose>
        <xsl:when test="string-length($objectID) &gt; 0">
          <a href="{$WebApplicationBaseURL}receive/{$objectID}" title="{$titleText}">
            <xsl:value-of select="$titleText" />
          </a>
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="$titleText" />
        </xsl:otherwise>
      </xsl:choose>
    </h3>

    <!-- hit author -->
    <xsl:if test="mods:name[@type='personal']">
      <div class="hit_author">
        <xsl:for-each select="mods:name[@type='personal'][position() &lt;= 3]">
          <xsl:if test="position() > 1">
            <xsl:text> / </xsl:text>
          </xsl:if>
          <xsl:choose>
            <xsl:when test="mods:namePart[@type='family'] and mods:namePart[@type='given']">
              <xsl:value-of select="mods:namePart[@type='family']" />
              <xsl:text>, </xsl:text>
              <xsl:value-of select="mods:namePart[@type='given']" />
            </xsl:when>
            <xsl:when test="mods:displayForm">
              <xsl:value-of select="mods:displayForm" />
            </xsl:when>
            <xsl:otherwise>
              <xsl:value-of select="mods:namePart" />
            </xsl:otherwise>
          </xsl:choose>
        </xsl:for-each>
        <xsl:if test="count(mods:name[@type='personal']) > 3">
          <xsl:text> / et.al.</xsl:text>
        </xsl:if>
      </div>
    </xsl:if>

    <!-- hit abstract -->
    <xsl:if test="mods:abstract">
      <div class="hit_abstract">
        <xsl:value-of select="mods:abstract[1]" />
      </div>
    </xsl:if>

    <!-- hit publisher -->
    <xsl:if test="mods:originInfo/mods:publisher">
      <div class="hit_pub_name">
        <xsl:variable name="date" select="mods:originInfo/mods:dateIssued" />
        <xsl:variable name="place" select="mods:originInfo/mods:place/mods:placeTerm" />

        <xsl:if test="string-length($place) &gt; 0">
          <xsl:value-of select="concat($place, ': ')" />
        </xsl:if>

        <xsl:value-of select="mods:originInfo/mods:publisher" />

        <xsl:if test="string-length($date) &gt; 0">
          <xsl:value-of select="concat(', ', $date)" />
        </xsl:if>
      </div>
    </xsl:if>
  </xsl:template>

</xsl:stylesheet>