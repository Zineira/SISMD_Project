import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class ChartGenerator {

    // Histogram chart (matches PDF style: 800x450)
    private static final int HIST_W = 800;
    private static final int HIST_H = 450;

    // Performance / speedup chart (wider for rotated labels)
    private static final int PERF_W = 1100;
    private static final int PERF_H = 520;

    private static final int PAD_LEFT        = 75;
    private static final int PAD_RIGHT       = 30;
    private static final int PAD_TOP         = 55;
    private static final int PAD_BOT_HIST    = 65;
    private static final int PAD_BOT_PERF    = 145;

    private static final Color HIST_BAR    = new Color(15, 30, 80);
    private static final Color PERF_BAR    = new Color(30, 80, 160);
    private static final Color SPEED_BAR   = new Color(20, 120, 60);
    private static final Color GRID        = new Color(220, 220, 220);
    private static final Color BASELINE    = new Color(200, 40, 40);

    // -------------------------------------------------------------------------
    // Pixel-intensity histogram  (like figures 3 and 4 in the project PDF)
    // -------------------------------------------------------------------------
    public static void generateHistogramChart(int[] hist, String title, String outputPath)
            throws IOException {

        BufferedImage img = new BufferedImage(HIST_W, HIST_H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = setup(img);

        int cW = HIST_W - PAD_LEFT - PAD_RIGHT;
        int cH = HIST_H - PAD_TOP  - PAD_BOT_HIST;

        int maxVal = 1;
        for (int v : hist) if (v > maxVal) maxVal = v;

        // Horizontal grid + Y labels
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        for (int i = 0; i <= 5; i++) {
            int y = PAD_TOP + cH - i * cH / 5;
            long tick = (long) i * maxVal / 5;

            g.setColor(GRID);
            g.drawLine(PAD_LEFT, y, PAD_LEFT + cW, y);

            g.setColor(Color.BLACK);
            String lbl = fmt(tick);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(lbl, PAD_LEFT - fm.stringWidth(lbl) - 6, y + fm.getAscent() / 2 - 2);
        }

        // 256 bars (one per intensity level)
        float bw = (float) cW / 256;
        g.setColor(HIST_BAR);
        for (int i = 0; i < 256; i++) {
            if (hist[i] == 0) continue;
            int bh = (int) ((long) hist[i] * cH / maxVal);
            int x  = PAD_LEFT + Math.round(i * bw);
            int w  = Math.max(1, Math.round((i + 1) * bw) - Math.round(i * bw));
            g.fillRect(x, PAD_TOP + cH - bh, w, bh);
        }

        // Axes
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(PAD_LEFT, PAD_TOP, PAD_LEFT, PAD_TOP + cH);
        g.drawLine(PAD_LEFT, PAD_TOP + cH, PAD_LEFT + cW, PAD_TOP + cH);

        // X-axis ticks
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        for (int tick : new int[]{0, 50, 100, 150, 200, 255}) {
            int x = PAD_LEFT + Math.round(tick * bw);
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(1f));
            g.drawLine(x, PAD_TOP + cH, x, PAD_TOP + cH + 4);
            FontMetrics fm = g.getFontMetrics();
            String lbl = String.valueOf(tick);
            g.drawString(lbl, x - fm.stringWidth(lbl) / 2, PAD_TOP + cH + 17);
        }

        drawYLabel(g, "Frequency", HIST_H, cH);
        drawXLabel(g, "Pixel Intensity", HIST_W, HIST_H, cW);
        drawTitle(g, title, HIST_W);

        save(img, outputPath, g);
    }

    // -------------------------------------------------------------------------
    // Execution-time bar chart
    // -------------------------------------------------------------------------
    public static void generatePerformanceChart(List<BenchmarkResult> results,
                                                String outputPath) throws IOException {
        barChart(results, "Execution Time per Implementation",
                 "Time (ms)", false, PERF_BAR, outputPath);
    }

    // -------------------------------------------------------------------------
    // Speedup bar chart
    // -------------------------------------------------------------------------
    public static void generateSpeedupChart(List<BenchmarkResult> results,
                                            String outputPath) throws IOException {
        barChart(results, "Speedup vs Sequential Baseline",
                 "Speedup (x)", true, SPEED_BAR, outputPath);
    }

    // -------------------------------------------------------------------------
    // Generic bar chart (shared by performance + speedup)
    // -------------------------------------------------------------------------
    private static void barChart(List<BenchmarkResult> results,
                                 String title, String yLabel, boolean isSpeedup,
                                 Color color, String outputPath) throws IOException {

        int n = results.size();
        BufferedImage img = new BufferedImage(PERF_W, PERF_H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = setup(img);

        int cW = PERF_W - PAD_LEFT - PAD_RIGHT;
        int cH = PERF_H - PAD_TOP  - PAD_BOT_PERF;

        double maxVal = 0.01;
        for (BenchmarkResult r : results) {
            double v = isSpeedup ? r.speedup : r.avgMs;
            if (v > maxVal) maxVal = v;
        }
        maxVal *= 1.12;

        // Grid + Y labels
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        for (int i = 0; i <= 5; i++) {
            int y = PAD_TOP + cH - i * cH / 5;
            double tick = i * maxVal / 5;

            g.setColor(GRID);
            g.drawLine(PAD_LEFT, y, PAD_LEFT + cW, y);

            g.setColor(Color.BLACK);
            String lbl = isSpeedup ? String.format("%.1fx", tick) : String.valueOf((long) tick);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(lbl, PAD_LEFT - fm.stringWidth(lbl) - 6, y + fm.getAscent() / 2 - 2);
        }

        // Baseline at speedup = 1x
        if (isSpeedup) {
            int y = PAD_TOP + cH - (int) (cH / maxVal);
            g.setColor(BASELINE);
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
                    0, new float[]{6, 4}, 0));
            g.drawLine(PAD_LEFT, y, PAD_LEFT + cW, y);
        }

        // Bars
        int slot = cW / n;
        int barW = Math.max(slot - 6, 8);

        for (int i = 0; i < n; i++) {
            BenchmarkResult r = results.get(i);
            double val = isSpeedup ? r.speedup : r.avgMs;
            int bh = (int) (val * cH / maxVal);
            int x  = PAD_LEFT + i * slot + (slot - barW) / 2;

            g.setColor(color);
            g.setStroke(new BasicStroke(1f));
            g.fillRect(x, PAD_TOP + cH - bh, barW, bh);
            g.setColor(color.darker());
            g.drawRect(x, PAD_TOP + cH - bh, barW, bh);

            // Value above bar
            g.setColor(Color.BLACK);
            g.setFont(new Font("SansSerif", Font.PLAIN, 9));
            String lbl = isSpeedup ? String.format("%.1fx", val) : val + "ms";
            FontMetrics fm = g.getFontMetrics();
            int lx = x + barW / 2 - fm.stringWidth(lbl) / 2;
            int ly = PAD_TOP + cH - bh - 3;
            if (ly > PAD_TOP + 8) g.drawString(lbl, lx, ly);
        }

        // Axes
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(PAD_LEFT, PAD_TOP, PAD_LEFT, PAD_TOP + cH);
        g.drawLine(PAD_LEFT, PAD_TOP + cH, PAD_LEFT + cW, PAD_TOP + cH);

        // X labels (rotated 45°)
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        for (int i = 0; i < n; i++) {
            int cx = PAD_LEFT + i * slot + slot / 2;
            AffineTransform orig = g.getTransform();
            g.translate(cx, PAD_TOP + cH + 10);
            g.rotate(Math.PI / 4);
            g.setColor(Color.BLACK);
            g.drawString(results.get(i).name, 0, 0);
            g.setTransform(orig);
        }

        drawYLabel(g, yLabel, PERF_H, cH);
        drawTitle(g, title, PERF_W);

        save(img, outputPath, g);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private static void drawYLabel(Graphics2D g, String label, int imgH, int cH) {
        AffineTransform orig = g.getTransform();
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        FontMetrics fm = g.getFontMetrics();
        int midY = PAD_TOP + cH / 2;
        g.rotate(-Math.PI / 2);
        g.setColor(Color.BLACK);
        g.drawString(label, -(midY + fm.stringWidth(label) / 2), 16);
        g.setTransform(orig);
    }

    private static void drawXLabel(Graphics2D g, String label, int imgW, int imgH, int cW) {
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(label, PAD_LEFT + (cW - fm.stringWidth(label)) / 2, imgH - 10);
    }

    private static void drawTitle(Graphics2D g, String title, int imgW) {
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(title, (imgW - fm.stringWidth(title)) / 2, PAD_TOP - 20);
    }

    private static Graphics2D setup(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        return g;
    }

    private static void save(BufferedImage img, String path, Graphics2D g) throws IOException {
        g.dispose();
        new File(path).getParentFile().mkdirs();
        ImageIO.write(img, "png", new File(path));
        System.out.println("Gráfico gerado: " + path);
    }

    private static String fmt(long n) {
        if (n >= 1_000_000) return n / 1_000_000 + "M";
        if (n >= 1_000)     return n / 1_000     + "k";
        return String.valueOf(n);
    }
}
