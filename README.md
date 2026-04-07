# Java Async Pipeline — CompletableFuture Processing

## Overview
Implement an async data processing pipeline using `CompletableFuture` that supports stage chaining, error propagation, timeouts, retries, and parallel execution.

## Your Task
Complete `PipelineBuilder.java` to build pipelines from `Stage` instances.

Requirements:
- Chain stages: output of one feeds into the next
- Error propagation: if a stage fails, the pipeline fails with that error
- Timeout per stage: configurable max execution time
- Retry with backoff: configurable retry count with exponential backoff
- Parallel fan-out/fan-in: split input across multiple stages, collect results
- Pipeline metrics: track execution time per stage

## Building & Testing
```bash
mvn compile -q
mvn test -q
mvn test -q -Dtest=AllTests
```

## Files
- `Stage.java` — Functional interface for a processing stage
- `Pipeline.java` — Pipeline interface
- `PipelineBuilder.java` — Your implementation (stubs)
- `PipelineMetrics.java` — Metrics collection
- `StageResult.java` — Result wrapper with timing info
