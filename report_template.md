# Histogram Equalization and Parallel Processing in Java

**Sistemas Multinúcleo e Distribuídos**
Mestrado em Engenharia Informática
Instituto Superior de Engenharia do Porto

---

**Título:** Histogram Equalization and Parallel Processing in Java
**Unidade Curricular:** Sistemas Multinúcleo e Distribuídos
**Curso:** Mestrado em Engenharia Informática
**Instituto:** Instituto Superior de Engenharia do Porto

| Nº | Nome Completo |
|----|---------------|
|    |               |
|    |               |

**Data de Entrega:** 12 de Maio de 2026

---

## 1. Introduction

> Contextualizar o problema do histogram equalization e a motivação para usar processamento paralelo.
> Breve descrição do que foi feito no projeto.

Digital image processing can significantly enhance the visual detail and clarity of photographs. Histogram equalization is a technique that redistributes pixel intensities across the full available range, resulting in increased contrast. In this project, we implement histogram equalization in Java using multiple concurrency strategies, comparing their performance and analyzing the trade-offs of each approach.

---

## 2. Objectives

> Listar os objetivos do projeto.

- Implement histogram equalization sequentially as a performance baseline
- Develop parallel implementations using Java concurrency mechanisms:
  - Multithreaded (without thread pools)
  - Multithreaded (with thread pools via `ExecutorService`)
  - Fork/Join framework
  - `CompletableFuture`-based asynchronous approach
- Measure and compare execution time, memory usage, and speedup across implementations
- Tune the Java Garbage Collector to optimize memory management
- Analyze the impact of concurrency on performance and identify bottlenecks

---

## 3. Implementation Approaches

The histogram equalization algorithm is divided into three stages:
1. **Compute the luminosity histogram** — count pixel occurrences per intensity level (0–255)
2. **Compute the cumulative histogram** — prefix sum of the histogram (always sequential)
3. **Transform each pixel** — map original luminosity to new value using:

$$\text{newLuminosity} = \left\lfloor 255 \times \frac{\text{cumulativeHist}[L]}{\text{totalPixels}} \right\rfloor$$

Luminosity is computed as:

$$L = \text{round}(0.299 \cdot R + 0.587 \cdot G + 0.114 \cdot B)$$

---

### 3.1 Sequential Solution

> Descrever a implementação sequencial. Explicar como serve de baseline. Incluir snippet essencial.

The sequential implementation processes the image in a single thread, iterating over all pixels for each of the three stages. It serves as the performance baseline against which all parallel implementations are compared.

```java
// Stage 1: luminosity histogram
int[] hist = new int[256];
for (int i = 0; i < width; i++)
    for (int j = 0; j < height; j++) {
        Color p = image[i][j];
        hist[computeLuminosity(p.getRed(), p.getGreen(), p.getBlue())]++;
    }

// Stage 2: cumulative histogram
int[] cumulative = buildCumulative(hist);

// Stage 3: pixel transformation
for (int i = 0; i < width; i++)
    for (int j = 0; j < height; j++) {
        Color p = image[i][j];
        int lum = computeLuminosity(p.getRed(), p.getGreen(), p.getBlue());
        int newLum = (int) Math.floor(255.0 * cumulative[lum] / totalPixels);
        result[i][j] = new Color(newLum, newLum, newLum);
    }
```

Sequential processing does not utilize available CPU cores, leading to suboptimal performance on multicore systems.

---

### 3.2 Multithreaded Solution (Without Thread Pools)

> Descrever a estratégia de divisão do trabalho, a sincronização usada e justificar as escolhas.

Each thread processes a disjoint horizontal chunk of rows, eliminating the need for synchronization in Stages 1 and 3. In Stage 1, each thread builds its own local `int[256]` histogram — avoiding shared mutable state — which are then merged by the main thread before Stage 2.

```java
// Stage 1: local histograms per thread (no synchronization needed)
int[][] localHists = new int[numThreads][256];
for (int t = 0; t < numThreads; t++) {
    final int start = t * chunkSize;
    final int end = Math.min(start + chunkSize, width);
    final int[] localHist = localHists[t];
    threads[t] = new Thread(() -> {
        for (int i = start; i < end; i++)
            for (int j = 0; j < height; j++) {
                Color p = image[i][j];
                localHist[computeLuminosity(p.getRed(), p.getGreen(), p.getBlue())]++;
            }
    });
    threads[t].start();
}
for (Thread t : threads) t.join();
```

