package de.knito.typestones.picture;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javax.imageio.ImageIO;

/**
 * A picture for Typestones: {@link #pixels()} x {@link #pixels()} pixels per stone, over {@link #cols()} x
 * {@link #rows()} stones. A pixel is either a full color or transparent; transparent pixels show the
 * stone beneath. Two pictures with the same pixels have the same {@link #hash()}. Plain Java (AWT for
 * PNG), like Letter.
 *
 * <p>The resolution belongs to each picture, so a picture made at 32 px keeps it when the setting for
 * new pictures changes.
 */
public final class Picture {
	/** Pixels per stone side of the first pictures, and of every picture saved without a resolution. */
	public static final int PIXELS = 32;
	public static final List<Integer> RESOLUTIONS = List.of(16, 32, 64);
	/** Most stones per side; 16 x 16 stones at 64 px are 1024 x 1024 pixels. */
	public static final int MAX_STONES = 16;
	/** Like the Billboard Engine: alpha below this is transparent, the rest is full color. */
	public static final int ALPHA_CUT = 128;

	/** How an image is put into the stones. The names are those of the Billboard Engine. */
	public enum Mode {
		/** The whole image, proportions kept, centered; the rest is transparent. */
		FIT,
		/** The stones are covered, proportions kept; what sticks out at two sides is cut off. */
		FILL,
		/** The image is reshaped to the stones. */
		STRETCH;

		public String luaName() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static Optional<Mode> of(String name) {
			for (Mode mode : values()) {
				if (mode.luaName().equals(name)) {
					return Optional.of(mode);
				}
			}
			return Optional.empty();
		}
	}

	private final int cols;
	private final int rows;
	private final int pixels;
	/** Row by row, 0xAARRGGBB with alpha either 0xFF or 0 (then the whole pixel is 0). */
	private final int[] argb;
	private final boolean transparent;
	private final String hash;

	private Picture(int cols, int rows, int pixels, int[] argb) {
		if (cols < 1 || rows < 1 || cols > MAX_STONES || rows > MAX_STONES || !RESOLUTIONS.contains(pixels)
				|| argb.length != cols * pixels * rows * pixels) {
			throw new IllegalArgumentException("Bad picture size " + cols + " x " + rows + " at " + pixels + " px");
		}
		boolean anyTransparent = false;
		for (int i = 0; i < argb.length; i++) {
			if (argb[i] >>> 24 < ALPHA_CUT) {
				argb[i] = 0;
				anyTransparent = true;
			} else {
				argb[i] |= 0xFF000000;
			}
		}
		this.cols = cols;
		this.rows = rows;
		this.pixels = pixels;
		this.argb = argb;
		this.transparent = anyTransparent;
		this.hash = hashOf();
	}

	/** The image cut to fill cols x rows stones at 32 px, as the first pictures were made. */
	public static Picture fit(BufferedImage image, int cols, int rows) {
		return of(image, cols, rows, PIXELS, Mode.FILL);
	}

	/** The image put into cols x rows stones of pixels x pixels each. */
	public static Picture of(BufferedImage image, int cols, int rows, int pixels, Mode mode) {
		int width = cols * pixels;
		int height = rows * pixels;
		BufferedImage source = argb(image);
		// Where the image goes in the target, and which part of the image.
		int x = 0;
		int y = 0;
		int w = width;
		int h = height;
		switch (mode) {
			case FILL -> {
				// The largest part of the image with the stones' proportions, from its middle.
				int cropWidth = source.getWidth();
				int cropHeight = (int) Math.round((double) cropWidth * height / width);
				if (cropHeight > source.getHeight()) {
					cropHeight = source.getHeight();
					cropWidth = (int) Math.round((double) cropHeight * width / height);
				}
				source = source.getSubimage((source.getWidth() - cropWidth) / 2, (source.getHeight() - cropHeight) / 2,
						Math.max(1, cropWidth), Math.max(1, cropHeight));
			}
			case FIT -> {
				double scale = Math.min((double) width / source.getWidth(), (double) height / source.getHeight());
				w = Math.clamp(Math.round(source.getWidth() * scale), 1, width);
				h = Math.clamp(Math.round(source.getHeight() * scale), 1, height);
				x = (width - w) / 2;
				y = (height - h) / 2;
			}
			case STRETCH -> {
			}
		}
		// Halving step by step keeps detail that a single big reduction would skip.
		while (source.getWidth() >= 2 * w && source.getHeight() >= 2 * h) {
			source = scaled(source, source.getWidth() / 2, source.getHeight() / 2);
		}
		BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = target.createGraphics();
		hints(g);
		g.drawImage(source, x, y, w, h, null);
		g.dispose();
		return new Picture(cols, rows, pixels, target.getRGB(0, 0, width, height, null, 0, width));
	}

