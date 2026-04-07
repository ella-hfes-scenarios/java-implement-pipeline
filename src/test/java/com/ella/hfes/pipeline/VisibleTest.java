package com.ella.hfes.pipeline;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

class VisibleTest {

    @Test
    void singleStage() throws Exception {
        Pipeline<String, String> pipeline = PipelineBuilder.<String>create()
                .addStage("upper", input -> CompletableFuture.completedFuture(input.toUpperCase()))
                .build();

        String result = pipeline.execute("hello").get();
        assertEquals("HELLO", result);
    }

    @Test
    void twoStageChain() throws Exception {
        Pipeline<String, Integer> pipeline = PipelineBuilder.<String>create()
                .addStage("trim", input -> CompletableFuture.completedFuture(input.trim()))
                .addStage("parse", input -> CompletableFuture.completedFuture(Integer.parseInt(input)))
                .build();

        int result = pipeline.execute("  42  ").get();
        assertEquals(42, result);
    }

    @Test
    void errorPropagation() {
        Pipeline<String, Integer> pipeline = PipelineBuilder.<String>create()
                .addStage("fail", input -> CompletableFuture.<Integer>failedFuture(
                        new RuntimeException("stage error")))
                .build();

        var future = pipeline.execute("input");
        assertThrows(ExecutionException.class, future::get);
    }

    @Test
    void threeStageChain() throws Exception {
        Pipeline<Integer, String> pipeline = PipelineBuilder.<Integer>create()
                .addStage("double", n -> CompletableFuture.completedFuture(n * 2))
                .addStage("add10", n -> CompletableFuture.completedFuture(n + 10))
                .addStage("toString", n -> CompletableFuture.completedFuture("Result: " + n))
                .build();

        String result = pipeline.execute(5).get();
        assertEquals("Result: 20", result);
    }

    @Test
    void metricsAvailableAfterExecution() throws Exception {
        Pipeline<String, String> pipeline = PipelineBuilder.<String>create()
                .addStage("echo", input -> CompletableFuture.completedFuture(input))
                .build();

        pipeline.execute("test").get();
        PipelineMetrics metrics = pipeline.getMetrics();
        assertNotNull(metrics);
        assertEquals(1, metrics.getStageCount());
    }

    @Test
    void metricsTrackStageName() throws Exception {
        Pipeline<String, String> pipeline = PipelineBuilder.<String>create()
                .addStage("myStage", input -> CompletableFuture.completedFuture(input))
                .build();

        pipeline.execute("test").get();
        assertTrue(pipeline.getMetrics().getStageDurations().containsKey("myStage"));
    }
}
