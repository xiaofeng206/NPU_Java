import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class ImageSegmentation {

	public static void main(String[] args) throws IOException {
		String inputPath = args.length > 0 ? args[0] : "下載.jpg";
		String outputPath = args.length > 1 ? args[1] : "segmented.png";
		double thresholdScale = args.length > 2 ? Double.parseDouble(args[2]) : 1.35;

		BufferedImage input = readImage(inputPath);
		if (input == null) {
			throw new IOException("Cannot read image: " + inputPath);
		}

		BufferedImage segmented = segmentRhino(input, thresholdScale);
		ImageIO.write(segmented, "png", new File(outputPath));

		System.out.println("Segmentation complete: " + outputPath);
	}

	private static BufferedImage readImage(String inputPath) throws IOException {
		File file = new File(inputPath);
		BufferedImage image = ImageIO.read(file);
		if (image != null) {
			return image;
		}

		Path tempPng = Files.createTempFile("segmentation_input_", ".png");
		try {
			Process process = new ProcessBuilder("sips", "-s", "format", "png", inputPath, "--out", tempPng.toString())
					.redirectErrorStream(true)
					.start();
			int exitCode = process.waitFor();
			if (exitCode != 0) {
				return null;
			}
			return ImageIO.read(tempPng.toFile());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Image conversion interrupted", e);
		} finally {
			Files.deleteIfExists(tempPng);
		}
	}

	private static BufferedImage segmentRhino(BufferedImage input, double thresholdScale) {
		int width = input.getWidth();
		int height = input.getHeight();
		int centerX1 = width / 5;
		int centerX2 = width - centerX1;
		int centerY1 = height / 5;
		int centerY2 = height - centerY1;
		double[] backgroundMean = estimateBackgroundMean(input);
		double backgroundStd = estimateBackgroundStd(input, backgroundMean);
		double threshold = Math.max(28.0, backgroundStd * 2.0 * thresholdScale);
		boolean[][] mask = new boolean[height][width];

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int rgb = input.getRGB(x, y);
				double distance = colorDistance(rgb, backgroundMean);
				boolean inCenter = x >= centerX1 && x <= centerX2 && y >= centerY1 && y <= centerY2;
				mask[y][x] = distance > threshold || (inCenter && distance > threshold * 0.7);
			}
		}

		mask = smoothMask(mask, 1);
		mask = dilateMask(mask, 1);
		BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				output.setRGB(x, y, mask[y][x] ? 0x00FF00 : input.getRGB(x, y));
			}
		}

		return output;
	}

	private static double[] estimateBackgroundMean(BufferedImage input) {
		int width = input.getWidth();
		int height = input.getHeight();
		double[] sums = new double[3];
		int count = 0;
		for (int x = 0; x < width; x++) {
			addPixelToSums(input.getRGB(x, 0), sums);
			addPixelToSums(input.getRGB(x, height - 1), sums);
			count += 2;
		}
		for (int y = 1; y < height - 1; y++) {
			addPixelToSums(input.getRGB(0, y), sums);
			addPixelToSums(input.getRGB(width - 1, y), sums);
			count += 2;
		}

		return new double[] { sums[0] / count, sums[1] / count, sums[2] / count };
	}

	private static double estimateBackgroundStd(BufferedImage input, double[] mean) {
		int width = input.getWidth();
		int height = input.getHeight();
		double sum = 0;
		int count = 0;

		for (int x = 0; x < width; x++) {
			sum += squaredColorDistance(input.getRGB(x, 0), mean);
			sum += squaredColorDistance(input.getRGB(x, height - 1), mean);
			count += 2;
		}
		for (int y = 1; y < height - 1; y++) {
			sum += squaredColorDistance(input.getRGB(0, y), mean);
			sum += squaredColorDistance(input.getRGB(width - 1, y), mean);
			count += 2;
		}

		return Math.sqrt(sum / Math.max(1, count));
	}

	private static boolean[][] smoothMask(boolean[][] mask, int radius) {
		int height = mask.length;
		int width = mask[0].length;
		boolean[][] result = new boolean[height][width];

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int foregroundVotes = 0;
				int total = 0;
				for (int dy = -radius; dy <= radius; dy++) {
					for (int dx = -radius; dx <= radius; dx++) {
						int ny = y + dy;
						int nx = x + dx;
						if (ny >= 0 && ny < height && nx >= 0 && nx < width) {
							if (mask[ny][nx]) {
								foregroundVotes++;
							}
							total++;
						}
					}
				}
				result[y][x] = foregroundVotes >= (total / 2);
			}
		}

		return result;
	}

	private static boolean[][] dilateMask(boolean[][] mask, int radius) {
		int height = mask.length;
		int width = mask[0].length;
		boolean[][] result = new boolean[height][width];

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				boolean found = false;
				for (int dy = -radius; dy <= radius && !found; dy++) {
					for (int dx = -radius; dx <= radius; dx++) {
						int ny = y + dy;
						int nx = x + dx;
						if (ny >= 0 && ny < height && nx >= 0 && nx < width && mask[ny][nx]) {
							found = true;
							break;
						}
					}
				}
				result[y][x] = found;
			}
		}

		return result;
	}

	private static double colorDistance(int rgb, double[] mean) {
		double r = (rgb >> 16) & 0xFF;
		double g = (rgb >> 8) & 0xFF;
		double b = rgb & 0xFF;
		return Math.sqrt((r - mean[0]) * (r - mean[0]) + (g - mean[1]) * (g - mean[1]) + (b - mean[2]) * (b - mean[2]));
	}

	private static double squaredColorDistance(int rgb, double[] mean) {
		double r = (rgb >> 16) & 0xFF;
		double g = (rgb >> 8) & 0xFF;
		double b = rgb & 0xFF;
		double dr = r - mean[0];
		double dg = g - mean[1];
		double db = b - mean[2];
		return dr * dr + dg * dg + db * db;
	}

	private static void addPixelToSums(int rgb, double[] sums) {
		sums[0] += (rgb >> 16) & 0xFF;
		sums[1] += (rgb >> 8) & 0xFF;
		sums[2] += rgb & 0xFF;
	}
}