	private static BufferedImage argb(BufferedImage image) {
		if (image.getType() == BufferedImage.TYPE_INT_ARGB) {
			return image;
		}
		BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = copy.createGraphics();
		g.drawImage(image, 0, 0, null);
		g.dispose();
		return copy;
	}

	private static BufferedImage scaled(BufferedImage source, int width, int height) {
		BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = target.createGraphics();
		hints(g);
		g.drawImage(source, 0, 0, width, height, null);
		g.dispose();
		return target;
	}

	private static void hints(Graphics2D g) {
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
	}

	/** Reads a PNG as written by {@link #toPng()}; its size must be cols x rows whole stones of 16, 32 or 64 px. */
	public static Picture fromPng(byte[] png, int cols, int rows) throws IOException {
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
		if (image == null || cols < 1 || rows < 1 || image.getWidth() % cols != 0) {
			throw new IOException("Not a picture PNG");
		}
		int width = image.getWidth();
		int height = image.getHeight();
		int pixels = width / cols;
		if (height != rows * pixels) {
			throw new IOException("Picture PNG of " + width + " x " + height + " doesn't fit " + cols + " x " + rows + " stones");
		}
		try {
			return new Picture(cols, rows, pixels, argb(image).getRGB(0, 0, width, height, null, 0, width));
		} catch (IllegalArgumentException e) {
			throw new IOException(e.getMessage(), e);
		}
	}

	/** RGB without alpha when the picture has no transparent pixel, as the first pictures were saved. */
	public byte[] toPng() {
		BufferedImage image = new BufferedImage(width(), height(), transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
		image.setRGB(0, 0, width(), height(), argb, 0, width());
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			ImageIO.write(image, "png", out);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return out.toByteArray();
	}

	public int cols() {
		return cols;
	}

	public int rows() {
		return rows;
	}

	/** Pixels per stone side. */
	public int pixels() {
		return pixels;
	}

	public int width() {
		return cols * pixels;
	}

	public int height() {
		return rows * pixels;
	}

	/** 0xRRGGBB of the pixel at x, y of the whole picture; 0 when it is transparent. */
	public int rgb(int x, int y) {
		return argb[y * width() + x] & 0xFFFFFF;
	}

	public boolean isTransparent(int x, int y) {
		return argb[y * width() + x] == 0;
	}

	/** True when at least one pixel is transparent. */
	public boolean hasTransparency() {
		return transparent;
	}

	public String hash() {
		return hash;
	}

	/**
	 * Opaque 32 px pictures hash as the first pictures did, so their stones and files stay valid. Other
	 * pictures add their resolution and a transparency mask.
	 */
	private String hashOf() {
		try {
			MessageDigest sha = MessageDigest.getInstance("SHA-256");
			sha.update(ByteBuffer.allocate(8).putInt(cols).putInt(rows).array());
			ByteBuffer rgb = ByteBuffer.allocate(argb.length * 3);
			for (int pixel : argb) {
				rgb.put((byte) (pixel >> 16)).put((byte) (pixel >> 8)).put((byte) pixel);
			}
			sha.update(rgb.array());
			if (pixels != PIXELS || transparent) {
				sha.update(ByteBuffer.allocate(4).putInt(pixels).array());
				byte[] mask = new byte[(argb.length + 7) / 8];
				for (int i = 0; i < argb.length; i++) {
					if (argb[i] == 0) {
						mask[i / 8] |= (byte) (1 << (i % 8));
					}
				}
				sha.update(mask);
			}
			return HexFormat.of().formatHex(sha.digest(), 0, 16);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("Java without SHA-256", e);
		}
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Picture other && hash.equals(other.hash);
	}

	@Override
	public int hashCode() {
		return hash.hashCode();
	}
}
