package org.hathitrust.htrc.tools.ef.metadata.marcjsontomarcxml

import java.io.File
import java.nio.charset.StandardCharsets

import com.gilt.gfc.time.Timer
import com.sun.org.apache.bcel.internal.generic._
import org.apache.commons.io.FileUtils
import org.apache.hadoop.fs.Path
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.hathitrust.htrc.tools.ef.metadata.marcjsontomarcxml.Helper.{logger, readStdIn}
import org.hathitrust.htrc.tools.spark.errorhandling.ErrorAccumulator
import org.hathitrust.htrc.tools.spark.errorhandling.RddExtensions._
import org.json4s.DefaultFormats
import org.json4s.JsonAST._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

import scala.language.reflectiveCalls
import scala.util.matching.Regex

object Main {
  val appName: String = "marcjson2bibframexml"
  val marc2BibframeXsl: String = new File(System.getProperty("bibframe-xsl")).getAbsolutePath
//  val marc2BibframeXsl = "/xsl/marc2bibframe2.xsl"
  val oclcMarker: String = "(OCoLC)"
  val oclcRegex: Regex = raw"""${Regex.quote(oclcMarker)}(.*)""".r

  def main(args: Array[String]): Unit = {
    val conf = new Conf(args.toIndexedSeq)
    val inputFile = conf.inputFile.toOption
    val outputPath = conf.outputPath().toString

    conf.outputPath().mkdirs()

    // set up logging destination
    conf.sparkLog.toOption match {
      case Some(logFile) => System.setProperty("spark.logFile", logFile)
      case None =>
    }
    System.setProperty("logLevel", conf.logLevel().toUpperCase)

    // set up Spark context
    val sparkConf = new SparkConf()
    sparkConf.setAppName(appName)
    sparkConf.setIfMissing("spark.master", "local[*]")

    val spark = SparkSession.builder()
      .config(sparkConf)
      .getOrCreate()

    val sc = spark.sparkContext

    val numPartitions = conf.numPartitions.getOrElse{
      if (inputFile.isDefined) sc.defaultMinPartitions
      else sc.defaultParallelism
    }

    logger.info("Starting...")

    // record start time
    val t0 = System.nanoTime()

    logger.info(s"Using XSL: $marc2BibframeXsl")

    val marcJsonTextsRDD = inputFile match {
      case Some(file) => sc.textFile(file.toString, minPartitions = numPartitions)
      case None => sc.parallelize(readStdIn(), numSlices = numPartitions)
    }

    val errorsParseMarcJson = new ErrorAccumulator[String, String](identity)(sc)
    val volIdMarcJsonlinePairRDD = marcJsonTextsRDD
      .tryMap { jsonLine =>
        implicit val formats: DefaultFormats = DefaultFormats

        val marcRecord = parse(jsonLine)
        val volIds = (marcRecord \ "fields" \ "974" \ "subfields" \ "u").extract[List[String]]

        volIds.head -> jsonLine
      }(errorsParseMarcJson)

//    val marcJsonsSignaturesRDD = marcJsonTextsRDD.tryMap { jsonLine =>
//      implicit val formats: DefaultFormats = DefaultFormats
//
//      val marcRecord = parse(jsonLine)
//      val fields = marcRecord \ "fields"
//      val volId = (fields \\ "974" \\ "u").extract[String]
//      val oclcSubfieldAValues = (fields \\ "035" \\ "subfields" \\ "a").children.map(_.extract[String])
//      val recordSignature = oclcSubfieldAValues.sorted.mkString("|")
//
//      recordSignature -> (volId, marcRecord)
//    }(errorsParseMarcJson)
//
//    val errorsReduceMarc = new ErrorAccumulator[(String, Iterable[(String, JValue)]), String](_._2.map(_._1).mkString("|"))(sc)
//    val marcJsonsCleanedRDD = marcJsonsSignaturesRDD
//      .groupByKey()
//      .tryFlatMap {
//        case (signature, xs) if signature.isEmpty => xs
//        case (_, records) =>
//          implicit val formats: DefaultFormats = DefaultFormats
//
//          val reducedRecords = records.tail.map { case (id, marcRecord) =>
//            val fields = marcRecord \ "fields"
//            val oclcSubfieldAFields = (fields \\ "035").children
//              .map(f => f -> (f \\ "subfields" \\ "a").extractOpt[String])
//              .collect {
//                case (f, Some(value)) => f -> value
//              }
//
//            val oclcOpt = oclcSubfieldAFields.collectFirst { case (f, oclcRegex(_)) => f }
//            val leader = (marcRecord \ "leader").extract[String]
//
//            val reducedRecord = oclcOpt match {
//              case Some(oclcField) => JObject(
//                "leader" -> leader,
//                "fields" -> (
//                  JArray((fields \ "001").children.map(f => JObject("001" -> f))) ++
//                  JArray(JObject("035" -> oclcField) :: Nil) ++
//                  JArray((fields \ "974").children.map(f => JObject("974" -> f)))
//                )
//              )
//
//              case None => JObject(
//                "leader" -> leader,
//                "fields" -> (
//                  JArray((fields \ "001").children.map(f => JObject("001" -> f))) ++
//                  JArray((fields \ "974").children.map(f => JObject("974" -> f)))
//                )
//              )
//            }
//
//            id -> reducedRecord
//          }
//
//          Iterable(records.head) ++ reducedRecords
//      }(errorsReduceMarc)
//      .mapValues(record => compact(render(record)))

    val errorsConvertMarc2Bibframe = new ErrorAccumulator[(String, String), String](_._1)(sc)
//    val bibframeXmlRDD = marcJsonsCleanedRDD.tryMapValues(Helper.marcJson2BibframeXml)(errorsConvertMarc2Bibframe)
    val bibframeXmlRDD = volIdMarcJsonlinePairRDD.tryMapValues(Helper.marcJson2BibframeXml)(errorsConvertMarc2Bibframe)

    bibframeXmlRDD.saveAsSequenceFile(outputPath + "/output")

//    bibframeXmlRDD.foreach { case (id, xml) =>
//      val cleanId = id.replaceAllLiterally(":", "+").replaceAllLiterally("/", "=")
//      FileUtils.writeStringToFile(new File(outputPath, s"$cleanId.xml"), xml, StandardCharsets.UTF_8)
//    }

//    marcJsonsCleanedRDD.saveAsTextFile(outputPath + "/output")

//    if (errorsParseMarcJson.nonEmpty || errorsReduceMarc.nonEmpty || errorsConvertMarc2Bibframe.nonEmpty)
    if (errorsParseMarcJson.nonEmpty || errorsConvertMarc2Bibframe.nonEmpty)
      logger.info("Writing error report(s)...")

    // save any errors to the output folder
    if (errorsParseMarcJson.nonEmpty)
      errorsParseMarcJson.saveErrors(new Path(outputPath, "marcjson_errors.txt"))

//    if (errorsReduceMarc.nonEmpty)
//      errorsReduceMarc.saveErrors(new Path(outputPath, "reducemarc_errors.txt"), _.toString)

    if (errorsConvertMarc2Bibframe.nonEmpty)
      errorsConvertMarc2Bibframe.saveErrors(new Path(outputPath, "marc2bibframe_errors.txt"))

    // record elapsed time and report it
    val t1 = System.nanoTime()
    val elapsed = t1 - t0

    logger.info(f"All done in ${Timer.pretty(elapsed)}")
  }

}
