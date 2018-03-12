package com.alexb;

import org.apache.spark.streaming.scheduler.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaStreamingListener implements StreamingListener {

    private static final Logger logger = LoggerFactory.getLogger(KafkaStreamingListener.class);

    @Override
    public void onStreamingStarted(StreamingListenerStreamingStarted arg) {
        logger.info("onStreamingStarted productPrefix:{}", arg.productPrefix());
    }

    @Override
    public void onReceiverStarted(StreamingListenerReceiverStarted receiverStarted) {
        logger.info("onReceiverStarted streamId:{}", receiverStarted.receiverInfo().streamId());
    }

    @Override
    public void onReceiverStopped(StreamingListenerReceiverStopped receiverStopped) {
        logger.info("onReceiverStopped lastError: {}", receiverStopped.receiverInfo().lastErrorMessage());
    }

    @Override
    public void onReceiverError(StreamingListenerReceiverError receiverError) {
        logger.info("onReceiverError lastError: {}", receiverError.receiverInfo().lastErrorMessage());
    }

    @Override
    public void onBatchSubmitted(StreamingListenerBatchSubmitted batchSubmitted) {
        logger.info("onBatchSubmitted numRecords: {}", batchSubmitted.batchInfo().numRecords());
    }

    @Override
    public void onBatchStarted(StreamingListenerBatchStarted batchStarted) {
        logger.info("onBatchStarted numRecords: {}", batchStarted.batchInfo().numRecords());
    }

    @Override
    public void onBatchCompleted(StreamingListenerBatchCompleted batchCompleted) {
        logger.info("onBatchCompleted numRecords: {}", batchCompleted.batchInfo().numRecords());
    }

    @Override
    public void onOutputOperationStarted(StreamingListenerOutputOperationStarted outputOperationStarted) {
        logger.info("onOutputOperationStarted outputOperationInfo: {}", outputOperationStarted.outputOperationInfo());
    }

    @Override
    public void onOutputOperationCompleted(StreamingListenerOutputOperationCompleted outputOperationCompleted) {
        logger.info("onOutputOperationCompleted outputOperationInfo: {}", outputOperationCompleted.outputOperationInfo());
    }

}
