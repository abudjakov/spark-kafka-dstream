package com.alexb;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.BytesDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.spark.SparkConf;
import org.apache.spark.TaskContext;
import org.apache.spark.streaming.Durations;
import org.apache.spark.streaming.api.java.JavaInputDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.apache.spark.streaming.kafka010.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class KafkaDStream {

    private static final Logger logger = LoggerFactory.getLogger(KafkaDStream.class);

    public KafkaDStream() throws InterruptedException {
        SparkConf sparkConf = new SparkConf()
                .setMaster("local[*]")
                .setAppName("KafkaDStream")
                .set("spark.streaming.blockInterval", "500ms")
                .set("spark.streaming.kafka.maxRatePerPartition", "1000")
                .set("spark.streaming.backpressure.enabled", "true")
                .set("spark.streaming.stopGracefullyOnShutdown", "true");


        JavaStreamingContext ssc = new JavaStreamingContext(sparkConf, Durations.seconds(10));

        Map<String, Object> kafkaParams = new HashMap<>();
        kafkaParams.put("bootstrap.servers", "localhost:9093");
        kafkaParams.put("key.deserializer", StringDeserializer.class);
        kafkaParams.put("value.deserializer", BytesDeserializer.class);
        kafkaParams.put("group.id", "dstream-a");
//        kafkaParams.put("auto.offset.reset", "latest");
        kafkaParams.put("auto.offset.reset", "earliest"); // to check offset logic
        kafkaParams.put("enable.auto.commit", false);

        Set<String> topicsSet = new HashSet<>(Arrays.asList("topicA", "topicB"));

        Map<TopicPartition, Long> fromOffsets = new HashMap<>();

        // TODO: populate offsets for listed topics from DB, offset + 1

       /* JavaInputDStream<ConsumerRecord<String, String>> stream = KafkaUtils.createDirectStream(
                ssc,
                LocationStrategies.PreferConsistent(),
                ConsumerStrategies.<String, String>Assign(fromOffsets.keySet(), kafkaParams, fromOffsets)
        );*/

        JavaInputDStream<ConsumerRecord<String, String>> stream = KafkaUtils.createDirectStream(
                ssc,
                LocationStrategies.PreferConsistent(),
                ConsumerStrategies.Subscribe(topicsSet, kafkaParams));

        stream.foreachRDD(rdd -> {
            OffsetRange[] offsetRanges = ((HasOffsetRanges) rdd.rdd()).offsetRanges();

            rdd.foreachPartition(consumerRecords -> {
                OffsetRange o = offsetRanges[TaskContext.get().partitionId()];
                logger.info("{} : {} : {} -> {}", o.topic(), o.partition(), o.fromOffset(), o.untilOffset());

                consumerRecords.forEachRemaining(r -> {
                    logger.info("record: {}", r.value());
                });

            });

            // TODO: save results
            // TODO: update offset set offset = untilOffset for topic, partition, fromOffset
            // TODO: assert that offsets were updated correctly

            // commit offset to kafka otherwise we can't use kafka monitoring tool
            ((CanCommitOffsets) stream.inputDStream()).commitAsync(offsetRanges);
        });

        ssc.addStreamingListener(new KafkaStreamingListener());
        ssc.start();

        try {
            ssc.awaitTermination();
            logger.info("Streaming has been stopped");
        }
        catch (Exception e) {
            logger.error("Unable to stop streaming", e);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        new KafkaDStream();
    }
}
