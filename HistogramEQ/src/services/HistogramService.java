import java.awt.Color;

public interface HistogramService {

    String getName();

    /**
     * Applies histogram equalization to the given image.
     * @param image source pixel matrix (must not be modified)
     * @return new pixel matrix with equalized luminosity
     */
    Color[][] equalize(Color[][] image) throws Exception;
}
