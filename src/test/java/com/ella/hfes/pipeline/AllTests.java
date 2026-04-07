package com.ella.hfes.pipeline;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

class AllTests {

    // ---- Basic chaining ----

    @Test
    void singleStage() throws Exception {
        var pipeline = PipelineBuilder.<String>create()
                .addStage("upper", s -> CompletableFuture.completedFuture(s.toUpperCase()))
                .build();
        assertEquals("HELLO", pipeline.execute("hello").get());
    }

    @Test
    void twoStages() throws Exception {
        var pipeline = PipelineBuilder.<String>create()
                .addStage("trim", s -> CompletableFuture.completedFuture(s.trim()))
                .addStage("length", s -> CompletableFuture.completedFuture(s.length()))
                .build();
        assertEquals(5, pipeline.execute("  hello  ").get());
    }

    @Test
    void fiveStageChain() throws Exception {
        var pipeline = PipelineBuilder.<Integer>create()
                .addStage("s1", n -> CompletableFuture.completedFuture(n + 1))
                .addStage("s2", n -> CompletableFuture.completedFuture(n * 2))
                .addStage("s3", n -> CompletableFuture.completedFuture(n + 3))
                .addStage("s4", n -> CompletableFuture.completedFuture(n * 10))
                .addStage("s5", n -> CompletableFuture.completedFuture(n - 1))
                .build();
        // 1 -> 2 -> 4 -> 7 -> 70 -> 69
        assertEquals(69, pipeline.execute(1).get());
    }

    // ---- Error propagation ----

    @Test
    void errorInMiddleStage() {
        var pipeline = PipelineBuilder.<String>create()
                .addStage("ok", s -> CompletableFuture.completedFuture(s))
                .addStage("fail", s -> CompletableFuture.failedFuture(new RuntimeException("boom")))
                .addStage("unreached", s -> CompletableFuture.completedFuture(s))
                .build();
        var future = pipeline.execute("input");
        var ex = assertThrows(ExecutionException.class, future::get);
        assertTrue(ex.getCause().getMessage().contains("boom"));
    }

    // ---- Timeout enforcement ----

