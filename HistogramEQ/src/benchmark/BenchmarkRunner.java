import java.awt.Color;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.GarbageCollectorMXBean;
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

        // CPU time — available on HotSpot/OpenJDK via com.sun.management extension
        com.sun.management.OperatingSystemMXBean osMxBean = null;
        if (ManagementFactory.getOperatingSystemMXBean()
                instanceof com.sun.management.OperatingSystemMXBean) {
            osMxBean = (com.sun.management.OperatingSystemMXBean)
                    ManagementFactory.getOperatingSystemMXBean();
        }

        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();

        System.out.printf("%-45s %10s %10s %10s %14s %8s %8s%n",
                "Implementation", "Avg(ms)", "CPU(ms)", "Speedup", "PeakHeap", "GC cnt", "GC ms");
        System.out.println("-".repeat(110));

        long sequentialAvgMs = -1;
        List<BenchmarkResult> results = new ArrayList<>();

        for (HistogramService service : services) {

            // Warm-up
            for (int i = 0; i < WARM_UP_RUNS; i++)
                service.equalize(Utils.copyImage(image));

            System.gc();
            Thread.sleep(60);
            long memBefore  = memBean.getHeapMemoryUsage().getUsed();
            long peakMem    = memBefore;
            long cpuBefore  = osMxBean != null ? osMxBean.getProcessCpuTime() : 0;
            long gcCntBefore  = totalGcCount(gcBeans);
            long gcTimeBefore = totalGcTime(gcBeans);

            long totalNs = 0;
            for (int i = 0; i < BENCHMARK_RUNS; i++) {
                Color[][] copy = Utils.copyImage(image);
                long start = System.nanoTime();
                service.equalize(copy);
                totalNs += System.nanoTime() - start;

                long now = memBean.getHeapMemoryUsage().getUsed();
                if (now > peakMem) peakMem = now;
            }

            long cpuAfter   = osMxBean != null ? osMxBean.getProcessCpuTime() : 0;
            long gcCntAfter   = totalGcCount(gcBeans);
            long gcTimeAfter  = totalGcTime(gcBeans);

            long avgMs       = (totalNs / BENCHMARK_RUNS) / 1_000_000;
            long peakDeltaMB = Math.max(0, peakMem - memBefore) / (1024 * 1024);
            long avgCpuMs    = osMxBean != null
                    ? (cpuAfter - cpuBefore) / (1_000_000L * BENCHMARK_RUNS)
                    : -1;
            long gcCount     = gcCntAfter  - gcCntBefore;
            long gcTimeMs    = gcTimeAfter - gcTimeBefore;

            if (sequentialAvgMs < 0) sequentialAvgMs = avgMs == 0 ? 1 : avgMs;
            double speedup = (double) sequentialAvgMs / Math.max(1, avgMs);

            String cpuStr = avgCpuMs >= 0 ? String.valueOf(avgCpuMs) : "N/A";
            System.out.printf("%-45s %10d %10s %10.2fx %14d %8d %8d%n",
                    service.getName(), avgMs, cpuStr, speedup, peakDeltaMB, gcCount, gcTimeMs);

            results.add(new BenchmarkResult(service.getName(), avgMs, speedup, peakDeltaMB,
                    avgCpuMs, gcCount, gcTimeMs));
        }

        exportCsv(results);
        ChartGenerator.generatePerformanceChart(results, "charts/execution_times.png");
        ChartGenerator.generateSpeedupChart(results, "charts/speedup.png");

        return results;
    }

    private static long totalGcCount(List<GarbageCollectorMXBean> beans) {
        long total = 0;
        for (GarbageCollectorMXBean b : beans) {
            long c = b.getCollectionCount();
            if (c > 0) total += c;
        }
        return total;
    }

    private static long totalGcTime(List<GarbageCollectorMXBean> beans) {
        long total = 0;
        for (GarbageCollectorMXBean b : beans) {
            long t = b.getCollectionTime();
            if (t > 0) total += t;
        }
        return total;
    }

    private static void exportCsv(List<BenchmarkResult> results) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter("results.csv"))) {
            pw.println("Implementation,AvgMs,CpuMs,Speedup,PeakHeapMB,GcCount,GcTimeMs");
            for (BenchmarkResult r : results) {
                pw.printf("%s,%d,%d,%.2f,%d,%d,%d%n",
                        r.name, r.avgMs, r.avgCpuMs, r.speedup,
                        r.peakMemoryMB, r.gcCount, r.gcTimeMs);
            }
        }
        System.out.println("\nTabela exportada: results.csv");
    }
}
