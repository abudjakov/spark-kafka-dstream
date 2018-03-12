package com.alexb;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.StorageLevels;
import org.apache.spark.broadcast.Broadcast;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.streaming.Durations;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.spark.streaming.api.java.JavaReceiverInputDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.apache.spark.util.LongAccumulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/*
 * Run NetCat
 * nc -lk 1234
 * */
public final class NetCatSqlWordCount {

    private static final Pattern SPACE = Pattern.compile(" ");
    private static final Logger logger = LoggerFactory.getLogger(NetCatSqlWordCount.class);

    public NetCatSqlWordCount() throws InterruptedException {
        SparkConf sparkConf = new SparkConf()
                .setMaster("local[*]")
                .setAppName("NetCatSqlWordCount")
                .set("spark.streaming.stopGracefullyOnShutdown", "true");

        JavaStreamingContext ssc = new JavaStreamingContext(sparkConf, Durations.seconds(10));

        JavaReceiverInputDStream<String> lines = ssc.socketTextStream("localhost", 1234, StorageLevels.MEMORY_AND_DISK_SER);
        JavaDStream<String> words = lines.flatMap(x -> Arrays.asList(SPACE.split(x)).iterator());

        words.foreachRDD((rdd, time) -> {
            logger.info("partitioner: {}, partitions: {}", rdd.partitioner(), rdd.partitions());
            SparkSession spark = JavaSparkSessionSingleton.getInstance(rdd.context().getConf());

            Broadcast<List<String>> blacklist = JavaWordBlacklist.getInstance(JavaSparkContext.fromSparkContext(rdd.context()));
            LongAccumulator longAccumulator = JavaWordsCounter.getInstance(JavaSparkContext.fromSparkContext(rdd.context()));

            JavaRDD<JavaRecord> rowRDD = rdd.map(word -> {
                JavaRecord record = new JavaRecord();
                record.setWord(word);
                if (blacklist.value().contains(word)) {
                    record.setBlackListed(true);
                    longAccumulator.add(1);
                }
                return record;
            });
            Dataset<Row> wordsDataFrame = spark.createDataFrame(rowRDD, JavaRecord.class);

            wordsDataFrame.createOrReplaceTempView("words");

            Dataset<Row> wordCountsDataFrame = spark.sql("select word, blackListed, count(*) as total from words group by word, blackListed");
            wordCountsDataFrame.show();
            logger.info("========= {} =========", time);
            logger.info("blackListed count: {}", longAccumulator.value());
        });

        ssc.start();
        ssc.awaitTermination();
    }

    public static void main(String[] args) throws Exception {
        new NetCatSqlWordCount();
    }


}
