package org.sharpsw.spark

/*
 * Inside the SBT shell
 * Input file should be a zip as of now.
 * 1. Processing local input file and storing the processed files locally
 * run --local [PathToLocalInputFile] --destination [Destination folder] --master [SparkMaster]
 *
 * 2. Processing local input file and storing the files in cloud storage
 * run --local [PathToLocalInputFile] --destination [Destination folder] \
   --dest-bucket [DestinationBucketName] --dest-prefix [PrefixInsideDestinationBucket] \
   --master [SparkMaster]
 *
 * 3. Processing input file from cloud storage and storing the out data locally
 * run --aws-s3 [PathToInputFileInsideBucket] --src-bucket [InputFileBucketName] \
   --destination [Destination folder] --master [SparkMaster]
 *
 * 4. Processing input file from cloud storage and storing the output data on the cloud storage
 * run --aws-s3 [PathToInputFileInsideBucket] --src-bucket [InputFileBucketName] \
   --destination [Destination folder] --dest-bucket [DestinationBucketName] \
   --dest-prefix [PrefixInsideDestinationBucket] --master [SparkMaster]
 */
object CmdLineOptions {

  // If the input file is stored locally
  val LocalFileInputSource: String = "--local"
  // If the input file is stored in any of the Big three Cloud providers
  val S3FileInputSource: String = "--aws-s3"
  val AzureFileInputSource: String = "--azure"
  val GCloudFileInputSource: String = "--gcloud"

  // Bucket where the input file is present
  val SourceBucket: String = "--src-bucket"

  // Local directory for storing processed data
  val Destination: String = "--destination"
  // Cloud storage bucket(container) name and path prefix(folder) inside the bucket
  val DestinationBucket: String = "--dest-bucket"
  val DestinationBucketPrefix: String = "--dest-prefix"

  //spark master
  val Master: String = "--master"
}
