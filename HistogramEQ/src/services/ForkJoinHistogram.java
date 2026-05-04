import java.awt.Color;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.RecursiveTask;

public class ForkJoinHistogram extends AbstractHistogram {

    private static final int THRESHOLD = 256;

    public ForkJoinHistogram() {}

    @Override
    public String getName() {
        return "Fork/Join";
    }

    @Override
    public Color[][] equalize(Color[][] image) {
        int width = image.length;
        int totalPixels = width * image[0].length;
        Color[][] result = new Color[width][image[0].length];

        ForkJoinPool pool = ForkJoinPool.commonPool();

        // Stage 1: recursive histogram computation
        int[] hist = pool.invoke(new HistogramTask(image, 0, width));

        // Stage 2: cumulative histogram (sequential)
        int[] cumulative = buildCumulative(hist);

        // Stage 3: recursive pixel transformation
        pool.invoke(new TransformTask(image, result, cumulative, totalPixels, 0, width));

        return result;
    }

    // -------------------------------------------------------------------------
    // RecursiveTask: returns a partial int[256] histogram for rows [start, end)
    // -------------------------------------------------------------------------
    private static class HistogramTask extends RecursiveTask<int[]> {

        private final Color[][] image;
        private final int start;
        private final int end;

        HistogramTask(Color[][] image, int start, int end) {
            this.image = image;
            this.start = start;
            this.end = end;
        }

        @Override
        protected int[] compute() {
            if (end - start <= THRESHOLD) {
                int height = image[0].length;
                int[] local = new int[256];
                for (int i = start; i < end; i++)
                    for (int j = 0; j < height; j++) {
                        Color p = image[i][j];
                        int lum = (int) Math.round(0.299 * p.getRed() + 0.587 * p.getGreen() + 0.114 * p.getBlue());
                        local[lum]++;
                    }
                return local;
            }

            int mid = (start + end) / 2;
            HistogramTask left = new HistogramTask(image, start, mid);
            left.fork();

            int[] rightResult = new HistogramTask(image, mid, end).compute();
            int[] leftResult  = left.join();

            // Merge the two partial histograms
            int[] merged = new int[256];
            for (int i = 0; i < 256; i++) merged[i] = leftResult[i] + rightResult[i];
            return merged;
        }
    }

    // -------------------------------------------------------------------------
    // RecursiveAction: transforms pixels for rows [start, end) into result[]
    // -------------------------------------------------------------------------
    private static class TransformTask extends RecursiveAction {

        private final Color[][] source;
        private final Color[][] result;
        private final int[] cumulative;
        private final int totalPixels;
        private final int start;
        private final int end;

        TransformTask(Color[][] source, Color[][] result, int[] cumulative,
                      int totalPixels, int start, int end) {
            this.source      = source;
            this.result      = result;
            this.cumulative  = cumulative;
            this.totalPixels = totalPixels;
            this.start       = start;
            this.end         = end;
        }

        @Override
        protected void compute() {
            if (end - start <= THRESHOLD) {
                int height = source[0].length;
                for (int i = start; i < end; i++)
                    for (int j = 0; j < height; j++) {
                        Color p = source[i][j];
                        int lum = (int) Math.round(0.299 * p.getRed() + 0.587 * p.getGreen() + 0.114 * p.getBlue());
                        int newLum = (int) Math.floor(255.0 * cumulative[lum] / totalPixels);
                        result[i][j] = new Color(newLum, newLum, newLum);
                    }
                return;
            }

            int mid = (start + end) / 2;
            TransformTask left  = new TransformTask(source, result, cumulative, totalPixels, start, mid);
            TransformTask right = new TransformTask(source, result, cumulative, totalPixels, mid, end);
            invokeAll(left, right);
        }
    }
}
