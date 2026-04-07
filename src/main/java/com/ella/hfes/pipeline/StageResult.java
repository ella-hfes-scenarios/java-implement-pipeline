package com.ella.hfes.pipeline;

import java.time.Duration;

/**
 * Wraps the result of a stage execution with timing information.
 *
 * @param <T> the result type
 */
public record StageResult<T>(T value, String stageName, Duration duration) {
}
