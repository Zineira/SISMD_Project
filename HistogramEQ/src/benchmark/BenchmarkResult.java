public class BenchmarkResult {
    public final String name;
    public final long   avgMs;
    public final double speedup;
    public final long   peakMemoryMB;

    BenchmarkResult(String name, long avgMs, double speedup, long peakMemoryMB) {
        this.name         = name;
        this.avgMs        = avgMs;
        this.speedup      = speedup;
        this.peakMemoryMB = peakMemoryMB;
    }
}