**Synchronization strategy:** No locks are required because each thread writes exclusively to its own array section. The merge and `join()` calls act as the synchronization barriers between stages.

---

### 3.3 Multithreaded Solution (With Thread Pools)

> Explicar o uso de ExecutorService, Callable e Future. Justificar vantagem face à abordagem anterior.

This implementation uses `ExecutorService` with a fixed thread pool. Stage 1 submits `Callable<int[]>` tasks that return local histograms; Stage 3 submits `Runnable` tasks for pixel transformation.

```java
ExecutorService pool = Executors.newFixedThreadPool(numThreads);

// Stage 1: Callable returns local histogram
List<Future<int[]>> histFutures = new ArrayList<>();
for (int t = 0; t < numThreads; t++) {
    final int start = t * chunkSize;
    final int end = Math.min(start + chunkSize, width);
    histFutures.add(pool.submit(() -> {
        int[] local = new int[256];
        for (int i = start; i < end; i++)
            for (int j = 0; j < height; j++) {
                Color p = image[i][j];
                local[computeLuminosity(p.getRed(), p.getGreen(), p.getBlue())]++;
            }
        return local;
    }));
}

// Merge (blocks until all futures complete)
int[] hist = new int[256];
for (Future<int[]> f : histFutures)
    for (int i = 0; i < 256; i++) hist[i] += f.get()[i];
```

**Advantage over manual threads:** Thread creation and destruction overhead is eliminated since the pool reuses existing threads across invocations, leading to better performance under repeated calls.

---

### 3.4 Fork/Join Framework Solution

> Explicar o padrão divide-and-conquer, RecursiveTask vs RecursiveAction, e o threshold.

The Fork/Join framework splits the row range `[0, width)` recursively until each chunk falls below a threshold (256 rows). `RecursiveTask<int[]>` is used for Stage 1 (returns a histogram), and `RecursiveAction` for Stage 3 (writes directly to the result array).

```java
// Stage 1: RecursiveTask splits recursively, merges results bottom-up
protected int[] compute() {
    if (end - start <= THRESHOLD) {
        // Base case: compute local histogram
        int[] local = new int[256];
        for (int i = start; i < end; i++)
            for (int j = 0; j < height; j++) {
                Color p = image[i][j];
                local[(int) Math.round(0.299*p.getRed() + 0.587*p.getGreen() + 0.114*p.getBlue())]++;
            }
        return local;
    }
    int mid = (start + end) / 2;
    HistogramTask left = new HistogramTask(image, start, mid);
    left.fork();                                          // async
    int[] right = new HistogramTask(image, mid, end).compute(); // current thread
    int[] leftResult = left.join();
    int[] merged = new int[256];
    for (int i = 0; i < 256; i++) merged[i] = leftResult[i] + right[i];
    return merged;
}
```

**Design decision:** `left.fork()` followed by `compute()` on the right subtask reuses the current thread instead of creating an extra one, which is the recommended Fork/Join pattern to avoid task explosion.

---

### 3.5 CompletableFuture-Based Solution

> Explicar supplyAsync, runAsync, allOf e thenApply. Realçar a natureza declarativa/composicional.

`CompletableFuture` provides a high-level, composable API for asynchronous programming. Stage 1 uses `supplyAsync()` to compute local histograms; `allOf().thenApply()` merges them without blocking until the final `.get()`. Stage 3 uses `runAsync()`.

```java
// Stage 1: supplyAsync per chunk
List<CompletableFuture<int[]>> histFutures = new ArrayList<>();
for (int t = 0; t < numThreads; t++) {
    final int start = t * chunkSize;
    final int end = Math.min(start + chunkSize, width);
    histFutures.add(CompletableFuture.supplyAsync(() -> {
        int[] local = new int[256];
        for (int i = start; i < end; i++)
            for (int j = 0; j < height; j++) {
                Color p = image[i][j];
                local[computeLuminosity(p.getRed(), p.getGreen(), p.getBlue())]++;
            }
        return local;
    }, executor));
}

// Merge declaratively after all complete
int[] hist = CompletableFuture
    .allOf(histFutures.toArray(new CompletableFuture[0]))
    .thenApply(v -> {
        int[] merged = new int[256];
        for (CompletableFuture<int[]> f : histFutures)
            for (int i = 0; i < 256; i++) merged[i] += f.join()[i];
        return merged;
    }).get();
```

