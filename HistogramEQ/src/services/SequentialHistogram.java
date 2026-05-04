import java.awt.Color;

public class SequentialHistogram extends AbstractHistogram {

    public SequentialHistogram() {}

    @Override
    public String getName() {
        return "Sequential";
    }

    @Override
    public Color[][] equalize(Color[][] image) {
        int width = image.length;
        int height = image[0].length;
        int totalPixels = width * height;
        Color[][] result = new Color[width][height];

        // Stage 1: luminosity histogram
        int[] hist = new int[256];
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                Color p = image[i][j];
                hist[computeLuminosity(p.getRed(), p.getGreen(), p.getBlue())]++;
            }
        }

        // Stage 2: cumulative histogram
        int[] cumulative = buildCumulative(hist);

        // Stage 3: transform each pixel
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                Color p = image[i][j];
                int lum = computeLuminosity(p.getRed(), p.getGreen(), p.getBlue());
                int newLum = (int) Math.floor(255.0 * cumulative[lum] / totalPixels);
                result[i][j] = new Color(newLum, newLum, newLum);
            }
        }

        return result;
    }


}
