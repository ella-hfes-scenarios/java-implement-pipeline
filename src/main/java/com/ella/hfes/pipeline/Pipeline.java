package com.ella.hfes.pipeline;

import java.util.concurrent.CompletableFuture;

/**
 * An async processing pipeline that chains stages together.
 *
 * @param <I> pipeline input type
 * @param <O> pipeline output type
 */
public interface Pipeline<I, O> {
    /**
     * Executes the pipeline with the given input.
     *
     * @param input the pipeline input
     * @return a CompletableFuture containing the final output
     */
    CompletableFuture<O> execute(I input);

    /**
     * Returns metrics collected during the last execution.
     */
    PipelineMetrics getMetrics();
}
