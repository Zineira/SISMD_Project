import java.awt.Color;

public class ThreadedHistogram extends AbstractHistogram {

    private final int numThreads;

    public ThreadedHistogram(int numThreads) {
        this.numThreads = numThreads;
    }

    @Override
    public String getName() {
        return "Threaded (no pool) - " + numThreads + " threads";
    }

    @Override
    public Color[][] equalize(Color[][] image) throws InterruptedException {
        int width = image.length;
        int height = image[0].length;
        int totalPixels = width * height;
        Color[][] result = new Color[width][height];

        // ceiling division so no row is ever skipped
        int chunkSize = (width + numThreads - 1) / numThreads;

        // --- Stage 1: each thread builds its own local histogram ---
        // No shared mutable state -> no synchronisation needed here
        int[][] localHists = new int[numThreads][256];
        Thread[] threads = new Thread[numThreads];

        for (int t = 0; t < numThreads; t++) {
            final int start = t * chunkSize;
            final int end = Math.min(start + chunkSize, width);
            final int[] localHist = localHists[t];
            threads[t] = new Thread(() -> {
                for (int i = start; i < end; i++) {
                    for (int j = 0; j < height; j++) {
                        Color p = image[i][j];
                        localHist[computeLuminosity(p.getRed(), p.getGreen(), p.getBlue())]++;
                    }
                }
            });
            threads[t].start();
        }
        for (Thread t : threads) t.join();

        // Merge all local histograms into one (done by main thread, O(256*numThreads))
        int[] hist = new int[256];
        for (int[] local : localHists) {
            for (int i = 0; i < 256; i++) hist[i] += local[i];
        }

        // --- Stage 2: cumulative histogram (sequential — only 256 steps) ---
        int[] cumulative = buildCumulative(hist);

        // --- Stage 3: transform pixels in parallel ---
        // cumulative[] is read-only from here; each thread writes to its own rows
        // -> no synchronisation needed
        for (int t = 0; t < numThreads; t++) {
            final int start = t * chunkSize;
            final int end = Math.min(start + chunkSize, width);
            threads[t] = new Thread(() -> {
                for (int i = start; i < end; i++) {
                    for (int j = 0; j < height; j++) {
                        Color p = image[i][j];
                        int lum = computeLuminosity(p.getRed(), p.getGreen(), p.getBlue());
                        int newLum = (int) Math.floor(255.0 * cumulative[lum] / totalPixels);
                        result[i][j] = new Color(newLum, newLum, newLum);
                    }
                }
            });
            threads[t].start();
        }
        for (Thread t : threads) t.join();

        return result;
    }

}
