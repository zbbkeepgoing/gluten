/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.glutenproject.utils

import io.glutenproject.backendsapi.clickhouse.CHBackendSettings
import io.glutenproject.sql.shims.SparkShimLoader

import org.apache.spark.internal.Logging
import org.apache.spark.sql.catalyst.expressions.Attribute
import org.apache.spark.sql.connector.read.InputPartition
import org.apache.spark.sql.execution.PartitionedFileUtil
import org.apache.spark.sql.execution.datasources.{FilePartition, HadoopFsRelation, PartitionDirectory, PartitionedFile}
import org.apache.spark.util.SparkResourceUtil
import org.apache.spark.util.collection.BitSet

import scala.collection.mutable.ArrayBuffer

case class CHInputPartitionsUtil(
    relation: HadoopFsRelation,
    selectedPartitions: Array[PartitionDirectory],
    output: Seq[Attribute],
    optionalBucketSet: Option[BitSet],
    optionalNumCoalescedBuckets: Option[Int],
    disableBucketedScan: Boolean)
  extends Logging {

  private val bucketedScan: Boolean = {
    if (
      relation.sparkSession.sessionState.conf.bucketingEnabled && relation.bucketSpec.isDefined
      && !disableBucketedScan
    ) {
      val spec = relation.bucketSpec.get
      val bucketColumns = spec.bucketColumnNames.flatMap(n => toAttribute(n))
      bucketColumns.size == spec.bucketColumnNames.size
    } else {
      false
    }
  }

  def genInputPartitionSeq(): Seq[InputPartition] = {
    if (bucketedScan) {
      genBucketedInputPartitionSeq()
    } else {
      genNonBuckedInputPartitionSeq()
    }
  }

  private def genNonBuckedInputPartitionSeq(): Seq[InputPartition] = {
    val maxSplitBytes =
      FilePartition.maxSplitBytes(relation.sparkSession, selectedPartitions)

    val splitFiles = selectedPartitions
      .flatMap {
        partition =>
          partition.files.flatMap {
            file =>
              // getPath() is very expensive so we only want to call it once in this block:
              val filePath = file.getPath
              val isSplitable =
                relation.fileFormat.isSplitable(relation.sparkSession, relation.options, filePath)
              PartitionedFileUtil.splitFiles(
                sparkSession = relation.sparkSession,
                file = file,
                filePath = filePath,
                isSplitable = isSplitable,
                maxSplitBytes = maxSplitBytes,
                partitionValues = partition.values)
          }
      }
      .sortBy(_.length)(implicitly[Ordering[Long]].reverse)

    val totalCores = SparkResourceUtil.getTotalCores(relation.sparkSession.sessionState.conf)
    val fileCntPerPartition = math.ceil((splitFiles.size * 1.0) / totalCores).toInt
    val fileCntThreshold = relation.sparkSession.sessionState.conf
      .getConfString(
        CHBackendSettings.GLUTEN_CLICKHOUSE_FILES_PER_PARTITION_THRESHOLD,
        CHBackendSettings.GLUTEN_CLICKHOUSE_FILES_PER_PARTITION_THRESHOLD_DEFAULT
      )
      .toInt

    if (fileCntThreshold > 0 && fileCntPerPartition > fileCntThreshold) {
      getFilePartitionsByFileCnt(splitFiles, fileCntPerPartition)
    } else {
      FilePartition.getFilePartitions(relation.sparkSession, splitFiles, maxSplitBytes)
    }
  }

  private def genBucketedInputPartitionSeq(): Seq[InputPartition] = {
    val bucketSpec = relation.bucketSpec.get
    logInfo(s"Planning with ${bucketSpec.numBuckets} buckets")
    val filesGroupedToBuckets =
      SparkShimLoader.getSparkShims.filesGroupedToBuckets(selectedPartitions)

    val prunedFilesGroupedToBuckets = if (optionalBucketSet.isDefined) {
      val bucketSet = optionalBucketSet.get
      filesGroupedToBuckets.filter(f => bucketSet.get(f._1))
    } else {
      filesGroupedToBuckets
    }

    optionalNumCoalescedBuckets
      .map {
        numCoalescedBuckets =>
          logInfo(s"Coalescing to $numCoalescedBuckets buckets")
          val coalescedBuckets = prunedFilesGroupedToBuckets.groupBy(_._1 % numCoalescedBuckets)
          Seq.tabulate(numCoalescedBuckets) {
            bucketId =>
              val partitionedFiles = coalescedBuckets
                .get(bucketId)
                .map {
                  _.values.flatten.toArray
                }
                .getOrElse(Array.empty)
              FilePartition(bucketId, partitionedFiles)
          }
      }
      .getOrElse {
        Seq.tabulate(bucketSpec.numBuckets) {
          bucketId =>
            FilePartition(bucketId, prunedFilesGroupedToBuckets.getOrElse(bucketId, Array.empty))
        }
      }
  }

  /** Generate `Seq[FilePartition]` according to the file count */
  private def getFilePartitionsByFileCnt(
      partitionedFiles: Seq[PartitionedFile],
      fileCntPerPartition: Int): Seq[FilePartition] = {
    val partitions = new ArrayBuffer[FilePartition]
    val currentFiles = new ArrayBuffer[PartitionedFile]
    var currentFileCnt = 0L

    /** Close the current partition and move to the next. */
    def closePartition(): Unit = {
      if (currentFiles.nonEmpty) {
        // Copy to a new Array.
        val newPartition = FilePartition(partitions.size, currentFiles.toArray)
        partitions += newPartition
      }
      currentFiles.clear()
      currentFileCnt = 0L
    }

    partitionedFiles.foreach {
      file =>
        if (currentFileCnt >= fileCntPerPartition) {
          closePartition()
        }
        // Add the given file to the current partition.
        currentFileCnt += 1L
        currentFiles += file
    }
    closePartition()
    partitions.toSeq
  }

  private def toAttribute(colName: String): Option[Attribute] = {
    output.find(_.name == colName)
  }
}
