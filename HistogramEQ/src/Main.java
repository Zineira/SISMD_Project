import java.awt.Color;
import java.io.File;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Scanner;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

public class Main {

    public static void main(String[] args) throws Exception {
        String imagePath;

        if (args.length > 0) {
            imagePath = args[0];
        } else {
            imagePath = selectImage();
        }

        File imageFile = new File(imagePath);
        if (!imageFile.exists()) {
            System.err.println("Image not found: " + imagePath);
            System.exit(1);
        }

        // Output folder: results/<image-name-without-extension>/
        String baseName = imageFile.getName();
        if (baseName.contains(".")) baseName = baseName.substring(0, baseName.lastIndexOf('.'));
        String outputDir = "results/" + baseName;
        new File(outputDir).mkdirs();
        new File(outputDir + "/charts").mkdirs();

        Color[][] image = Utils.loadImage(imagePath);
        int cores = Runtime.getRuntime().availableProcessors();

        System.out.println("\n=== Benchmark ===");
        System.out.println("Image  : " + imageFile.getName() +
                " (" + image.length + "x" + image[0].length +
                " = " + (image.length * image[0].length / 1_000) + "k pixels)");
        System.out.println("Cores  : " + cores);
        System.out.println("Output : " + outputDir + "/");
        System.out.println();

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

        BenchmarkRunner.run(services, image, outputDir);

        // Equalized output image
        Color[][] equalized = new SequentialHistogram().equalize(Utils.copyImage(image));
        Utils.writeImage(equalized, outputDir + "/output.jpg");
        System.out.println("Output image saved to " + outputDir + "/output.jpg");

        // Pixel-intensity histograms
        int[] histOriginal  = computeHistogram(image);
        int[] histEqualized = computeHistogram(equalized);
        ChartGenerator.generateHistogramChart(histOriginal,
                "Histogram of Original Image",  outputDir + "/charts/histogram_original.png");
        ChartGenerator.generateHistogramChart(histEqualized,
                "Histogram of Equalized Image", outputDir + "/charts/histogram_equalized.png");
    }

    // -------------------------------------------------------------------------
    // Interactive image selection menu
    // -------------------------------------------------------------------------
    private static String selectImage() throws Exception {
        File imagesDir = new File("images");
        File[] files = imagesDir.exists()
                ? imagesDir.listFiles(f -> {
                    String n = f.getName().toLowerCase();
                    return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png");
                })
                : null;

        if (files == null) files = new File[0];
        Arrays.sort(files);

        System.out.println();
        System.out.println("┌─────────────────────────────────────────────────────────────┐");
        System.out.println("│          Histogram Equalization — Image Selection           │");
        System.out.println("├────┬──────────────────────────┬─────────────┬───────────────┤");
        System.out.printf( "│ %-2s │ %-24s │ %-11s │ %-13s │%n", "#", "File", "Dimensions", "Pixels");
        System.out.println("├────┼──────────────────────────┼─────────────┼───────────────┤");

        for (int i = 0; i < files.length; i++) {
            int[] dim = readDimensions(files[i]);
            long pixels = (long) dim[0] * dim[1];
            String dimStr = dim[0] + "x" + dim[1];
            String pxStr  = pixels > 1_000_000
                    ? String.format("%.1fM", pixels / 1_000_000.0)
                    : String.format("%dk", pixels / 1_000);
            System.out.printf("│ %-2d │ %-24s │ %-11s │ %-13s │%n",
                    i + 1, files[i].getName(), dimStr, pxStr);
        }

        System.out.println("├────┼──────────────────────────┼─────────────┼───────────────┤");
        System.out.printf( "│ %-2s │ %-24s │ %-11s │ %-13s │%n",
                "0", "Custom path...", "", "");
        System.out.println("└────┴──────────────────────────┴─────────────┴───────────────┘");

        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.print("Select image [0-" + files.length + "]: ");
            String line = sc.nextLine().trim();
            try {
                int choice = Integer.parseInt(line);
                if (choice == 0) {
                    System.out.print("Enter path: ");
                    return sc.nextLine().trim();
                }
                if (choice >= 1 && choice <= files.length) {
                    return files[choice - 1].getPath();
                }
            } catch (NumberFormatException ignored) {}
            System.out.println("Invalid choice, try again.");
        }
    }

    // Reads image dimensions from header only (no full pixel load)
    private static int[] readDimensions(File f) {
        try (ImageInputStream in = ImageIO.createImageInputStream(f)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                try {
                    reader.setInput(in);
                    return new int[]{reader.getWidth(0), reader.getHeight(0)};
                } finally {
                    reader.dispose();
                }
            }
        } catch (Exception ignored) {}
        return new int[]{0, 0};
    }

    private static int[] computeHistogram(Color[][] image) {
        int[] hist = new int[256];
        for (Color[] col : image)
            for (Color p : col) {
                int lum = (int) Math.round(0.299 * p.getRed() + 0.587 * p.getGreen() + 0.114 * p.getBlue());
                hist[Math.max(0, Math.min(255, lum))]++;
            }
        return hist;
    }
}
