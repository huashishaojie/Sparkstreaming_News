package com.spark.test

import java.sql.DriverManager

import org.apache.spark.sql.SparkSession
import org.apache.spark.streaming.{Seconds, StreamingContext}



object TestStreaming {

  def main(args: Array[String]): Unit = {

    val spark  = SparkSession.builder()
      .master("local[3]")
      .appName("streaming").getOrCreate()

    val sc =spark.sparkContext
    val ssc = new StreamingContext(sc, Seconds(5))
    val lines = ssc.socketTextStream("172.18.74.220", 9999)
    val words = lines.flatMap(_.split(" ")).map(word => (word, 1)).reduceByKey(_ + _)

    words.foreachRDD(rdd => rdd.foreachPartition(line => {
         Class.forName("com.mysql.jdbc.Driver")
         val conn = DriverManager
           .getConnection("jdbc:mysql://172.18.74.221:3306/test","root","123456")
         try{
            for(row <- line){
              val sql = "insert into webCount(titleName,count)values('"+row._1+"',"+row._2+")"
              conn.prepareStatement(sql).executeUpdate()
            }
         }finally {
            conn.close()
         }
    }))

     //words.print()
      ssc.start()
      ssc.awaitTermination()
  }

}
