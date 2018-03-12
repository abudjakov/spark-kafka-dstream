package com.alexb;

import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.util.LongAccumulator;

public class JavaWordsCounter {

    private static volatile LongAccumulator instance = null;

    public static LongAccumulator getInstance(JavaSparkContext jsc) {
        if (instance == null) {
            synchronized (JavaWordsCounter.class) {
                if (instance == null) {
                    instance = jsc.sc().longAccumulator("JavaWordsCounter");
                }
            }
        }
        return instance;
    }
}
