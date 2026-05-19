import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

class Main extends JFrame {

	private final ImagePanel originalPanel;
	private final ImagePanel resultPanel;
	private final JLabel statusLabel;
	private final JComboBox<String> styleBox;
	private final JSlider edgeThresholdSlider;

	private BufferedImage originalImage;

	public Main() {
		setTitle("圖片轉卡通風格 App");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(1300, 760);
		setLocationRelativeTo(null);
		setLayout(new BorderLayout(12, 12));

		JPanel topBar = new JPanel();
		topBar.setLayout(new BoxLayout(topBar, BoxLayout.X_AXIS));
		topBar.setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));

		JButton loadButton = new JButton("1) 選擇並讀取圖片");
		JButton processButton = new JButton("開始轉卡通");
		JButton saveButton = new JButton("儲存結果");

		String[] styles = {
				"經典卡通",
				"柔和卡通",
				"鉛筆素描",
				"普普藝術",
				"水墨風",
				"AI 萌系電影風（參考圖）"
		};
		styleBox = new JComboBox<>(styles);

		edgeThresholdSlider = new JSlider(20, 220, 90);
		edgeThresholdSlider.setMajorTickSpacing(50);
		edgeThresholdSlider.setMinorTickSpacing(10);
		edgeThresholdSlider.setPaintTicks(true);
		edgeThresholdSlider.setPaintLabels(true);

		topBar.add(loadButton);
		topBar.add(Box.createHorizontalStrut(10));
		topBar.add(new JLabel("4) 風格："));
		topBar.add(Box.createHorizontalStrut(6));
		topBar.add(styleBox);
		topBar.add(Box.createHorizontalStrut(12));
		topBar.add(new JLabel("2) Signal Point 門檻："));
		topBar.add(Box.createHorizontalStrut(6));
		topBar.add(edgeThresholdSlider);
		topBar.add(Box.createHorizontalStrut(12));
		topBar.add(processButton);
		topBar.add(Box.createHorizontalStrut(10));
		topBar.add(saveButton);

		originalPanel = new ImagePanel("原圖顯示區");
		resultPanel = new ImagePanel("卡通風格結果區");

		JPanel imageContainer = new JPanel(new GridLayout(1, 2, 10, 10));
		imageContainer.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
		imageContainer.add(wrapWithTitle("原始圖片", originalPanel));
		imageContainer.add(wrapWithTitle("轉換後圖片", resultPanel));

		statusLabel = new JLabel("請先載入圖片，接著選擇風格並處理。", JLabel.LEFT);
		statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 14f));
		statusLabel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createEmptyBorder(8, 12, 12, 12),
				BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 220, 220))
		));

		add(topBar, BorderLayout.NORTH);
		add(new JScrollPane(imageContainer), BorderLayout.CENTER);
		add(statusLabel, BorderLayout.SOUTH);

		loadButton.addActionListener(e -> chooseAndLoadImage());
		processButton.addActionListener(e -> processImage());
		saveButton.addActionListener(e -> saveResultImage());
	}

	private JPanel wrapWithTitle(String title, JPanel panel) {
		JPanel container = new JPanel(new BorderLayout());
		JLabel titleLabel = new JLabel(title, JLabel.CENTER);
		titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
		titleLabel.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
		container.add(titleLabel, BorderLayout.NORTH);
		container.add(panel, BorderLayout.CENTER);
		return container;
	}

	private void chooseAndLoadImage() {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("選擇圖片");
		String[] readableExtensions = getReadableImageExtensions();
		chooser.setAcceptAllFileFilterUsed(true);
		if (readableExtensions.length > 0) {
			chooser.setFileFilter(new FileNameExtensionFilter("可讀取圖片格式", readableExtensions));
		}

		int result = chooser.showOpenDialog(this);
		if (result != JFileChooser.APPROVE_OPTION) {
			return;
		}

		File selected = chooser.getSelectedFile();
		try {
			BufferedImage img = ImageIO.read(selected);
			if (img == null) {
				throw new IOException("無法讀取此檔案格式。支援格式：" + getReadableImageFormatsText());
			}
			originalImage = img;
			originalPanel.setImage(img);
			resultPanel.setImage(null);
			statusLabel.setText("已讀取圖片：" + selected.getName());
		} catch (IOException ex) {
			JOptionPane.showMessageDialog(this,
					"讀取圖片失敗：" + ex.getMessage(),
					"錯誤",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	private String[] getReadableImageExtensions() {
		String[] suffixes = ImageIO.getReaderFileSuffixes();
		Set<String> normalized = new LinkedHashSet<>();

		for (String ext : suffixes) {
			if (ext == null) {
				continue;
			}
			String value = ext.trim().toLowerCase(Locale.ROOT);
			if (!value.isEmpty()) {
				normalized.add(value);
			}
		}

		String[] result = normalized.toArray(new String[0]);
		Arrays.sort(result);
		return result;
	}

	private String getReadableImageFormatsText() {
		String[] exts = getReadableImageExtensions();
		return exts.length == 0 ? "(無)" : String.join(", ", exts);
	}

	private void processImage() {
		if (originalImage == null) {
			JOptionPane.showMessageDialog(this,
					"請先載入一張圖片。",
					"提醒",
					JOptionPane.WARNING_MESSAGE);
			return;
		}

		int threshold = edgeThresholdSlider.getValue();
		String style = (String) styleBox.getSelectedItem();

		statusLabel.setText("處理中：尋找 Signal Points → 邊緣灰階 → 套用風格 " + style + " ...");

		BufferedImage edgeGray = detectSignalEdgesAsGray(originalImage, threshold);
		BufferedImage output = applyStyle(originalImage, edgeGray, style);

		resultPanel.setImage(output);
		statusLabel.setText("完成：已套用 " + style + " 風格，並美觀顯示整張圖片。 (門檻=" + threshold + ")");
	}

	private void saveResultImage() {
		BufferedImage output = resultPanel.getImage();
		if (output == null) {
			JOptionPane.showMessageDialog(this,
					"沒有可儲存的結果，請先進行卡通化處理。",
					"提醒",
					JOptionPane.WARNING_MESSAGE);
			return;
		}

		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("儲存卡通圖片");
		chooser.setSelectedFile(new File("cartoon_result.png"));

		int result = chooser.showSaveDialog(this);
		if (result != JFileChooser.APPROVE_OPTION) {
			return;
		}

		File file = chooser.getSelectedFile();
		String fileName = file.getName().toLowerCase();
		String format = fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") ? "jpg" : "png";

		try {
			ImageIO.write(output, format, file);
			statusLabel.setText("已儲存結果：" + file.getName());
		} catch (IOException ex) {
			JOptionPane.showMessageDialog(this,
					"儲存失敗：" + ex.getMessage(),
					"錯誤",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	// Step 2 + Step 3: 使用 Sobel 找出高梯度 signal points 並輸出灰階邊緣圖
	private BufferedImage detectSignalEdgesAsGray(BufferedImage src, int threshold) {
		int w = src.getWidth();
		int h = src.getHeight();

		int[][] gray = new int[h][w];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int rgb = src.getRGB(x, y);
				int r = (rgb >> 16) & 0xff;
				int g = (rgb >> 8) & 0xff;
				int b = rgb & 0xff;
				gray[y][x] = (int) (0.299 * r + 0.587 * g + 0.114 * b);
			}
		}

		int[][] gxKernel = {
				{-1, 0, 1},
				{-2, 0, 2},
				{-1, 0, 1}
		};
		int[][] gyKernel = {
				{-1, -2, -1},
				{0, 0, 0},
				{1, 2, 1}
		};

		BufferedImage edges = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

		for (int y = 1; y < h - 1; y++) {
			for (int x = 1; x < w - 1; x++) {
				int gx = 0;
				int gy = 0;
				for (int ky = -1; ky <= 1; ky++) {
					for (int kx = -1; kx <= 1; kx++) {
						int val = gray[y + ky][x + kx];
						gx += val * gxKernel[ky + 1][kx + 1];
						gy += val * gyKernel[ky + 1][kx + 1];
					}
				}

				int magnitude = (int) Math.min(255, Math.sqrt((double) gx * gx + (double) gy * gy));
				int edgeValue = magnitude >= threshold ? 0 : 255; // 邊緣設為黑色，其餘白色
				int rgb = (edgeValue << 16) | (edgeValue << 8) | edgeValue;
				edges.setRGB(x, y, rgb);
			}
		}

		// 邊框補白
		for (int x = 0; x < w; x++) {
			edges.setRGB(x, 0, 0xffffff);
			edges.setRGB(x, h - 1, 0xffffff);
		}
		for (int y = 0; y < h; y++) {
			edges.setRGB(0, y, 0xffffff);
			edges.setRGB(w - 1, y, 0xffffff);
		}

		return edges;
	}

	private BufferedImage applyStyle(BufferedImage src, BufferedImage edgeGray, String style) {
		BufferedImage smooth;
		BufferedImage colorBase;

		switch (style) {
			case "柔和卡通":
				smooth = blur(src, 2);
				colorBase = posterize(smooth, 7);
				return combineWithEdges(colorBase, edgeGray, 0.72f);

			case "鉛筆素描":
				BufferedImage gray = toGray(src);
				BufferedImage inv = invert(gray);
				BufferedImage blurInv = blur(inv, 5);
				BufferedImage sketch = colorDodge(gray, blurInv);
				return combineWithEdges(sketch, edgeGray, 0.85f);

			case "普普藝術":
				smooth = blur(src, 1);
				colorBase = posterize(boostSaturation(smooth, 1.35f), 4);
				return combineWithEdges(colorBase, edgeGray, 0.78f);

			case "水墨風":
				BufferedImage inkBase = posterize(toGray(src), 5);
				return combineWithEdges(inkBase, edgeGray, 0.95f);

			case "AI 萌系電影風（參考圖）":
				return applyAICuteCinematicStyle(src, edgeGray);

			case "經典卡通":
			default:
				smooth = blur(src, 3);
				colorBase = posterize(smooth, 6);
				return combineWithEdges(colorBase, edgeGray, 0.82f);
		}
	}

	private BufferedImage applyAICuteCinematicStyle(BufferedImage src, BufferedImage edgeGray) {
		BufferedImage smooth = blur(src, 2);
		BufferedImage vivid = boostSaturation(smooth, 1.25f);
		BufferedImage coolTone = applyCoolOrangeTone(vivid, 0.22f);
		BufferedImage bloom = addBloom(coolTone, 6, 0.30f);
		BufferedImage finish = addVignette(bloom, 0.23f);
		return combineWithEdges(finish, edgeGray, 0.58f);
	}

	private BufferedImage applyCoolOrangeTone(BufferedImage src, float strength) {
		int w = src.getWidth();
		int h = src.getHeight();
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int rgb = src.getRGB(x, y);
				int r = (rgb >> 16) & 0xff;
				int g = (rgb >> 8) & 0xff;
				int b = rgb & 0xff;

				int brightness = (r + g + b) / 3;
				float cool = strength;
				float warm = strength * (brightness / 255f);

				r = clamp((int) (r * (1f - strength * 0.20f) + 45f * warm));
				g = clamp((int) (g * (1f - strength * 0.08f) + 18f * cool));
				b = clamp((int) (b * (1f + strength * 0.18f) + 50f * cool));

				out.setRGB(x, y, (r << 16) | (g << 8) | b);
			}
		}
		return out;
	}

	private BufferedImage addBloom(BufferedImage src, int blurRadius, float amount) {
		BufferedImage blurred = blur(src, blurRadius);
		int w = src.getWidth();
		int h = src.getHeight();
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int base = src.getRGB(x, y);
				int glow = blurred.getRGB(x, y);

				int br = (base >> 16) & 0xff;
				int bg = (base >> 8) & 0xff;
				int bb = base & 0xff;

				int gr = (glow >> 16) & 0xff;
				int gg = (glow >> 8) & 0xff;
				int gb = glow & 0xff;

				int r = clamp((int) (br + gr * amount));
				int g = clamp((int) (bg + gg * amount));
				int b = clamp((int) (bb + gb * amount));

				out.setRGB(x, y, (r << 16) | (g << 8) | b);
			}
		}
		return out;
	}

	private BufferedImage addVignette(BufferedImage src, float amount) {
		int w = src.getWidth();
		int h = src.getHeight();
		double cx = w / 2.0;
		double cy = h / 2.0;
		double maxDist = Math.sqrt(cx * cx + cy * cy);
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int rgb = src.getRGB(x, y);
				int r = (rgb >> 16) & 0xff;
				int g = (rgb >> 8) & 0xff;
				int b = rgb & 0xff;

				double dist = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
				float factor = (float) (1.0 - amount * Math.pow(dist / maxDist, 1.4));
				factor = Math.max(0.65f, Math.min(1f, factor));

				r = clamp((int) (r * factor));
				g = clamp((int) (g * factor));
				b = clamp((int) (b * factor));

				out.setRGB(x, y, (r << 16) | (g << 8) | b);
			}
		}
		return out;
	}

	private BufferedImage combineWithEdges(BufferedImage colorImg, BufferedImage edgeGray, float edgeStrength) {
		int w = colorImg.getWidth();
		int h = colorImg.getHeight();
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int c = colorImg.getRGB(x, y);
				int e = edgeGray.getRGB(x, y) & 0xff;

				int r = (c >> 16) & 0xff;
				int g = (c >> 8) & 0xff;
				int b = c & 0xff;

				float edgeFactor = (e / 255f);
				edgeFactor = (1f - edgeStrength) + edgeStrength * edgeFactor;

				r = clamp((int) (r * edgeFactor));
				g = clamp((int) (g * edgeFactor));
				b = clamp((int) (b * edgeFactor));

				out.setRGB(x, y, (r << 16) | (g << 8) | b);
			}
		}
		return out;
	}

	private BufferedImage blur(BufferedImage src, int radius) {
		if (radius <= 0) {
			return copy(src);
		}
		BufferedImage temp = copy(src);
		BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);

		int w = src.getWidth();
		int h = src.getHeight();

		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int rs = 0;
				int gs = 0;
				int bs = 0;
				int count = 0;

				for (int dy = -radius; dy <= radius; dy++) {
					int ny = y + dy;
					if (ny < 0 || ny >= h) continue;
					for (int dx = -radius; dx <= radius; dx++) {
						int nx = x + dx;
						if (nx < 0 || nx >= w) continue;
						int rgb = temp.getRGB(nx, ny);
						rs += (rgb >> 16) & 0xff;
						gs += (rgb >> 8) & 0xff;
						bs += rgb & 0xff;
						count++;
					}
				}

				int r = rs / count;
				int g = gs / count;
				int b = bs / count;
				out.setRGB(x, y, (r << 16) | (g << 8) | b);
			}
		}
		return out;
	}

	private BufferedImage posterize(BufferedImage src, int levels) {
		int w = src.getWidth();
		int h = src.getHeight();
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		int step = Math.max(1, 256 / levels);

		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int rgb = src.getRGB(x, y);
				int r = quantize((rgb >> 16) & 0xff, step);
				int g = quantize((rgb >> 8) & 0xff, step);
				int b = quantize(rgb & 0xff, step);
				out.setRGB(x, y, (r << 16) | (g << 8) | b);
			}
		}
		return out;
	}

	private BufferedImage toGray(BufferedImage src) {
		int w = src.getWidth();
		int h = src.getHeight();
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int rgb = src.getRGB(x, y);
				int r = (rgb >> 16) & 0xff;
				int g = (rgb >> 8) & 0xff;
				int b = rgb & 0xff;
				int gray = clamp((int) (0.299 * r + 0.587 * g + 0.114 * b));
				out.setRGB(x, y, (gray << 16) | (gray << 8) | gray);
			}
		}
		return out;
	}

	private BufferedImage invert(BufferedImage src) {
		int w = src.getWidth();
		int h = src.getHeight();
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int rgb = src.getRGB(x, y);
				int r = 255 - ((rgb >> 16) & 0xff);
				int g = 255 - ((rgb >> 8) & 0xff);
				int b = 255 - (rgb & 0xff);
				out.setRGB(x, y, (r << 16) | (g << 8) | b);
			}
		}
		return out;
	}

	private BufferedImage colorDodge(BufferedImage base, BufferedImage blend) {
		int w = base.getWidth();
		int h = base.getHeight();
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int b1 = base.getRGB(x, y) & 0xff;
				int b2 = blend.getRGB(x, y) & 0xff;
				int v = b2 == 255 ? 255 : Math.min(255, (b1 << 8) / (255 - b2));
				out.setRGB(x, y, (v << 16) | (v << 8) | v);
			}
		}
		return out;
	}

	private BufferedImage boostSaturation(BufferedImage src, float factor) {
		int w = src.getWidth();
		int h = src.getHeight();
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int rgb = src.getRGB(x, y);
				int r = (rgb >> 16) & 0xff;
				int g = (rgb >> 8) & 0xff;
				int b = rgb & 0xff;

				float[] hsb = Color.RGBtoHSB(r, g, b, null);
				hsb[1] = Math.min(1f, hsb[1] * factor);
				int outRgb = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
				out.setRGB(x, y, outRgb & 0x00ffffff);
			}
		}
		return out;
	}

	private int quantize(int value, int step) {
		return clamp((value / step) * step + step / 2);
	}

	private int clamp(int v) {
		return Math.max(0, Math.min(255, v));
	}

	private BufferedImage copy(BufferedImage src) {
		BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D g2 = out.createGraphics();
		g2.drawImage(src, 0, 0, null);
		g2.dispose();
		return out;
	}

	private static class ImagePanel extends JPanel {
		private BufferedImage image;
		private final String placeholder;

		public ImagePanel(String placeholder) {
			this.placeholder = placeholder;
			setPreferredSize(new Dimension(600, 600));
			setBackground(new Color(248, 248, 250));
			setBorder(BorderFactory.createLineBorder(new Color(210, 210, 215), 1));
		}

		public void setImage(BufferedImage image) {
			this.image = image;
			repaint();
		}

		public BufferedImage getImage() {
			return image;
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			Graphics2D g2 = (Graphics2D) g;
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

			int w = getWidth();
			int h = getHeight();

			if (image == null) {
				g2.setColor(new Color(170, 170, 176));
				g2.setFont(g2.getFont().deriveFont(Font.BOLD, 18f));
				int strW = g2.getFontMetrics().stringWidth(placeholder);
				g2.drawString(placeholder, (w - strW) / 2, h / 2);

				g2.setStroke(new BasicStroke(2f));
				g2.drawRoundRect(25, 25, Math.max(50, w - 50), Math.max(50, h - 50), 20, 20);
				return;
			}

			int imgW = image.getWidth();
			int imgH = image.getHeight();
			double scale = Math.min((w - 30.0) / imgW, (h - 30.0) / imgH);
			int drawW = Math.max(1, (int) (imgW * scale));
			int drawH = Math.max(1, (int) (imgH * scale));
			int x = (w - drawW) / 2;
			int y = (h - drawH) / 2;

			Image scaled = image.getScaledInstance(drawW, drawH, Image.SCALE_SMOOTH);
			g2.drawImage(scaled, x, y, null);
		}
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			try {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			} catch (Exception ignored) {
			}
			new Main().setVisible(true);
		});
	}
}