**Key difference from ThreadPool:** `CompletableFuture` allows composing asynchronous stages declaratively without explicit blocking, making the code more readable and easier to extend with dependent tasks.

---

### 3.6 Garbage Collector Tuning

> Descrever os GCs testados, justificar a escolha e apresentar evidência (logs/métricas).
> Ver secção de análise de performance para os resultados.

Java provides multiple garbage collectors with different trade-offs. Three were evaluated:

| GC | Flag | Characteristics |
|----|------|-----------------|
| G1GC | `-XX:+UseG1GC` | Default since Java 9; low-pause, region-based |
| ParallelGC | `-XX:+UseParallelGC` | High throughput; longer but less frequent pauses |
| ZGC | `-XX:+UseZGC` | Ultra-low latency; concurrent collection |

**Test command:**
```bash
java -XX:+UseG1GC -Xms256m -Xmx512m -verbose:gc -cp out Main
java -XX:+UseParallelGC -Xms256m -Xmx512m -verbose:gc -cp out Main
java -XX:+UseZGC -Xms256m -Xmx512m -verbose:gc -cp out Main
```

> [Inserir logs de GC e análise comparativa aqui]

**Chosen GC:** [Justificar com base nos resultados]

---

## 4. Concurrency and Synchronization

> Analisar as decisões de sincronização em cada implementação.

| Implementation | Shared mutable state | Synchronization mechanism |
|---|---|---|
| Sequential | None | N/A |
| Threaded | `result[][]` (Stage 3) | Disjoint chunks — no locks needed |
| Thread Pool | `result[][]` (Stage 3) | Disjoint chunks — `Future.get()` as barrier |
| Fork/Join | `result[][]` (Stage 3) | `invokeAll()` as barrier |
| CompletableFuture | `result[][]` (Stage 3) | `allOf().get()` as barrier |

Stage 2 (cumulative histogram) is inherently sequential in all implementations — each entry depends on the previous one, making it impossible to parallelize without additional synchronization.

**Data structure choices:**
- Local `int[256]` per thread (Stages 1) — avoids contention on shared histogram
- Disjoint row chunks (Stage 3) — allows lock-free parallel writes to `result[][]`

---

## 5. Performance Analysis

### 5.1 Benchmarking Results

> Inserir tabela de resultados e gráficos gerados automaticamente.

Each implementation was benchmarked with 1 warm-up run and 5 measurement runs. Peak heap memory was measured via `MemoryMXBean`.

| Implementation | Avg (ms) | Speedup | Peak Heap (MB) |
|---|---|---|---|
| Sequential | | 1.00x | |
| Threaded - 2t | | | |
| Threaded - 4t | | | |
| Threaded - Nt | | | |
| Thread Pool - 2t | | | |
| Thread Pool - 4t | | | |
| Thread Pool - Nt | | | |
| Fork/Join | | | |
| CompletableFuture - 2t | | | |
| CompletableFuture - 4t | | | |
| CompletableFuture - Nt | | | |

> [Inserir gráfico execution_times.png]
> [Inserir gráfico speedup.png]
> [Inserir gráfico histogram_original.png e histogram_equalized.png]

---

### 5.2 Efficiency Gains

> Comparar com o baseline sequencial. Discutir os ganhos reais.

---

### 5.3 Scalability

> Como o speedup evolui com o aumento de threads? Existe um ponto de saturação?

---

### 5.4 Overhead Analysis

> Custo de criação de threads (Threaded vs ThreadPool), custo de objetos (CompletableFuture, Fork/Join tasks).

---

### 5.5 Bottlenecks

> Stage 2 é sempre sequencial. Memory bandwidth limita o ganho em Stages 1 e 3. Discutir.

---

## 6. Conclusions

> Resumir os resultados. Qual a melhor implementação? O que limitou o speedup? O que foi aprendido?

---

## References

[1] R.C. Gonzalez and R.E. Woods. *Digital Image Processing*. Pearson, 2018.

[2] Robert Sedgewick and Kevin Wayne. *Computer Science: An Interdisciplinary Approach*. Addison-Wesley Professional, 1st edition, 2016.
