/*
 * Gopinathan Munappy, 31/07/2017
 * Bala,			 17/07/2017 
 */
package com.test

import com.test.config.ConfigurationFactory
import com.test.config.objects.Config

import com.test.config.ConfigurationFactory
import com.test.utils.JsonUtils
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.log4j.Logger
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.types._
import org.apache.spark.sql.{Row, SQLContext, SaveMode, SparkSession}
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe
import org.apache.spark.streaming.kafka010.KafkaUtils
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferBrokers
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import org.apache.spark.streaming.{Seconds, StreamingContext}

import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.sql._
import org.apache.spark._
import org.apache.spark.sql.SparkSession

import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._
import org.apache.spark.sql._

import org.apache.spark.mllib.evaluation.RegressionMetrics
import org.apache.spark.mllib.evaluation.BinaryClassificationMetrics
import org.apache.spark.ml.tuning.{ ParamGridBuilder, CrossValidator }
import org.apache.spark.ml.{ Pipeline, PipelineStage }
import org.apache.spark.ml.classification.RandomForestClassifier
import org.apache.spark.ml.classification.RandomForestClassificationModel
import org.apache.spark.ml.evaluation.MulticlassClassificationEvaluator
import org.apache.spark.ml.evaluation.BinaryClassificationEvaluator
import org.apache.spark.ml.feature.StringIndexer
import org.apache.spark.ml.feature.VectorAssembler
import org.apache.spark.ml.Estimator

import org.apache.log4j.Logger;

import java.io._

object RandomForestRealtime {
				case class kafkacredit(
					credibility: Double,
					balance: Double,
					duration: Double,
					history: Double,
					purpose: Double,
					amount:  Double,
					savings: Double,
					instPercent: Double,
					guarantors: Double,
					assets: Double,
					concCredit: Double,
					credits: Double,
					sexMarried: Double,
					employment: Double,
					occupation: Double,
					residenceDuration: Double,
					age: Double,
					apartment: Double,
					dependents: Double,
					hasPhone: Double,
					foreignWorker: Double
					)
					
				case class Credit(
					creditability: Double,
					balance: Double,
					duration: Double,
					history: Double,
					purpose: Double,
					amount:  Double,
					savings: Double,
					employment: Double,
					instPercent: Double,
					sexMarried: Double,
					guarantors: Double,
					residenceDuration: Double,
					assets: Double,
					age: Double,
					concCredit: Double,
					apartment: Double,
					credits: Double,
					occupation: Double,
					dependents: Double,
					hasPhone: Double,
					foreign: Double
					)		
	
				var realTimeMessage: String = "" 
				val modelSavePath: String = "/home/cloudera/Real-Time-Credit-Risk-Analysis/save/"
				
				private[this] lazy val logger = Logger.getLogger(getClass)
				private[this] val config = ConfigurationFactory.load()
			    
				val spark = SparkSession.builder
				.appName("Credit Prediction")
				.master("local[*]")
				.getOrCreate
					
