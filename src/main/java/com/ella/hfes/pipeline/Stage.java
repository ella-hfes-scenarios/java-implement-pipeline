package com.ella.hfes.pipeline;

import java.util.concurrent.CompletableFuture;

/**
 * A single processing stage in a pipeline.
 *
 * @param <I> input type
 * @param <O> output type
 */
@FunctionalInterface
public interface Stage<I, O> {
    /**
     * Processes the input and returns a future result.
     */
    CompletableFuture<O> process(I input);
}
