<?xml version='1.0'?>
<xsl:stylesheet version="1.0"
                xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                xmlns:rdfs="http://www.w3.org/2000/01/rdf-schema#"
                xmlns:marc="http://www.loc.gov/MARC21/slim"
                xmlns:bf="http://id.loc.gov/ontologies/bibframe/"
                xmlns:bflc="http://id.loc.gov/ontologies/bflc/"
                xmlns:madsrdf="http://www.loc.gov/mads/rdf/v1#"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                exclude-result-prefixes="xsl marc">

  <!--
      Conversion specs for 841-887
  -->

  <xsl:template match="marc:datafield[@tag='856']" mode="item">
    <xsl:param name="serialization" select="'rdfxml'"/>
    <xsl:param name="instanceid"/>
    <xsl:if test="marc:subfield[@code='u'] and
                  not(starts-with(marc:subfield[@code='u'],'http://hdl.handle.net/2027/')) and
                  not(starts-with(marc:subfield[@code='u'],'https://hdl.handle.net/2027/')) and
                  (@ind2='1' or
                  (@ind2 != '0' and @ind2 != '2' and
                  substring(../marc:leader,7,1) != 'm' and
                  substring(../marc:controlfield[@tag='008'],24,1) != 'o' and
                  substring(../marc:controlfield[@tag='008'],24,1) != 's'))">
      <xsl:variable name="vItemUri"><xsl:value-of select="marc:subfield[@code='u']"/></xsl:variable>
      <xsl:choose>
        <xsl:when test="$serialization = 'rdfxml'">
          <bf:Item>
            <xsl:attribute name="rdf:about"><xsl:value-of select="$vItemUri"/></xsl:attribute>
            <xsl:apply-templates select="." mode="locator856">
              <xsl:with-param name="serialization" select="$serialization"/>
              <xsl:with-param name="pProp">bf:electronicLocator</xsl:with-param>
            </xsl:apply-templates>
            <bf:itemOf>
              <xsl:attribute name="rdf:resource"><xsl:value-of select="$instanceid"/></xsl:attribute>
            </bf:itemOf>
          </bf:Item>
        </xsl:when>
      </xsl:choose>
    </xsl:if>
  </xsl:template>
  
  <xsl:template match="marc:datafield[@tag='856']" mode="instance">
    <xsl:param name="recordid"/>
    <xsl:param name="serialization" select="'rdfxml'"/>
    <xsl:if test="marc:subfield[@code='u'] and @ind2='2'">
      <xsl:choose>
        <xsl:when test="$serialization = 'rdfxml' and marc:subfield[@code='z' or @code='y' or @code='3']">
          <xsl:apply-templates select="." mode="locator856">
            <xsl:with-param name="serialization" select="$serialization"/>
            <xsl:with-param name="pProp">bf:supplementaryContent</xsl:with-param>
          </xsl:apply-templates>
        </xsl:when>
        <xsl:otherwise>
          <xsl:for-each select="marc:subfield[@code='u']">
            <xsl:element name="bf:supplementaryContent">
              <rdfs:Resource>
                <bflc:locator>
                  <xsl:attribute name="rdf:resource"><xsl:value-of select="."/></xsl:attribute>
                </bflc:locator>
              </rdfs:Resource>
            </xsl:element>
          </xsl:for-each>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:if>
    <xsl:if test="marc:subfield[@code='u'] and
                  not(starts-with(marc:subfield[@code='u'],'http://hdl.handle.net/2027/')) and
                  not(starts-with(marc:subfield[@code='u'],'https://hdl.handle.net/2027/')) and
                  (@ind2='1' or
                  (@ind2 != '0' and @ind2 != '2' and
                  substring(../marc:leader,7,1) != 'm' and
                  substring(../marc:controlfield[@tag='008'],24,1) != 'o' and
                  substring(../marc:controlfield[@tag='008'],24,1) != 's'))">
      <bf:hasItem>
        <xsl:attribute name="rdf:resource"><xsl:value-of select="marc:subfield[@code='u']"/></xsl:attribute>
      </bf:hasItem>
    </xsl:if>
  </xsl:template>
          
  <xsl:template match="marc:datafield[@tag='850' or @tag='852']" mode="hasItem">
    <xsl:param name="recordid"/>
    <xsl:param name="serialization" select="'rdfxml'"/>
    <xsl:variable name="vItemUriStem"><xsl:value-of select="$recordid"/>#Item<xsl:value-of select="@tag"/>-<xsl:value-of select="position()"/></xsl:variable>
    <xsl:variable name="vAddress">
      <xsl:call-template name="chopPunctuation">
        <xsl:with-param name="chopString">
          <xsl:for-each select="marc:subfield[@code='e' or @code='n']">
            <xsl:value-of select="concat(.,', ')"/>
          </xsl:for-each>
        </xsl:with-param>
      </xsl:call-template>
    </xsl:variable>
    <xsl:choose>
      <xsl:when test="$serialization = 'rdfxml'">
        <xsl:for-each select="marc:subfield[@code='a']">
          <xsl:variable name="vItemUri">
            <xsl:choose>
              <xsl:when test="parent::marc:datafield/@tag='850'">
                <xsl:value-of select="$vItemUriStem"/>-<xsl:value-of select="position()"/>
              </xsl:when>
              <xsl:otherwise><xsl:value-of select="$vItemUriStem"/></xsl:otherwise>
            </xsl:choose>
          </xsl:variable>
          <bf:hasItem>
            <bf:Item>
              <xsl:attribute name="rdf:about"><xsl:value-of select="$vItemUri"/></xsl:attribute>
              <bf:heldBy>
                <bf:Agent>
                  <xsl:choose>
                    <xsl:when test="string-length(.) &lt; 10">
                      <xsl:attribute name="rdf:about"><xsl:value-of select="concat($organizations,.)"/></xsl:attribute>
                    </xsl:when>
                    <xsl:otherwise>
                      <rdfs:label><xsl:value-of select="."/></rdfs:label>
                    </xsl:otherwise>
                  </xsl:choose>
                </bf:Agent>
              </bf:heldBy>
              <xsl:if test="../@tag='852'">
                <xsl:for-each select="../marc:subfield[@code='b']">
                  <bf:subLocation>
                    <bf:SubLocation>
                      <rdfs:label><xsl:value-of select="."/></rdfs:label>
                    </bf:SubLocation>
                  </bf:subLocation>
                </xsl:for-each>
                <xsl:if test="$vAddress != ''">
                  <bf:subLocation>
                    <bf:SubLocation>
                      <rdfs:label><xsl:value-of select="$vAddress"/></rdfs:label>
                    </bf:SubLocation>
                  </bf:subLocation>
                </xsl:if>
                <xsl:for-each select="../marc:subfield[@code='u']">
                  <bf:electronicLocator>
                    <xsl:attribute name="rdf:resource"><xsl:value-of select="."/></xsl:attribute>
                  </bf:electronicLocator>
                </xsl:for-each>
                <xsl:for-each select="../marc:subfield[@code='x' or @code='z']">
                  <bf:note>
                    <bf:Note>
                      <rdfs:label><xsl:value-of select="."/></rdfs:label>
                    </bf:Note>
                  </bf:note>
                </xsl:for-each>
              </xsl:if>
              <bf:itemOf>
                <xsl:attribute name="rdf:resource"><xsl:value-of select="$recordid"/>#Instance</xsl:attribute>
              </bf:itemOf>
            </bf:Item>
          </bf:hasItem>
        </xsl:for-each>
      </xsl:when>
    </xsl:choose>
  </xsl:template>

  <xsl:template match="marc:datafield[@tag='856']" mode="hasItem">
    <xsl:param name="recordid"/>
    <xsl:param name="serialization" select="'rdfxml'"/>
    <xsl:if test="marc:subfield[@code='u'] and
                  (@ind2='0' or
                  substring(../marc:leader,7,1)='m' or
                  substring(../marc:controlfield[@tag='008'],24,1)='o' or
                  substring(../marc:controlfield[@tag='008'],24,1)='s')">
      <xsl:variable name="vItemUri"><xsl:value-of select="$recordid"/>#Item<xsl:value-of select="@tag"/>-<xsl:value-of select="position()"/></xsl:variable>
      <xsl:choose>
        <xsl:when test="$serialization = 'rdfxml'">
          <bf:hasItem>
            <bf:Item>
              <xsl:attribute name="rdf:about"><xsl:value-of select="$vItemUri"/></xsl:attribute>
              <xsl:apply-templates select="." mode="locator856">
                <xsl:with-param name="serialization" select="$serialization"/>
                <xsl:with-param name="pProp">bf:electronicLocator</xsl:with-param>
              </xsl:apply-templates>
              <bf:itemOf>
                <xsl:attribute name="rdf:resource"><xsl:value-of select="$recordid"/>#Instance</xsl:attribute>
              </bf:itemOf>
            </bf:Item>
          </bf:hasItem>
        </xsl:when>
      </xsl:choose>
    </xsl:if>
  </xsl:template>

  <xsl:template match="marc:datafield[@tag='856']" mode="locator856">
    <xsl:param name="serialization" select="'rdfxml'"/>
    <xsl:param name="pProp"/>
    <xsl:choose>
      <xsl:when test="$serialization='rdfxml'">
        <xsl:for-each select="marc:subfield[@code='u']">
          <xsl:if test="../marc:subfield[@code='z' or @code='y' or @code='3']">
            <xsl:element name="{$pProp}">
              <rdfs:Resource>
                <xsl:if test="$pProp = 'bf:supplementaryContent'">
                  <bflc:locator>
                    <xsl:attribute name="rdf:resource"><xsl:value-of select="."/></xsl:attribute>
                  </bflc:locator>
                </xsl:if>
                <xsl:for-each select="../marc:subfield[@code='z' or @code='y' or @code='3']">
                  <bf:note>
                    <bf:Note>
                      <rdfs:label><xsl:value-of select="."/></rdfs:label>
                    </bf:Note>
                  </bf:note>
                </xsl:for-each>
              </rdfs:Resource>
            </xsl:element>
          </xsl:if>
        </xsl:for-each>
      </xsl:when>
    </xsl:choose>
  </xsl:template>

  
</xsl:stylesheet>
