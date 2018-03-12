package com.alexb;

import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.broadcast.Broadcast;

import java.util.Arrays;
import java.util.List;

public class JavaWordBlacklist {

    private static volatile Broadcast<List<String>> instance = null;

    public static Broadcast<List<String>> getInstance(JavaSparkContext jsc) {
        if (instance == null) {
            synchronized (JavaWordBlacklist.class) {
                if (instance == null) {
                    List<String> wordBlacklist = Arrays.asList("3", "5");
                    instance = jsc.broadcast(wordBlacklist);
                }
            }
        }
        return instance;
    }
}
