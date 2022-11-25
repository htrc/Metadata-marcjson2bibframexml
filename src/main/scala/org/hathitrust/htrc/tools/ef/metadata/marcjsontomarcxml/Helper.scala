package org.hathitrust.htrc.tools.ef.metadata.marcjsontomarcxml

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, StringReader, StringWriter}
import java.nio.file.{Files, Paths}

import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.{StreamResult, StreamSource}
import net.sf.saxon.Configuration
import net.sf.saxon.lib.FeatureKeys
import org.apache.commons.io.IOUtils
import org.hathitrust.htrc.tools.ef.metadata.marcjsontomarcxml.Main.marc2BibframeXsl
import org.hathitrust.htrc.tools.scala.io.IOUtils.using
import org.marc4j.{MarcJsonReader, MarcXmlWriter}
import org.slf4j.{Logger, LoggerFactory}

import scala.io.StdIn

object Helper {
  @transient lazy val logger: Logger = LoggerFactory.getLogger(Main.appName)

  val systemId: String = Paths.get(marc2BibframeXsl).toUri.toString
//  val systemId: String = getClass.getResource(marc2BibframeXsl).toString
  val xslBytes: Array[Byte] = Files.readAllBytes(Paths.get(marc2BibframeXsl))
//  val xslBytes: Array[Byte] = IOUtils.toByteArray(getClass.getResourceAsStream(marc2BibframeXsl))

  def readStdIn(): Seq[String] = Iterator.continually(StdIn.readLine()).takeWhile(_ != null).toList

  def marcJson2MarcXml(jsonLine: String): Array[Byte] = {
    val marcRecord = using(new StringReader(jsonLine)) { sr =>
      val marcJsonReader = new MarcJsonReader(sr)
      if (marcJsonReader.hasNext) marcJsonReader.next() else null
    }
    assert(marcRecord != null, s"Could not find MARC record in line: $jsonLine")

    val baos = new ByteArrayOutputStream()
    using(new MarcXmlWriter(baos))(_.write(marcRecord))

    baos.toByteArray
  }

  def marcJson2BibframeXml(jsonLine: String): String = {
    val xmlBytes = marcJson2MarcXml(jsonLine)
    val xmlSource = new StreamSource(new ByteArrayInputStream(xmlBytes))
    val xslSource = new StreamSource(new ByteArrayInputStream(xslBytes))
    xslSource.setSystemId(systemId)

    val xmlStringWriter = new StringWriter()
    val result = new StreamResult(xmlStringWriter)
    val transformerFactory = TransformerFactory.newInstance()
    if (transformerFactory.isInstanceOf[net.sf.saxon.TransformerFactoryImpl])
      transformerFactory.setAttribute(FeatureKeys.RECOVERY_POLICY, Integer.valueOf(Configuration.RECOVER_SILENTLY))
    val transformer = transformerFactory.newTransformer(xslSource)
    transformer.transform(xmlSource, result)

    xmlStringWriter.toString
  }
}