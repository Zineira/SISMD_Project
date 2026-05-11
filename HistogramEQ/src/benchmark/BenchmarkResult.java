public class BenchmarkResult {
    public final String name;
    public final long   avgMs;
    public final double speedup;
    public final long   peakMemoryMB;
    public final long   avgCpuMs;
    public final long   gcCount;
    public final long   gcTimeMs;

    BenchmarkResult(String name, long avgMs, double speedup, long peakMemoryMB,
                    long avgCpuMs, long gcCount, long gcTimeMs) {
        this.name         = name;
        this.avgMs        = avgMs;
        this.speedup      = speedup;
        this.peakMemoryMB = peakMemoryMB;
        this.avgCpuMs     = avgCpuMs;
        this.gcCount      = gcCount;
        this.gcTimeMs     = gcTimeMs;
    }
}
