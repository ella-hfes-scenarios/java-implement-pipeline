package com.ella.hfes.pipeline;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Collects metrics from pipeline execution.
 */
public interface PipelineMetrics {

    /**
     * Returns the total pipeline execution time.
     */
    Duration getTotalDuration();

    /**
     * Returns execution duration for each stage by name.
     */
    Map<String, Duration> getStageDurations();

    /**
     * Returns the number of stages that were executed.
     */
    int getStageCount();
}
