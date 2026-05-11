# Histogram Equalization and Parallel Processing in Java

Sistemas Multinúcleo e Distribuídos — Mestrado em Engenharia Informática — ISEP

---

## Overview

This project implements histogram equalization using five concurrency strategies in Java, benchmarks their performance, and analyzes the impact of Garbage Collector selection on execution time and memory usage.

The algorithm is divided into three stages:
1. Compute the luminosity histogram of the source image
2. Compute the cumulative histogram (sequential — serial dependency)
3. Transform each pixel using the equalized luminosity mapping

---

## Prerequisites

- **Java 11 or higher** (tested with OpenJDK 25)
- **bash** (for the build and GC benchmark scripts)
- No external libraries — pure Java standard library

Verify your Java version:
```bash
java --version
```

---

## Project Structure

```
HistogramEQ/
├── src/
│   ├── Main.java                          # Entry point — runs benchmark + generates outputs
│   ├── Utils.java                         # loadImage / writeImage / copyImage
│   ├── services/
│   │   ├── HistogramService.java          # Interface
│   │   ├── AbstractHistogram.java         # computeLuminosity + buildCumulative
│   │   ├── SequentialHistogram.java       # Baseline — single thread
│   │   ├── ThreadedHistogram.java         # Explicit threads, no pool
│   │   ├── ThreadPoolHistogram.java       # ExecutorService + Callable/Future
│   │   ├── ForkJoinHistogram.java         # RecursiveTask + RecursiveAction
│   │   └── CompletableFutureHistogram.java# supplyAsync + allOf + thenApply
│   └── benchmark/
│       ├── BenchmarkRunner.java           # Measures time, CPU, heap, GC metrics
│       ├── BenchmarkResult.java           # Result data class
│       └── ChartGenerator.java           # Generates PNG charts (pure Java2D)
├── build.sh                               # Compile + run
├── run_gc_benchmark.sh                    # Run with G1GC / ParallelGC / ZGC
├── charts/                                # Generated PNG charts (auto-created)
├── gc_results/                            # GC logs and per-GC output (auto-created)
├── results.csv                            # Benchmark results table (auto-created)
└── output.jpg                             # Equalized image (auto-created)
```

---

## Build and Run

All commands must be run from inside the `HistogramEQ/` directory.

### Standard benchmark (default GC)

```bash
cd HistogramEQ
bash build.sh
```

This compiles all sources and runs the benchmark using `../StarterCode/src.jpg` as input.

To use a different image:
```bash
bash build.sh path/to/image.jpg
```

### Run without recompiling

```bash
java -cp out Main
java -cp out Main path/to/image.jpg
```

---

## GC Benchmark

Runs the full benchmark three times — once per Garbage Collector — and saves logs and results to `gc_results/`.

```bash
bash run_gc_benchmark.sh
bash run_gc_benchmark.sh path/to/image.jpg   # custom image
```

GCs tested:
| Flag | Collector |
|---|---|
| `-XX:+UseG1GC` | Garbage First (default since Java 9) |
| `-XX:+UseParallelGC` | Parallel GC |
| `-XX:+UseZGC` | Z Garbage Collector |

Output files in `gc_results/`:
- `out_G1GC.txt`, `out_ParallelGC.txt`, `out_ZGC.txt` — benchmark output per GC
- `gc_G1GC.log`, `gc_ParallelGC.log`, `gc_ZGC.log` — detailed GC event logs
- `summary.txt` — sequential baseline comparison across GCs

---

## Benchmark Output

Each run prints a table with the following columns:

| Column | Description |
|---|---|
| `Avg(ms)` | Average wall-clock time per run (5 runs) |
| `CPU(ms)` | Average CPU time consumed per run across all cores |
| `Speedup` | Ratio relative to sequential baseline |
| `PeakHeap` | Peak heap delta in MB (via `MemoryMXBean`) |
| `GC cnt` | Number of GC events during the 5 runs |
| `GC ms` | Total GC pause time during the 5 runs |

Example output:
```
Implementation                                 Avg(ms)    CPU(ms)    Speedup      PeakHeap   GC cnt   GC ms
-----------------------------------------------------------------------------------------------------------
Sequential                                          20         33       1.00x           84        2       9
Thread Pool - 4 threads                              3         17       6.67x          127        1       3
Fork/Join                                           11         51       1.82x          133        1       4
```

Generated files after each run:
- `charts/execution_times.png` — bar chart of average execution times
- `charts/speedup.png` — speedup vs sequential baseline
- `charts/histogram_original.png` — pixel intensity distribution before equalization
- `charts/histogram_equalized.png` — pixel intensity distribution after equalization
- `results.csv` — full results table (importable into Excel/LibreOffice)
- `output.jpg` — equalized output image

---

## Implementations

| Implementation | Class | Concurrency mechanism |
|---|---|---|
| Sequential | `SequentialHistogram` | None — baseline |
| Threaded | `ThreadedHistogram` | `Thread`, `join()` |
| Thread Pool | `ThreadPoolHistogram` | `ExecutorService`, `Callable`, `Future` |
| Fork/Join | `ForkJoinHistogram` | `ForkJoinPool`, `RecursiveTask`, `RecursiveAction` |
| CompletableFuture | `CompletableFutureHistogram` | `CompletableFuture`, `supplyAsync`, `allOf` |

All parallel implementations use **disjoint row chunks** to avoid synchronization in Stages 1 and 3. Stage 2 (cumulative histogram) is always sequential due to its serial prefix-sum dependency.

---

## JVM Parameters (used in GC benchmark)

```
-Xms256m              Initial heap size
-Xmx512m              Maximum heap size
-XX:+UseG1GC          Select G1 Garbage Collector
-XX:+UseParallelGC    Select Parallel Garbage Collector
-XX:+UseZGC           Select Z Garbage Collector
-Xlog:gc*:file=...    Write GC events to log file (Java 9+ unified logging)
```
