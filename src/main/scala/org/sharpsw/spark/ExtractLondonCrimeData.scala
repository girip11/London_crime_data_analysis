package org.sharpsw.spark

import scala.collection.Map
import org.apache.log4j.{ConsoleAppender, Level, Logger, PatternLayout, RollingFileAppender}
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.sharpsw.spark.utils.DataFrameUtil.{saveDataFrameToCsv, saveDataFrameToParquet}
import org.sharpsw.spark.utils.TraceUtil.{timed, timing}
import LondonCrimeDataExplorer._
import org.sharpsw.spark.utils.ArgsUtil.parseArgs
import org.sharpsw.spark.utils.FileUtil.{buildFilePath, getListOfFiles}
import org.sharpsw.spark.utils.{StorageUtil, StorageUtilFactory}
import org.sharpsw.spark.utils.ZipUtils.unZipIt

object ExtractLondonCrimeData {
  private val inputFile = "london_crime_by_lsoa.csv"

  @transient lazy val logger = getLogger()
  Logger.getLogger("org.apache").setLevel(Level.ERROR)

  private def getLogger(): Logger = {
    val appLogger = Logger.getLogger(getClass.getName)
    appLogger.setLevel(Level.DEBUG)

    //Define log pattern layout
    val layout = new PatternLayout("%d{ISO8601} [%t] %-5p %c %x - %m%n")

    //Add console appender to logger
    appLogger.addAppender(new ConsoleAppender(layout))

    val fileAppender = new RollingFileAppender(layout, "application.log", false)

    //Add the appender to logger
    appLogger.addAppender(fileAppender)

    appLogger
  }

  private def getStorageUtil(argsMap: Map[String, String]): StorageUtil = {
    if (argsMap.contains(CmdLineOptions.S3FileInputSource)) {
      StorageUtilFactory.getStorageUtil(CmdLineOptions.S3FileInputSource)
    } else if (argsMap.contains(CmdLineOptions.AzureFileInputSource)) {
      StorageUtilFactory.getStorageUtil(CmdLineOptions.AzureFileInputSource)
    } else if (argsMap.contains(CmdLineOptions.GCloudFileInputSource)) {
      StorageUtilFactory.getStorageUtil(CmdLineOptions.GCloudFileInputSource)
    } else {
      null
    }
  }

  private def prepareInputFile(argsMap: Map[String, String], storageUtil: StorageUtil): Unit = {
    var inputZipFile = argsMap.getOrElse(CmdLineOptions.LocalFileInputSource, "")

    // Now use the storage util to download from the cloud storage
    if (!argsMap.contains(CmdLineOptions.LocalFileInputSource)) {
      val bucket: String = argsMap(CmdLineOptions.SourceBucket)
      val pathToInputFile: String = argsMap(storageUtil.getCloudStorageType())
      logger.info(s"Downloading object $pathToInputFile from bucket $bucket")
      inputZipFile = storageUtil.downloadObject(bucket, pathToInputFile)
    }

    unZipIt(inputZipFile, ".")
  }

  def main(args: Array[String]): Unit = {
    if (args.isEmpty) {
      throw new Exception("Please provide command line options")
    }

    val argsMap = parseArgs(args)
    logger.info("Processing London crime data information")
    val storageUtil = getStorageUtil(argsMap)
    prepareInputFile(argsMap, storageUtil)

    val sparkSession: SparkSession =
      if (!argsMap.contains(CmdLineOptions.Master))
        SparkSession.builder.appName("ExtractLondonCrimeData").getOrCreate()
      else
        SparkSession.builder
          .appName("ExtractLondonCrimeData")
          .master(argsMap(CmdLineOptions.Master))
          .config("spark.rpc.askTimeout", 2000)
          .getOrCreate()

    val destinationFolder = argsMap(CmdLineOptions.Destination)

    logger.info(s"Reading $inputFile contents")
    val fileContents = sparkSession.sparkContext.textFile(inputFile)
    val (headerColumns, contents) =
      timed("Reading file contents", readContents(fileContents, sparkSession))
    contents.cache()

    logger.info("Printing data set schema information:")
    headerColumns.foreach(logger.info(_))

    performAnalysis(sparkSession, contents, destinationFolder)
    logger.info("Finished Extract London Crime data information")
    sparkSession.stop()

    if (storageUtil != null && argsMap.contains(CmdLineOptions.DestinationBucket)) {
      logger.info("Uploading the processed data to cloud storage")
      storageUtil.uploadFiles(
        argsMap(CmdLineOptions.DestinationBucket),
        argsMap(CmdLineOptions.DestinationBucketPrefix),
        destinationFolder,
        getListOfFiles(destinationFolder))
    }

  }

