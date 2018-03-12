package com.alexb.utils;

import kafka.admin.AdminUtils;
import kafka.admin.RackAwareMode;
import kafka.admin.TopicCommand;
import kafka.server.KafkaConfig;
import kafka.server.KafkaServer;
import kafka.utils.TestUtils;
import kafka.utils.Time;
import kafka.utils.ZKStringSerializer$;
import kafka.utils.ZkUtils;
import kafka.zk.EmbeddedZookeeper;
import org.I0Itec.zkclient.ZkClient;
import org.apache.commons.io.FileUtils;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.collection.JavaConversions;
import scala.collection.Seq;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmbeddedKafkaTry {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddedKafkaTry.class);
    private final Path logPath;
    private final EmbeddedZookeeper zkServer;
    private final ZkClient zkClient;
    private final KafkaServer kafkaServer;
    private final ZkUtils zkUtils;
    private String BROKERHOST = "127.0.0.1";
    private String BROKERPORT = "9093";

    public EmbeddedKafkaTry() throws IOException {

        // setup Zookeeper
        zkServer = new EmbeddedZookeeper();
        String zkConnect = BROKERHOST + ":" + zkServer.port();
        zkClient = new ZkClient(zkConnect, 30000, 30000, ZKStringSerializer$.MODULE$);
        zkUtils = ZkUtils.apply(zkClient, false);

        // setup Broker
        logPath = Files.createTempDirectory("kafka-").toAbsolutePath();
        logger.info("Broker log path: {}", logPath);

        Properties brokerProps = new Properties();
        brokerProps.setProperty("zookeeper.connect", zkConnect);
        brokerProps.setProperty("broker.id", "0");
        brokerProps.setProperty("log.dirs", logPath.toString());
        brokerProps.setProperty("listeners", "PLAINTEXT://" + BROKERHOST + ":" + BROKERPORT);
        brokerProps.setProperty("offsets.topic.replication.factor", "1");
        KafkaConfig config = new KafkaConfig(brokerProps);
        Time mock = new kafka.utils.MockTime();
        kafkaServer = TestUtils.createServer(config, mock);
    }

    public void listTopics() {
        String[] arguments = new String[]{"--list"};
        TopicCommand.TopicCommandOptions opts = new TopicCommand.TopicCommandOptions(arguments);
        TopicCommand.listTopics(zkUtils, opts);
    }

    public Map<String, List<Integer>> topicsInfo() {
        Seq<String> seqTopics = zkUtils.getAllTopics();
        List<String> topics = JavaConversions.<String>seqAsJavaList(seqTopics);
        scala.collection.mutable.Map partitionsForTopics = zkUtils.getPartitionsForTopics(seqTopics);
        return JavaConversions.mapAsJavaMap(partitionsForTopics);
    }

    public static void main(String[] args) throws IOException {
        EmbeddedKafkaTry kafkaTry = new EmbeddedKafkaTry();
        kafkaTry.addTopic("topicA", 2);
        kafkaTry.addTopic("topicB", 2);

        logger.info("Available topics(partitions):");
        logger.info("{}", kafkaTry.topicsInfo());;

        logger.info("Available commands:");
        logger.info("*) send {count} {topic}");
        logger.info("*) exit");

        Pattern patternSend = Pattern.compile("^send\\s+(?<count>\\d+)\\s+(?<topic>\\w+)$");
        Scanner scanner = new Scanner(System.in);

        ExecutorService service = Executors.newFixedThreadPool(2);

        while (true) {
            String command = scanner.nextLine();

            if (command.startsWith("send")) {
                Matcher matcher = patternSend.matcher(command);
                if (matcher.matches()) {
                    int count = Integer.valueOf(matcher.group("count"));
                    String topic = matcher.group("topic");
                    CompletableFuture.runAsync(() -> kafkaTry.publish(count, topic), service);
                    continue;
                }
            }

            if (command.equals("exit")) {
                break;
            }

            logger.info("command [{}] is not recognized, please repeat...", command);
        }
        logger.info("stopping Kafka..");
        kafkaTry.stop();
        logger.info("Kafka stopped");

    }

    public void stop() throws IOException {
        kafkaServer.shutdown();
        zkClient.close();
        zkServer.shutdown();
        try {
            FileUtils.forceDelete(logPath.toFile());
            logger.info("temporary log folder deleted");
        }
        catch (Exception e) {
            logger.error("Unable to delete log folder {}", logPath.toString());
        }

    }

    public void addTopic(String topic, int partitions) {
        AdminUtils.createTopic(zkUtils, topic, partitions, 1, new Properties(), RackAwareMode.Disabled$.MODULE$);

        List<KafkaServer> servers = new ArrayList<>();
        servers.add(kafkaServer);
        scala.collection.Seq seq = scala.collection.JavaConversions.asScalaBuffer(servers);
        TestUtils.waitUntilMetadataIsPropagated(seq, topic, 0, 30000);
        logger.info("Added topic [{}], partitions: {}", topic, partitions);
    }

    public void publish(int count, String topic) {
        logger.info("Sending {} messages to {}", count, topic);

        Properties producerProps = new Properties();
        producerProps.setProperty("bootstrap.servers", BROKERHOST + ":" + BROKERPORT);
        producerProps.setProperty("key.serializer", "org.apache.kafka.common.serialization.IntegerSerializer");
        producerProps.setProperty("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");

        try (KafkaProducer<Integer, byte[]> producer = new KafkaProducer<>(producerProps)) {
            for (int i = 0; i < count; i++) {
                String message = UUID.randomUUID().toString();
                ProducerRecord<Integer, byte[]> data = new ProducerRecord<>(topic, i, message.getBytes(StandardCharsets.UTF_8));
                producer.send(data);
            }
        }
    }
}
