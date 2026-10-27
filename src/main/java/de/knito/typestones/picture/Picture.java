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

import javax.imageio.ImageIO;

/**
 * A picture for Typestones: {@link #PIXELS} x {@link #PIXELS} opaque RGB pixels per stone, over
 * {@link #cols()} x {@link #rows()} stones. Two pictures with the same pixels have the same {@link #hash()}.
 * Plain Java (AWT for PNG), like Letter.
 */
public final class Picture {
	/** Pixels per stone side: twice as sharp as vanilla paintings. */
	public static final int PIXELS = 32;
	/** Most stones per side; 16 x 16 stones are 512 x 512 pixels. */
	public static final int MAX_STONES = 16;

	private final int cols;
	private final int rows;
	/** Row by row, 0xRRGGBB. */
	private final int[] rgb;
	private final String hash;

	private Picture(int cols, int rows, int[] rgb) {
		if (cols < 1 || rows < 1 || cols > MAX_STONES || rows > MAX_STONES || rgb.length != cols * PIXELS * rows * PIXELS) {
			throw new IllegalArgumentException("Bad picture size " + cols + " x " + rows);
		}
		this.cols = cols;
		this.rows = rows;
		this.rgb = rgb;
		this.hash = hashOf(cols, rows, rgb);
	}

	/** The image scaled to fill cols x rows stones; it is cut at two sides to keep its proportions. */
	public static Picture fit(BufferedImage image, int cols, int rows) {
		int width = cols * PIXELS;
		int height = rows * PIXELS;
		// The largest part of the image with the stones' proportions, from its middle.
		int cropWidth = image.getWidth();
		int cropHeight = (int) Math.round((double) cropWidth * height / width);
		if (cropHeight > image.getHeight()) {
			cropHeight = image.getHeight();
			cropWidth = (int) Math.round((double) cropHeight * width / height);
		}
		BufferedImage source = image.getSubimage((image.getWidth() - cropWidth) / 2, (image.getHeight() - cropHeight) / 2,
				Math.max(1, cropWidth), Math.max(1, cropHeight));
		// Halving step by step keeps detail that a single big reduction would skip.
		while (source.getWidth() >= 2 * width && source.getHeight() >= 2 * height) {
			source = scaled(source, source.getWidth() / 2, source.getHeight() / 2);
		}
		BufferedImage result = scaled(source, width, height);
		int[] rgb = result.getRGB(0, 0, width, height, null, 0, width);
		for (int i = 0; i < rgb.length; i++) {
			rgb[i] &= 0xFFFFFF;
		}
		return new Picture(cols, rows, rgb);
	}

	private static BufferedImage scaled(BufferedImage source, int width, int height) {
		BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = target.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.drawImage(source, 0, 0, width, height, null);
		g.dispose();
		return target;
	}

	/** Reads a PNG as written by {@link #toPng()}; its size must be whole stones. */
	public static Picture fromPng(byte[] png) throws IOException {
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
		if (image == null || image.getWidth() % PIXELS != 0 || image.getHeight() % PIXELS != 0) {
			throw new IOException("Not a picture PNG");
		}
		int width = image.getWidth();
		int height = image.getHeight();
		int[] rgb = image.getRGB(0, 0, width, height, null, 0, width);
		for (int i = 0; i < rgb.length; i++) {
			rgb[i] &= 0xFFFFFF;
		}
		try {
			return new Picture(width / PIXELS, height / PIXELS, rgb);
		} catch (IllegalArgumentException e) {
			throw new IOException(e.getMessage(), e);
		}
	}

	public byte[] toPng() {
		BufferedImage image = new BufferedImage(width(), height(), BufferedImage.TYPE_INT_RGB);
		image.setRGB(0, 0, width(), height(), rgb, 0, width());
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

	public int width() {
		return cols * PIXELS;
	}

	public int height() {
		return rows * PIXELS;
	}

	/** 0xRRGGBB of the pixel at x, y of the whole picture. */
	public int rgb(int x, int y) {
		return rgb[y * width() + x];
	}

	public String hash() {
		return hash;
	}

	private static String hashOf(int cols, int rows, int[] rgb) {
		try {
			MessageDigest sha = MessageDigest.getInstance("SHA-256");
			sha.update(ByteBuffer.allocate(8).putInt(cols).putInt(rows).array());
			ByteBuffer pixels = ByteBuffer.allocate(rgb.length * 3);
			for (int pixel : rgb) {
				pixels.put((byte) (pixel >> 16)).put((byte) (pixel >> 8)).put((byte) pixel);
			}
			return HexFormat.of().formatHex(sha.digest(pixels.array()), 0, 16);
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