  private def performAnalysis(
    sparkSession: SparkSession,
    contents: DataFrame,
    destinationFolder: String): Unit = {
    logger.info("Extracting distinct boroughs")
    val boroughs = timed("Extracting distinct boroughs", extractDistinctBoroughs(contents))
    timed(
      "Exporting boroughs to csv",
      saveDataFrameToCsv(boroughs, buildFilePath(destinationFolder, "borough_csv")))
    timed(
      "Exporting the boroughs data frame to parquet",
      saveDataFrameToParquet(boroughs, buildFilePath(destinationFolder, "bourough_parquet")))

    logger.info("Extracting LSOA codes by borough")
    val lsoa = timed("Extracting LSOA codes by borough", extractBoroughLsoa(contents))
    timed(
      "Exporting lsoa codes to csv",
      saveDataFrameToCsv(lsoa, buildFilePath(destinationFolder, "lsoa_csv")))
    timed(
      "Exporting lsoa codes to parquet",
      saveDataFrameToParquet(lsoa, buildFilePath(destinationFolder, "lsoa_parquet")))

    logger.info("Extracting distinct major crime categories")
    val majorCrimeCategories =
      timed("Extracting major categories", extractDistinctMajorCrimeCategories(contents))
    timed(
      "Exporting major categories to csv",
      saveDataFrameToCsv(
        majorCrimeCategories,
        buildFilePath(destinationFolder, "major_category_csv")))
    timed(
      "Exporting major categories to parquet",
      saveDataFrameToParquet(
        majorCrimeCategories,
        buildFilePath(destinationFolder, "major_category_parquet")))

    logger.info("Extracting distinct minor crime categories")
    val minorCrimeCategories =
      timed("Extracting minor categories", extractDistinctMinorCrimeCategories(contents))
    timed(
      "Exporting minor category to csv",
      saveDataFrameToCsv(
        minorCrimeCategories,
        buildFilePath(destinationFolder, "minor_category_csv")))
    timed(
      "Exporting minor category to parquet",
      saveDataFrameToParquet(
        minorCrimeCategories,
        buildFilePath(destinationFolder, "minor_category_parquet")))

    logger.info("Extracting distinct combined crime categories")
    val categories = timed("Extracting categories to csv", extractCombinedCategories(contents))
    timed(
      "Exporting categories to csv",
      saveDataFrameToCsv(categories, buildFilePath(destinationFolder, "categories_csv")))
    timed(
      "Exporting categories to parquet",
      saveDataFrameToParquet(categories, buildFilePath(destinationFolder, "categories_parquet")))

    logger.info("Calculating total crimes by borough")
    val crimesByBorough =
      timed("Calculate total crimes by borough", calculateTotalCrimeCountByBorough(contents))
    timed(
      "Exporting resulting aggregation to CSV",
      saveDataFrameToCsv(
        crimesByBorough,
        buildFilePath(destinationFolder, "total_crimes_by_borough_csv")))
    timed(
      "Exporting resulting aggregation to parquet",
      saveDataFrameToParquet(
        crimesByBorough,
        buildFilePath(destinationFolder, "total_crimes_by_borough_parquet"))
    )

    logger.info("Calculating total crimes by major category")
    val crimesByMajorCategory =
      timed("Calculate total crimes by major category", calculateCrimesByMajorCategory(contents))
    timed(
      "Exporting resulting aggregation - by major category to csv",
      saveDataFrameToCsv(
        crimesByMajorCategory,
        buildFilePath(destinationFolder, "total_crimes_by_major_category_csv"))
    )
    timed(
      "Exporting resulting aggregation - by major category to parquet",
      saveDataFrameToParquet(
        crimesByMajorCategory,
        buildFilePath(destinationFolder, "total_crimes_by_major_category_parquet"))
    )

    logger.info("Calculating total crimes by minor category")
    val crimesByMinorCategory = timed(
      "Calculate total crimes by minor category",
      calculateCrimeCountByMinorCategory(contents))
    timed(
      "Exporting resulting aggregation - by minor category to csv",
      saveDataFrameToCsv(
        crimesByMinorCategory,
        buildFilePath(destinationFolder, "total_crimes_by_minor_category_csv"))
    )
    timed(
      "Exporting resulting aggregation - by minor category to parquet",
      saveDataFrameToParquet(
        crimesByMinorCategory,
        buildFilePath(destinationFolder, "total_crimes_by_minor_category_parquet"))
    )

    logger.info("Calculating total crimes by borough and year")
    val crimesByBoroughAndYear = timed(
      "Calculate total crimes by borough and year",
      calculateCrimeCountByBoroughAndYear(contents))
    timed(
      "Exporting resulting aggregation - by borough and year to csv",
      saveDataFrameToCsv(
        crimesByBoroughAndYear,
        buildFilePath(destinationFolder, "total_crimes_by_borough_year_csv"))
    )
    timed(
      "Exporting resulting aggregation - by borough and year to parquet",
      saveDataFrameToParquet(
        crimesByBoroughAndYear,
        buildFilePath(destinationFolder, "total_crimes_by_borough_year_parquet"))
    )

    logger.info("Calculating total crimes by major category and year")
    val crimesByMajorCategoryAndYear = timed(
      "Calculate total crimes by major category and year",
      calculateCrimesByMajorCategoryAndYear(contents))
    timed(
      "Exporting resulting aggregation - by major category and year to csv",
      saveDataFrameToCsv(
        crimesByMajorCategoryAndYear,
        buildFilePath(destinationFolder, "total_crimes_by_major_category_year_csv"))
    )
    timed(
      "Exporting resulting aggregation - by major category and year to parquet",
      saveDataFrameToParquet(
        crimesByMajorCategoryAndYear,
        buildFilePath(destinationFolder, "total_crimes_by_major_category_year_parquet"))
    )

    logger.info("Calculating total crimes by minor category and year")
    val crimesByMinorCategoryAndYear = timed(
      "Calculate total crimes by minor category and year",
      calculateCrimesByMinorCategoryAndYear(contents))
    timed(
      "Exporting resulting aggregation - by minor category and year to csv",
      saveDataFrameToCsv(
        crimesByMinorCategoryAndYear,
        buildFilePath(destinationFolder, "total_crimes_by_minor_category_year_csv"))
    )
    timed(
      "Exporting resulting aggregation - by minor category and year to parquet",
      saveDataFrameToParquet(
        crimesByMinorCategoryAndYear,
        buildFilePath(destinationFolder, "total_crimes_by_minor_category_year_parquet"))
    )

    logger.info("Calculating total crimes by year")
    val crimesByYear = timed("Calculate total crimes by year", calculateCrimesByYear(contents))
    timed(
      "Exporting crimes by year results to csv",
      saveDataFrameToCsv(
        crimesByYear,
        buildFilePath(destinationFolder, "total_crimes_by_year_csv")))
    timed(
      "Exporting crimes by year results to parquet",
      saveDataFrameToParquet(
        crimesByYear,
        buildFilePath(destinationFolder, "total_crimes_by_year_parquet")))

    logger.info("Calculating total crimes by year and month")
    val crimesByYearMonth =
      timed("Calculate total crimes by year", calculateCrimesByYearAndMonth(contents))
    timed(
      "Exporting crimes by year and month results to CSV",
      saveDataFrameToCsv(
        crimesByYearMonth,
        buildFilePath(destinationFolder, "total_crimes_by_year_month_csv"))
    )
    timed(
      "Exporting crimes by year and month results to parquet",
      saveDataFrameToParquet(
        crimesByYearMonth,
        buildFilePath(destinationFolder, "total_crimes_by_year_month_parquet"))
    )

    logger.info("Percentages of crimes by years")
    val crimePercentageByYear = calculateCrimesPercentageByCategoryAndYear(contents, sparkSession)
    crimePercentageByYear.foreach(item => {
      logger.info(s"Exporting crime percentage for year '${item._1}'")
      timed(
        s"Exporting crime percentage in ${item._1} to CSV",
        saveDataFrameToCsv(
          item._2,
          buildFilePath(destinationFolder, s"crime_percentage_${item._1}_csv")))
      timed(
        s"Exporting crime percentage in ${item._1} to parquet",
        saveDataFrameToParquet(
          item._2,
          buildFilePath(destinationFolder, s"crime_percentage_${item._1}_parquet"))
      )
    })

    logger.info("Calculating total crimes by year and LSOA codes")
    val totalCrimesByYearAndLsoa = timed(
      "Calculating total crimes by year and lsoa codes",
      calculateTotalCrimesByYearLsoaCode(contents))
    timed(
      "Exporting results to csv",
      saveDataFrameToCsv(
        totalCrimesByYearAndLsoa,
        buildFilePath(destinationFolder, "total_crimes_by_year_lsoa_code_csv")))
    timed(
      "Exporting results to parquet",
      saveDataFrameToCsv(
        totalCrimesByYearAndLsoa,
        buildFilePath(destinationFolder, "total_crimes_by_year_lsoa_code_parquet")))
    println(timing)
  }
}
