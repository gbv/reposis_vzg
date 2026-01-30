<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:mods="http://www.loc.gov/mods/v3"
  xmlns:xlink="http://www.w3.org/1999/xlink"
  exclude-result-prefixes="xlink mods">

  <xsl:import href="xslImport:solr-document:vzg-solr.xsl" />

  <xsl:strip-space elements="mods:*" />

  <xsl:template match="mycoreobject[./metadata/def.modsContainer/modsContainer/mods:mods]">
    <xsl:apply-imports />

    <xsl:for-each select="metadata/def.modsContainer/modsContainer/mods:mods/mods:identifier[@type]">
      <field name="mods.identifier.type.{@type}">
        <xsl:value-of select="text()" />
      </field>
    </xsl:for-each>
  </xsl:template>


</xsl:stylesheet>
