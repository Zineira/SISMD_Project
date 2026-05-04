import java.awt.Color;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.ArrayList;
import java.util.List;

public class BenchmarkRunner {

    private static final int WARM_UP_RUNS   = 1;
    private static final int BENCHMARK_RUNS = 5;

    public static List<BenchmarkResult> run(HistogramService[] services, Color[][] image)
            throws Exception {

        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();

        System.out.printf("%-45s %12s %10s %14s%n",
                "Implementation", "Avg (ms)", "Speedup", "Peak Heap (MB)");
        System.out.println("-".repeat(85));

        long sequentialAvgMs = -1;
        List<BenchmarkResult> results = new ArrayList<>();

        for (HistogramService service : services) {

            // Warm-up: let the JIT compile hot paths
            for (int i = 0; i < WARM_UP_RUNS; i++) {
                service.equalize(Utils.copyImage(image));
            }

            // Settle heap before measuring
            System.gc();
            Thread.sleep(60);
            long memBefore = memBean.getHeapMemoryUsage().getUsed();
            long peakMem   = memBefore;

            long totalNs = 0;
            for (int i = 0; i < BENCHMARK_RUNS; i++) {
                Color[][] copy = Utils.copyImage(image);
                long start = System.nanoTime();
                service.equalize(copy);
                totalNs += System.nanoTime() - start;

                long now = memBean.getHeapMemoryUsage().getUsed();
                if (now > peakMem) peakMem = now;
            }

            long avgMs       = (totalNs / BENCHMARK_RUNS) / 1_000_000;
            long peakDeltaMB = Math.max(0, peakMem - memBefore) / (1024 * 1024);

            if (sequentialAvgMs < 0) sequentialAvgMs = avgMs == 0 ? 1 : avgMs;
            double speedup = (double) sequentialAvgMs / Math.max(1, avgMs);

            System.out.printf("%-45s %12d %10.2fx %14d%n",
                    service.getName(), avgMs, speedup, peakDeltaMB);

            results.add(new BenchmarkResult(service.getName(), avgMs, speedup, peakDeltaMB));
        }

        exportCsv(results);
        ChartGenerator.generatePerformanceChart(results, "charts/execution_times.png");
        ChartGenerator.generateSpeedupChart(results, "charts/speedup.png");

        return results;
    }

    private static void exportCsv(List<BenchmarkResult> results) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter("results.csv"))) {
            pw.println("Implementation,AvgMs,Speedup,PeakHeapMB");
            for (BenchmarkResult r : results) {
                pw.printf("%s,%d,%.2f,%d%n", r.name, r.avgMs, r.speedup, r.peakMemoryMB);
            }
        }
        System.out.println("\nTabela exportada: results.csv");
    }
}
