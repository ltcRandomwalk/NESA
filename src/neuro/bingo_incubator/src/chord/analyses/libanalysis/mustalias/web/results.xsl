<xsl:stylesheet
	version="2.0"
	xmlns="http://www.w3.org/1999/xhtml"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

<xsl:include href="M.xsl"/>
<xsl:include href="I.xsl"/>
<xsl:include href="H.xsl"/>

<xsl:template match="/">
    <xsl:result-document href="results.html">
	<html>
	<head>
		<title>TypeState Results</title>
		<link rel="stylesheet" href="style.css" type="text/css"/>
	</head>
	<body>
	<table class="details">
    <colgroup>
            <col width="50%"/>
            <col width="50%"/>
    </colgroup>
	<xsl:for-each select="results/groups/group">
		<tr><td class="head1" colspan="2">Group:
				X lower=<xsl:value-of select="@Xlower"/>,
				X upper=<xsl:value-of select="@Xupper"/>,
				Y=<xsl:value-of select="@Y"/>,
				Probability=<xsl:value-of select="@Prob"/>
		</td></tr>
		<xsl:for-each select="id(@Mids)">
			<tr><td class="head2" colspan="2"><xsl:apply-templates select="."/></td></tr>
		</xsl:for-each>
		<xsl:for-each select="query">
			<tr><td><xsl:apply-templates select="id(@Iid)"/></td>
				<td><xsl:apply-templates select="id(@Hid)"/></td>
			</tr>
		</xsl:for-each>
	</xsl:for-each>
	</table>
	</body>
	</html>
    </xsl:result-document>
</xsl:template>

</xsl:stylesheet>