				val sqlContext= new org.apache.spark.sql.SQLContext(spark.sparkContext)
				import sqlContext._
				import sqlContext.implicits._
//--------------------------------------------------------------------------------------------------------------------------//
			// Parsing Credit data
			def parseCredit(line: Array[Double]): Credit = {
				Credit(
				line(0),
				line(1) - 1,
				line(2),
				line(3),
				line(4),
				line(5),
				line(6) - 1,
				line(7) - 1,
				line(8),
				line(9) - 1,
				line(10) - 1,
				line(11) - 1,
				line(12) - 1,
				line(13),
				line(14) - 1,
				line(15) - 1,
				line(16) - 1,
				line(17) - 1,
				line(18) - 1,
				line(19) - 1,
				line(20) - 1)
				}
	
//----------------------------------------------------------------------------------------------------------//				
			// Parsing Credit RDD
			def parseRDD(rdd: RDD[String]): RDD[Array[Double]] = {
				rdd.map(_.split(",")).map(_.map(_.toDouble))
				}
//----------------------------------------------------------------------------------------------------------//				
			// Get a dataframe from a string
			def getDataFrameFromString(creditRealTimeRDD: org.apache.spark.rdd.RDD[String]): org.apache.spark.sql.Dataset[Row] = {val creditRealTimeDF = parseRDD(creditRealTimeRDD).map(parseCredit).toDF().cache()
				return creditRealTimeDF
				}
//----------------------------------------------------------------------------------------------------------//				
			// Feature engineering
			def getFeature(creditRealTimeDF: org.apache.spark.sql.Dataset[Row]): org.apache.spark.sql.Dataset[Row] = {
				println("3A")
				val featureCols = Array("balance", "duration", "history", "purpose", "amount",
					"savings", "employment", "instPercent", "sexMarried", "guarantors",
					"residenceDuration", "assets", "age", "concCredit", "apartment",
					"credits", "occupation", "dependents", "hasPhone", "foreign")
				println("3B")
				val assembler = new VectorAssembler().setInputCols(featureCols).setOutputCol("features")
				println("3C")
				val df2 = assembler.transform(creditRealTimeDF)
				println("3D")
				//df2.show
				println("3E")
				val labelIndexer = new StringIndexer().setInputCol("creditability").setOutputCol("label")
				println("3F")
				val df3 = labelIndexer.fit(df2).transform(df2)
				println("3G")
				//df3.show
				println("3H")
				return df3
				}
//----------------------------------------------------------------------------------------------------------//				
			// Function to predict real time credit data
			def predictRealTime(realTimeData: org.apache.spark.sql.Dataset[Row], model: org.apache.spark.ml.classification.RandomForestClassificationModel) {
				
				val classifier = new RandomForestClassifier()
				.setImpurity("gini")
				.setMaxDepth(3).setNumTrees(20)
				.setFeatureSubsetStrategy("auto")
				.setSeed(5043)
				
				val evaluator = new BinaryClassificationEvaluator().setLabelCol("label")
				val predictions = model.transform(realTimeData)

				val accuracy = evaluator.evaluate(predictions)
				println("Real Time Accuracy before pipeline fitting" + accuracy)

/*				val rm = new RegressionMetrics(predictions.select("prediction", "label").rdd.map(x =>
				(x(0).asInstanceOf[Double], x(1).asInstanceOf[Double]))
				)
				
				println("MSE: " + rm.meanSquaredError)
				println("MAE: " + rm.meanAbsoluteError)
				println("RMSE Squared: " + rm.rootMeanSquaredError)
				println("R Squared: " + rm.r2)
				println("Explained Variance: " + rm.explainedVariance + "\n")

				val paramGrid = new ParamGridBuilder()
				.addGrid(classifier.maxBins, Array(25, 31))
				.addGrid(classifier.maxDepth, Array(5, 10))
				.addGrid(classifier.numTrees, Array(20, 60))
				.addGrid(classifier.impurity, Array("entropy", "gini"))
				.build()

				val steps: Array[PipelineStage] = Array(classifier)
				val pipeline = new Pipeline().setStages(steps)

				val cv = new CrossValidator()
				.setEstimator(pipeline)
				.setEvaluator(evaluator)
				.setEstimatorParamMaps(paramGrid)
				.setNumFolds(10)
				
				//val trainingData: Array[String] = getData()
				val pipelineFittedModel = cv.fit(trainingData)
				
				val predictions2 = pipelineFittedModel.transform(realTimeData)
				val accuracy2 = evaluator.evaluate(predictions2)
				println("accuracy after pipeline fitting" + accuracy2)

			println(pipelineFittedModel.bestModel.asInstanceOf[org.apache.spark.ml.PipelineModel].stages(0))

				pipelineFittedModel.bestModel
				.asInstanceOf[org.apache.spark.ml.PipelineModel]
				.stages(0)
				.extractParamMap

				val rm2 = new RegressionMetrics(predictions2.select("prediction", "label").rdd.map(x =>
				(x(0).asInstanceOf[Double], x(1).asInstanceOf[Double])))

				println("MSE: " + rm2.meanSquaredError)
				println("MAE: " + rm2.meanAbsoluteError)
				println("RMSE Squared: " + rm2.rootMeanSquaredError)
				println("R Squared: " + rm2.r2)
				println("Explained Variance: " + rm2.explainedVariance + "\n")
		
				// Calculate Binary Classification Metrics**
				val predictionAndLabels =predictions.select("prediction", "label")
				.rdd.map(x => (x(0).asInstanceOf[Double], x(1).asInstanceOf[Double]))
	
				val metrics = new BinaryClassificationMetrics(predictionAndLabels)
				// A Precision-Recall curve plots (precision, recall) points for different threshold values, 
				// while a receiver operating 
				// characteristic, or ROC, curve plots (recall, false positive rate) points.
				println("Area under the precision-recall curve: " + metrics.areaUnderPR)
				println("Area under the receiver operating characteristic (ROC) curve : " + metrics.areaUnderROC)
*/				
				}	// predictRealTime
//----------------------------------------------------------------------------------------------------------//				
				// main method
				def main(args: Array[String]): Unit = {
 			
					val sleep: Int = 1000
					var key:Int = 0
					var keyMsg:String = ""
				
					val streaming = new StreamingContext(spark.sparkContext, Seconds(config.getStreaming.getWindow))

					val servers = config.getProducer.getHosts.toArray.mkString(",")
					
					val params = Map[String, Object](
					"bootstrap.servers" -> servers,
					"key.deserializer" -> classOf[StringDeserializer],
					"value.deserializer" -> classOf[StringDeserializer],
					"auto.offset.reset" -> "latest",
					"group.id" -> "dashboard",
					"enable.auto.commit" -> (false: java.lang.Boolean)
					)

				// topic names which will be read
					val kafkaTopics = Array(config.getProducer.getKafkaTopic)
		
				// create kafka direct stream object
					val stream = KafkaUtils.createDirectStream[String, String](
					streaming, PreferConsistent, Subscribe[String, String](kafkaTopics, params))
			
				// just alias for simplicity
					type Record = ConsumerRecord[String, String]
					
					stream.foreachRDD((rdd: RDD[Record]) => {
					
				// convert string to PoJo and generate rows as tuple group
					val pairs = rdd.map(row => (row.value()))
				
					val kafkaRDD = pairs.map(line  => line.split(',')).map(line => kafkacredit(
					line(0).replaceAll("kafkacredit\\(" , "").trim.toDouble,
					line(1).trim.toDouble,
					line(2).trim.toDouble,
					line(3).trim.toDouble,
					line(4).trim.toDouble,
					line(5).trim.toDouble,
					line(6).trim.toDouble,
					line(7).trim.toDouble,
					line(8).trim.toDouble,
					line(9).trim.toDouble,
					line(10).trim.toDouble,
					line(11).trim.toDouble,
					line(12).trim.toDouble,
					line(13).trim.toDouble,
					line(14).trim.toDouble,
					line(15).trim.toDouble,
					line(16).trim.toDouble,
					line(17).trim.toDouble,
					line(18).trim.toDouble,
					line(19).trim.toDouble,
					line(20).replaceAll("\\):" , "").trim.toDouble)) 
					
					val sqlContext= new org.apache.spark.sql.SQLContext(spark.sparkContext)
					import sqlContext.implicits._
										
					// Writing to Kafka for downstream apps
					val parsedData = kafkaRDD.foreach(line => {
					val parts = (line + ":")
					var msg: String = parts.replaceAll("kafkacredit\\(" , "").trim
					realTimeMessage = msg.replaceAll("\\):" , "").trim
					println("Real Time Data : " + realTimeMessage)
					//val args : Array[String] = Array(realTimeMessage, "")
					//CreditMLModel.main(args)
					//println("Back here ")
//------------------------------------------------------------------------------------------------------------------//							// Real Time: Get a RDD from real time message string

					val arrayString: Array[String] = realTimeMessage.split(",")
					val list: List[String] = arrayString.toList
					//val realTimeList = List(realTimeMessage)
					println("1")
					val creditRealTimeRDD = (spark.sparkContext).parallelize(list) //.map(_.toDouble)
					//val creditRealTimeRDD = (spark.sparkContext).textFile(realTimeMessage)
					println("2")
					//creditRealTimeRDD.collect
					println("2A")
					//	Get a dataframe from a RDD to get real time data 
					val creditRealTimeDF: org.apache.spark.sql.Dataset[Row] = getDataFrameFromString(creditRealTimeRDD)
					println("3")
					// Do feature engineering on real time data and get test data for predictions
					val realTimeData: org.apache.spark.sql.Dataset[Row] = getFeature(creditRealTimeDF)
					println("3I")
					println("4")
					val sameModel = RandomForestClassificationModel.load(modelSavePath)
					println("5")			            
					// Get prediction on real time data
					predictRealTime(realTimeData, sameModel)	
//-------------------------------------------------------------------------------------------------------------------//
					}) 	//parsedData.foreach(println)
					
				})  // foreachRDD
		
				// create streaming context and submit streaming jobs
				streaming.start()

				// wait to killing signals etc.
				streaming.awaitTermination()
		}   //main

   }   	//object