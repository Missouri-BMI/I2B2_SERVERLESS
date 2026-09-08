<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:xs="http://www.w3.org/2001/XMLSchema" version="2.0">
    <xsl:output method="html"/>
    <xsl:param name="DATE_TIME" required="yes" as="xs:string"/>

    <xsl:template match="/">
        <html>
            <head>
            <title>JMeter Results</title>
            </head>
            <body>
                <p> <xsl:value-of select="$DATE_TIME"/> </p>

                <p>
                    <b style="color: blue;"> Total Failures: <xsl:value-of select="count(//assertionResult/failure[text()='true'])" /> </b>
                </p>

                <xsl:apply-templates />

            </body>
        </html>
    </xsl:template>

    <xsl:template match="sample"></xsl:template>

    <xsl:template match="httpSample">

        <xsl:if test="@lb='queryResult (polling)'">
            <xsl:if test="assertionResult/failure='true'">
                <h3 style="background-color: lightpink;padding: 2px;"><xsl:value-of select="@tn"/>: Sample <xsl:value-of select="@lb"/></h3>
            </xsl:if>

            <xsl:if test="assertionResult/failure='false'">
                <h3 style="background-color: lightblue;padding: 2px;"><xsl:value-of select="@tn"/>: Sample <xsl:value-of select="@lb"/></h3>
            </xsl:if>

            <h4>Response Output</h4>
            <p>
                <pre>
                    <xsl:value-of select="responseData"/>
                </pre>
            </p>
        </xsl:if>
    </xsl:template>

</xsl:stylesheet>