import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class ThreadPoolHistogram extends AbstractHistogram {

    private final int numThreads;

    public ThreadPoolHistogram(int numThreads) {
        this.numThreads = numThreads;
    }

    @Override
    public String getName() {
        return "Thread Pool - " + numThreads + " threads";
    }

    @Override
    public Color[][] equalize(Color[][] image) throws Exception {
        int width = image.length;
        int height = image[0].length;
        int totalPixels = width * height;
        Color[][] result = new Color[width][height];

        int chunkSize = (width + numThreads - 1) / numThreads;
        ExecutorService pool = Executors.newFixedThreadPool(numThreads);

        // --- Stage 1: each Callable returns its local int[256] histogram ---
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

        // Merge all partial histograms (blocks until all futures complete)
        int[] hist = new int[256];
        for (Future<int[]> f : histFutures)
            for (int i = 0; i < 256; i++) hist[i] += f.get()[i];

        // --- Stage 2: cumulative histogram (sequential) ---
        int[] cumulative = buildCumulative(hist);

        // --- Stage 3: each Runnable transforms its own disjoint chunk ---
        List<Future<?>> transformFutures = new ArrayList<>();
        for (int t = 0; t < numThreads; t++) {
            final int start = t * chunkSize;
            final int end = Math.min(start + chunkSize, width);
            transformFutures.add(pool.submit(() -> {
                for (int i = start; i < end; i++)
                    for (int j = 0; j < height; j++) {
                        Color p = image[i][j];
                        int lum = computeLuminosity(p.getRed(), p.getGreen(), p.getBlue());
                        int newLum = (int) Math.floor(255.0 * cumulative[lum] / totalPixels);
                        result[i][j] = new Color(newLum, newLum, newLum);
                    }
            }));
        }
        for (Future<?> f : transformFutures) f.get();

        pool.shutdown();
        return result;
    }
}
