import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CompletableFutureHistogram extends AbstractHistogram {

    private final int numThreads;

    public CompletableFutureHistogram(int numThreads) {
        this.numThreads = numThreads;
    }

    @Override
    public String getName() {
        return "CompletableFuture - " + numThreads + " threads";
    }

    @Override
    public Color[][] equalize(Color[][] image) throws Exception {
        int width = image.length;
        int height = image[0].length;
        int totalPixels = width * height;
        Color[][] result = new Color[width][height];

        int chunkSize = (width + numThreads - 1) / numThreads;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        // --- Stage 1: each supplyAsync returns a local int[256] histogram ---
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

        // Merge all partial histograms once every future completes
        int[] hist = CompletableFuture
                .allOf(histFutures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    int[] merged = new int[256];
                    for (CompletableFuture<int[]> f : histFutures)
                        for (int i = 0; i < 256; i++) merged[i] += f.join()[i];
                    return merged;
                })
                .get();

        // --- Stage 2: cumulative histogram (sequential) ---
        int[] cumulative = buildCumulative(hist);

        // --- Stage 3: each supplyAsync transforms its own disjoint chunk ---
        List<CompletableFuture<Void>> transformFutures = new ArrayList<>();
        for (int t = 0; t < numThreads; t++) {
            final int start = t * chunkSize;
            final int end = Math.min(start + chunkSize, width);
            transformFutures.add(CompletableFuture.runAsync(() -> {
                for (int i = start; i < end; i++)
                    for (int j = 0; j < height; j++) {
                        Color p = image[i][j];
                        int lum = computeLuminosity(p.getRed(), p.getGreen(), p.getBlue());
                        int newLum = (int) Math.floor(255.0 * cumulative[lum] / totalPixels);
                        result[i][j] = new Color(newLum, newLum, newLum);
                    }
            }, executor));
        }

        CompletableFuture.allOf(transformFutures.toArray(new CompletableFuture[0])).get();

        executor.shutdown();
        return result;
    }
}
