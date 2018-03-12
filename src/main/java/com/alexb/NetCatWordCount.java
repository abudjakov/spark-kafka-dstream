package com.alexb;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.StorageLevels;
import org.apache.spark.streaming.Durations;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.spark.streaming.api.java.JavaPairDStream;
import org.apache.spark.streaming.api.java.JavaReceiverInputDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import scala.Tuple2;

import java.util.Arrays;
import java.util.regex.Pattern;

/*
* Run NetCat
* nc -lk 1234
* */
public final class NetCatWordCount {


    private static final Pattern SPACE = Pattern.compile(" ");

    public NetCatWordCount() throws InterruptedException {
        SparkConf sparkConf = new SparkConf()
                .setMaster("local[*]")
                .setAppName("NetCatWordCount");

        JavaStreamingContext ssc = new JavaStreamingContext(sparkConf, Durations.seconds(30));

        JavaReceiverInputDStream<String> lines = ssc.socketTextStream("localhost", 1234, StorageLevels.MEMORY_AND_DISK_SER);
        JavaDStream<String> words = lines.flatMap(x -> Arrays.asList(SPACE.split(x)).iterator());
        JavaPairDStream<String, Integer> wordCounts = words.mapToPair(s -> new Tuple2<>(s, 1))
                .reduceByKey((i1, i2) -> i1 + i2);

        wordCounts.print();
        ssc.start();
        ssc.awaitTermination();
    }


    public static void main(String[] args) throws Exception {
        new NetCatWordCount();
    }


}