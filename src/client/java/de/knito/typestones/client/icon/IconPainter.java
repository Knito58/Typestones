package de.knito.typestones.client.icon;

import java.awt.image.BufferedImage;

/**
 * Paints an icon onto a canvas without any Minecraft class, so it can be tested on its own. A 16 px
 * sprite is enlarged exactly 3x with Scale3x (hard edges, diagonals rounded instead of stair-stepped),
 * then the silhouette's edge is blended into the canvas: only the alpha is softened, the colors inside
 * the icon stay crisp. Other sprite sizes are averaged to the icon size and get the same edge blending.
 */
public final class IconPainter {
	/** Pixels per stone side, and transparent pixels on each side of the icon: an eighth. */
	public static final int SIZE = 64;
	public static final int MARGIN = 8;
	public static final int INNER = SIZE - 2 * MARGIN;

	private IconPainter() {
	}

	/** The canvas (SIZE x SIZE, opaque ARGB) with the sprite (argb, width x height) in the middle. */
	public static BufferedImage paint(int[] canvas, int[] sprite, int width, int height) {
		int[] icon = width == 16 && height == 16 ? scale3x(sprite, 16, 16) : average(sprite, width, height, INNER);
		double[] alpha = new double[INNER * INNER];
		for (int i = 0; i < alpha.length; i++) {
			alpha[i] = (icon[i] >>> 24) / 255.0;
		}
		double[] soft = blur(blur(alpha, true), false);
		// Colors for pixels the blur reached from outside the icon: the average of the opaque ones around.
		BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				int back = canvas[y * SIZE + x];
				int ix = x - MARGIN;
				int iy = y - MARGIN;
				if (ix >= 0 && iy >= 0 && ix < INNER && iy < INNER) {
					int pixel = icon[iy * INNER + ix];
					double a = soft[iy * INNER + ix];
					if (a > 0) {
						int color = pixel >>> 24 != 0 ? pixel : neighbours(icon, ix, iy);
						back = mix(back, color, a);
					}
				}
				out.setRGB(x, y, 0xFF000000 | back);
			}
		}
		return out;
	}

	private static int mix(int back, int color, double a) {
		int r = (int) Math.round((back >> 16 & 0xFF) * (1 - a) + (color >> 16 & 0xFF) * a);
		int g = (int) Math.round((back >> 8 & 0xFF) * (1 - a) + (color >> 8 & 0xFF) * a);
		int b = (int) Math.round((back & 0xFF) * (1 - a) + (color & 0xFF) * a);
		return r << 16 | g << 8 | b;
	}

	/** Weighted by alpha, the average color of the eight neighbours; 0 when none is visible. */
	private static int neighbours(int[] icon, int x, int y) {
		double a = 0;
		double r = 0;
		double g = 0;
		double b = 0;
		for (int dy = -1; dy <= 1; dy++) {
			for (int dx = -1; dx <= 1; dx++) {
				int nx = x + dx;
				int ny = y + dy;
				if (nx < 0 || ny < 0 || nx >= INNER || ny >= INNER) {
					continue;
				}
				int pixel = icon[ny * INNER + nx];
				double w = pixel >>> 24;
				a += w;
				r += w * (pixel >> 16 & 0xFF);
				g += w * (pixel >> 8 & 0xFF);
				b += w * (pixel & 0xFF);
			}
		}
		return a == 0 ? 0 : 0xFF000000 | (int) Math.round(r / a) << 16 | (int) Math.round(g / a) << 8 | (int) Math.round(b / a);
	}

	/** [1 2 1] / 4 along one axis; the edge of the image counts as transparent. */
	private static double[] blur(double[] in, boolean horizontal) {
		double[] out = new double[in.length];
		for (int y = 0; y < INNER; y++) {
			for (int x = 0; x < INNER; x++) {
				double before = horizontal ? (x > 0 ? in[y * INNER + x - 1] : 0) : (y > 0 ? in[(y - 1) * INNER + x] : 0);
				double after = horizontal ? (x < INNER - 1 ? in[y * INNER + x + 1] : 0) : (y < INNER - 1 ? in[(y + 1) * INNER + x] : 0);
				out[y * INNER + x] = (before + 2 * in[y * INNER + x] + after) / 4;
			}
		}
		return out;
	}

	/** Pixels that count as the same color; every fully transparent pixel is alike. */
	private static int key(int pixel) {
		return pixel >>> 24 == 0 ? 0 : pixel;
	}

	/** Scale3x (AdvMAME3x): each pixel becomes 3 x 3, and corners are rounded where two neighbours agree. */
	static int[] scale3x(int[] src, int width, int height) {
		int[] out = new int[width * 3 * height * 3];
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int a = at(src, width, height, x - 1, y - 1);
				int b = at(src, width, height, x, y - 1);
				int c = at(src, width, height, x + 1, y - 1);
				int d = at(src, width, height, x - 1, y);
				int e = at(src, width, height, x, y);
				int f = at(src, width, height, x + 1, y);
				int g = at(src, width, height, x - 1, y + 1);
				int h = at(src, width, height, x, y + 1);
				int i = at(src, width, height, x + 1, y + 1);
				int[] p = {e, e, e, e, e, e, e, e, e};
				if (key(b) != key(h) && key(d) != key(f)) {
					boolean db = key(d) == key(b);
					boolean bf = key(b) == key(f);
					boolean dh = key(d) == key(h);
					boolean hf = key(h) == key(f);
					boolean ec = key(e) != key(c);
					boolean ea = key(e) != key(a);
					boolean eg = key(e) != key(g);
					boolean ei = key(e) != key(i);
					p[0] = db ? d : e;
					p[1] = (db && ec) || (bf && ea) ? b : e;
					p[2] = bf ? f : e;
					p[3] = (db && eg) || (dh && ea) ? d : e;
					p[5] = (bf && ei) || (hf && ec) ? f : e;
					p[6] = dh ? d : e;
					p[7] = (dh && ei) || (hf && eg) ? h : e;
					p[8] = hf ? f : e;
				}
				for (int k = 0; k < 9; k++) {
					out[(y * 3 + k / 3) * width * 3 + x * 3 + k % 3] = p[k];
				}
			}
		}
		return out;
	}

	private static int at(int[] src, int width, int height, int x, int y) {
		return src[Math.clamp(y, 0, height - 1) * width + Math.clamp(x, 0, width - 1)];
	}

	/**
	 * Every target pixel is the average of exactly the part of the source it covers, weighted by alpha:
	 * a whole-number ratio keeps pixels as they are, other ratios get soft edges.
	 */
	public static int[] average(int[] argb, int width, int height, int size) {
		int[] out = new int[size * size];
		for (int y = 0; y < size; y++) {
			double top = (double) y * height / size;
			double bottom = (double) (y + 1) * height / size;
			for (int x = 0; x < size; x++) {
				double left = (double) x * width / size;
				double right = (double) (x + 1) * width / size;
				double weight = 0;
				double alpha = 0;
				double red = 0;
				double green = 0;
				double blue = 0;
				for (int sy = (int) Math.floor(top); sy < Math.min(height, (int) Math.ceil(bottom)); sy++) {
					double wy = Math.min(bottom, sy + 1) - Math.max(top, sy);
					for (int sx = (int) Math.floor(left); sx < Math.min(width, (int) Math.ceil(right)); sx++) {
						double w = wy * (Math.min(right, sx + 1) - Math.max(left, sx));
						int pixel = argb[sy * width + sx];
						double a = (pixel >>> 24) * w;
						weight += w;
						alpha += a;
						red += a * (pixel >> 16 & 0xFF);
						green += a * (pixel >> 8 & 0xFF);
						blue += a * (pixel & 0xFF);
					}
				}
				if (alpha > 0 && weight > 0) {
					out[y * size + x] = (int) Math.round(alpha / weight) << 24 | (int) Math.round(red / alpha) << 16
							| (int) Math.round(green / alpha) << 8 | (int) Math.round(blue / alpha);
				}
			}
		}
		return out;
	}
}
