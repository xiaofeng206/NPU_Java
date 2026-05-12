import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

class Main {
    private static final int LEVELS = 3;

    public static void main(String[] args) throws Exception {
        String inputPath = args.length > 0 ? args[0] : "tiger.jpeg";
        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            throw new IOException("找不到輸入圖片: " + inputFile.getAbsolutePath());
        }

        BufferedImage image = ImageIO.read(inputFile);
        if (image == null) {
            throw new IOException("無法讀取圖片: " + inputFile.getAbsolutePath());
        }

        int width = image.getWidth();
        int height = image.getHeight();
        int[] scores = new int[width * height];
        int[] histogram = new int[256];

        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int score = computeTigerLikelihood(rgb);
                scores[index++] = score;
                histogram[score]++;
            }
        }

        int[] thresholds = findMultiOtsuThresholds(histogram, LEVELS);
        int t1 = thresholds[0];
        int t2 = thresholds[1];

        BufferedImage classMap = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = y * width + x;
                int score = scores[i];
                int cls = score <= t1 ? 0 : (score <= t2 ? 1 : 2);
                classMap.setRGB(x, y, classColor(cls).getRGB());
            }
        }

        ImageIO.write(classMap, "png", new File("segmentation_classes.png"));

        System.out.println("輸入圖片: " + inputFile.getAbsolutePath());
        System.out.println("自動找出的閥值: t1 = " + t1 + ", t2 = " + t2);
        System.out.println("輸出檔案: segmentation_classes.png");
    }

    private static int computeTigerLikelihood(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        double score = 128.0 + 0.70 * (r - g) + 0.30 * (r - b);
        return clampToByte((int) Math.round(score));
    }

    private static int[] findMultiOtsuThresholds(int[] histogram, int classCount) {
        if (classCount != 3) {
            throw new IllegalArgumentException("目前實作為 3 類別（2 個閥值）的 multi-threshold segmentation");
        }

        double[] prob = new double[256];
        double total = 0.0;
        for (int h : histogram) {
            total += h;
        }
        if (total == 0.0) {
            return new int[] {85, 170};
        }

        for (int i = 0; i < 256; i++) {
            prob[i] = histogram[i] / total;
        }

        double[] prefixProb = new double[256];
        double[] prefixMean = new double[256];
        prefixProb[0] = prob[0];
        prefixMean[0] = 0.0 * prob[0];
        for (int i = 1; i < 256; i++) {
            prefixProb[i] = prefixProb[i - 1] + prob[i];
            prefixMean[i] = prefixMean[i - 1] + i * prob[i];
        }

        double totalMean = prefixMean[255];
        double bestScore = -1.0;
        int bestT1 = 0;
        int bestT2 = 0;

        for (int t1 = 0; t1 < 254; t1++) {
            for (int t2 = t1 + 1; t2 < 255; t2++) {
                double w0 = prefixProb[t1];
                double w1 = prefixProb[t2] - prefixProb[t1];
                double w2 = 1.0 - prefixProb[t2];
                if (w0 <= 0.0 || w1 <= 0.0 || w2 <= 0.0) {
                    continue;
                }

                double m0 = prefixMean[t1] / w0;
                double m1 = (prefixMean[t2] - prefixMean[t1]) / w1;
                double m2 = (prefixMean[255] - prefixMean[t2]) / w2;

                double score = w0 * square(m0 - totalMean)
                        + w1 * square(m1 - totalMean)
                        + w2 * square(m2 - totalMean);

                if (score > bestScore) {
                    bestScore = score;
                    bestT1 = t1;
                    bestT2 = t2;
                }
            }
        }

        return new int[] {bestT1, bestT2};
    }

    private static Color classColor(int cls) {
        switch (cls) {
            case 0:
                return new Color(40, 90, 200);
            case 1:
                return new Color(40, 190, 90);
            default:
                return new Color(240, 120, 40);
        }
    }

    private static int clampToByte(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static double square(double value) {
        return value * value;
    }
}
