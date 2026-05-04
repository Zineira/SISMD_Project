import java.awt.Color;

public class Main {

    public static void main(String[] args) throws Exception {
        String imagePath = args.length > 0 ? args[0] : "../StarterCode/src.jpg";
        Color[][] image = Utils.loadImage(imagePath);

        int cores = Runtime.getRuntime().availableProcessors();
        System.out.println("Available cores: " + cores);
        System.out.println("Image size: " + image.length + "x" + image[0].length + " pixels\n");

        HistogramService[] services = {
            new SequentialHistogram(),
            new ThreadedHistogram(2),
            new ThreadedHistogram(4),
            new ThreadedHistogram(cores),
            new ThreadPoolHistogram(2),
            new ThreadPoolHistogram(4),
            new ThreadPoolHistogram(cores),
            new ForkJoinHistogram(),
            new CompletableFutureHistogram(2),
            new CompletableFutureHistogram(4),
            new CompletableFutureHistogram(cores),
        };

        BenchmarkRunner.run(services, image);

        // Generate output image + pixel-intensity histogram charts (like PDF figures 3 & 4)
        Color[][] equalized = new SequentialHistogram().equalize(Utils.copyImage(image));
        Utils.writeImage(equalized, "output.jpg");
        System.out.println("\nOutput image saved to output.jpg");

        int[] histOriginal  = computeHistogram(image);
        int[] histEqualized = computeHistogram(equalized);
        ChartGenerator.generateHistogramChart(histOriginal,  "Histogram of Original Image",   "charts/histogram_original.png");
        ChartGenerator.generateHistogramChart(histEqualized, "Histogram of Equalized Image", "charts/histogram_equalized.png");
    }

    private static int[] computeHistogram(Color[][] image) {
        int[] hist = new int[256];
        for (Color[] col : image) {
            for (Color p : col) {
                int lum = (int) Math.round(0.299 * p.getRed() + 0.587 * p.getGreen() + 0.114 * p.getBlue());
                hist[Math.max(0, Math.min(255, lum))]++;
            }
        }
        return hist;
    }
}