    @Test
    void stageTimeoutEnforced() {
        var pipeline = PipelineBuilder.<String>create()
                .addStage("slow", s -> {
                    return CompletableFuture.supplyAsync(() -> {
                        try { Thread.sleep(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                        return s;
                    });
                }, Duration.ofMillis(200))
                .build();

        var future = pipeline.execute("input");
        var ex = assertThrows(ExecutionException.class, () -> future.get(3, TimeUnit.SECONDS));
        assertTrue(ex.getCause() instanceof TimeoutException ||
                   ex.getCause() instanceof java.util.concurrent.TimeoutException ||
                   ex.getCause().getMessage().toLowerCase().contains("timeout"),
                "Should fail with timeout, got: " + ex.getCause());
    }

    // ---- Retry logic ----

    @Test
    void retryOnFailure() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        var pipeline = PipelineBuilder.<String>create()
                .addStageWithRetry("flaky", s -> {
                    if (attempts.incrementAndGet() < 3) {
                        return CompletableFuture.failedFuture(new RuntimeException("transient"));
                    }
                    return CompletableFuture.completedFuture("success");
                }, 3)
                .build();

        String result = pipeline.execute("input").get(10, TimeUnit.SECONDS);
        assertEquals("success", result);
        assertEquals(3, attempts.get());
    }

    @Test
    void retryExhaustedFails() {
        AtomicInteger attempts = new AtomicInteger();
        var pipeline = PipelineBuilder.<String>create()
                .addStageWithRetry("alwaysFails", s -> {
                    attempts.incrementAndGet();
                    return CompletableFuture.failedFuture(new RuntimeException("permanent"));
                }, 2)
                .build();

        assertThrows(ExecutionException.class, () -> pipeline.execute("input").get(10, TimeUnit.SECONDS));
        assertTrue(attempts.get() >= 3); // 1 initial + 2 retries
    }

    // ---- Parallel fan-out/fan-in ----

    @Test
    void parallelStages() throws Exception {
        List<Stage<Integer, Integer>> stages = List.of(
                n -> CompletableFuture.completedFuture(n * 2),
                n -> CompletableFuture.completedFuture(n * 3),
                n -> CompletableFuture.completedFuture(n * 4)
        );

        var pipeline = PipelineBuilder.<Integer>create()
                .addParallelStage("fan-out", stages)
                .build();

        List<Integer> results = pipeline.execute(10).get(5, TimeUnit.SECONDS);
        assertEquals(3, results.size());
        assertTrue(results.contains(20));
        assertTrue(results.contains(30));
        assertTrue(results.contains(40));
    }

    // ---- Metrics ----

    @Test
    void metricsCollected() throws Exception {
        var pipeline = PipelineBuilder.<String>create()
                .addStage("stage1", s -> CompletableFuture.completedFuture(s))
                .addStage("stage2", s -> CompletableFuture.completedFuture(s))
                .build();

        pipeline.execute("test").get();
        var metrics = pipeline.getMetrics();
        assertNotNull(metrics);
        assertEquals(2, metrics.getStageCount());
        assertNotNull(metrics.getTotalDuration());
        assertTrue(metrics.getStageDurations().containsKey("stage1"));
        assertTrue(metrics.getStageDurations().containsKey("stage2"));
    }

    @Test
    void metricsDurationsArePositive() throws Exception {
        var pipeline = PipelineBuilder.<String>create()
                .addStage("delay", s -> CompletableFuture.supplyAsync(() -> {
                    try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    return s;
                }))
                .build();

        pipeline.execute("test").get(5, TimeUnit.SECONDS);
        var duration = pipeline.getMetrics().getStageDurations().get("delay");
        assertNotNull(duration);
        assertTrue(duration.toMillis() >= 30, "Duration should reflect actual execution time");
    }

    // ---- Cancellation ----

    @Test
    void cancelledFuture() {
        var pipeline = PipelineBuilder.<String>create()
                .addStage("slow", s -> {
                    CompletableFuture<String> f = new CompletableFuture<>();
                    // Never completes
                    return f;
                })
                .build();

        CompletableFuture<?> future = pipeline.execute("input");
        future.cancel(true);
        assertTrue(future.isCancelled() || future.isDone());
    }

    // ---- Null input handling ----

    @Test
    void nullInputHandled() {
        var pipeline = PipelineBuilder.<String>create()
                .addStage("check", s -> {
                    if (s == null) return CompletableFuture.failedFuture(
                            new NullPointerException("input is null"));
                    return CompletableFuture.completedFuture(s);
                })
                .build();

        assertThrows(ExecutionException.class, () -> pipeline.execute(null).get());
    }

    // ---- Stage ordering guaranteed ----

    @Test
    void stageOrderPreserved() throws Exception {
        StringBuilder order = new StringBuilder();
        var pipeline = PipelineBuilder.<String>create()
                .addStage("first", s -> {
                    order.append("1");
                    return CompletableFuture.completedFuture(s);
                })
                .addStage("second", s -> {
                    order.append("2");
                    return CompletableFuture.completedFuture(s);
                })
                .addStage("third", s -> {
                    order.append("3");
                    return CompletableFuture.completedFuture(s);
                })
                .build();

        pipeline.execute("test").get();
        assertEquals("123", order.toString());
    }

    // ---- Multiple executions ----

    @Test
    void pipelineCanBeExecutedMultipleTimes() throws Exception {
        AtomicInteger count = new AtomicInteger();
        var pipeline = PipelineBuilder.<Integer>create()
                .addStage("increment", n -> {
                    count.incrementAndGet();
                    return CompletableFuture.completedFuture(n + 1);
                })
                .build();

        assertEquals(2, pipeline.execute(1).get());
        assertEquals(6, pipeline.execute(5).get());
        assertEquals(2, count.get());
    }
}
