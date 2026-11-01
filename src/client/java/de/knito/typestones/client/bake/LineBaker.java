package de.knito.typestones.client.bake;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import de.knito.typestones.letter.Letter;

/**
 * Bakes a whole line of text as one piece ("Joined Text"): Java's text layout shapes it, so joined
 * scripts like Arabic get their connected forms and right-to-left order, narrow signs sit close to
 * their word and letters are as wide as the font makes them. The finished line is then cut into
 * square cells, one per stone, from the left. A right-to-left line ends flush with the last stone's
 * right edge, so it reads right on the wall. Plain Java (no Minecraft) so it can be tried on its own.
 */
public final class LineBaker {
	public static final int SUPERSAMPLE = 8;
	/** Font size the metrics are measured at. */
	public static final float REFERENCE = 100f;
	/** Longest label of a stone, as in LetterInfo.MAX_TEXT. */
	private static final int MAX_LABEL = 16;
	private static final String CUT = "…";
	/** A character counts as touching a stone when this share of its width lies on it. */
	private static final double TOUCH = 0.12;
	/** Scripts whose letters change with their neighbours or run right to left. */
	private static final Set<Character.UnicodeScript> SHAPED = Set.of(
			Character.UnicodeScript.ARABIC, Character.UnicodeScript.HEBREW, Character.UnicodeScript.SYRIAC,
			Character.UnicodeScript.THAANA, Character.UnicodeScript.NKO, Character.UnicodeScript.DEVANAGARI,
			Character.UnicodeScript.BENGALI, Character.UnicodeScript.GURMUKHI, Character.UnicodeScript.GUJARATI,
			Character.UnicodeScript.ORIYA, Character.UnicodeScript.TAMIL, Character.UnicodeScript.TELUGU,
			Character.UnicodeScript.KANNADA, Character.UnicodeScript.MALAYALAM, Character.UnicodeScript.SINHALA,
			Character.UnicodeScript.THAI, Character.UnicodeScript.LAO, Character.UnicodeScript.TIBETAN,
			Character.UnicodeScript.MYANMAR, Character.UnicodeScript.KHMER);

	/**
	 * One stone of a joined line.
	 *
	 * @param letter the pixels, or null when the stone stays blank
	 * @param text   the characters the stone touches, in reading order; a letter that goes on beyond the stone
	 *               has "…" on that side, e.g. "…ung" or "nu…"
	 */
	public record Cell(Letter letter, String text) {
	}

	private LineBaker() {
	}

	/** True when the text has characters that can't be set one by one: joined or right-to-left scripts. */
	public static boolean needsShaping(String text) {
		return text.codePoints().anyMatch(c -> SHAPED.contains(Character.UnicodeScript.of(c)));
	}

	/** {font size, baseline y} on the supersampled canvas of one cell. */
	public static double[] layout(Font base, int size, int scale, int baseline) {
		LineMetrics metrics = base.deriveFont(REFERENCE).getLineMetrics("Hg", new FontRenderContext(null, true, true));
		double ascent = metrics.getAscent();
		double descent = metrics.getDescent();
		double cell = size * SUPERSAMPLE;
		double factor = cell / (ascent + descent) * scale / 100.0;
		double baselineY = cell / 2 + (ascent - descent) / 2 * factor - cell * baseline / 100.0;
		return new double[] {REFERENCE * factor, baselineY};
	}

	/** A pixel gets ink when at least half of its supersampled area is covered; null without any ink. */
	public static Letter reduce(Raster raster, int xOffset, int size) {
		boolean[] ink = new boolean[size * size];
		boolean any = false;
		int[] samples = new int[SUPERSAMPLE * SUPERSAMPLE];
		int half = SUPERSAMPLE * SUPERSAMPLE * 255 / 2;
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				raster.getSamples(xOffset + x * SUPERSAMPLE, y * SUPERSAMPLE, SUPERSAMPLE, SUPERSAMPLE, 0, samples);
				int coverage = 0;
				for (int sample : samples) {
					coverage += sample;
				}
				if (coverage >= half) {
					ink[y * size + x] = true;
					any = true;
				}
			}
		}
		return any ? Letter.of(size, ink) : null;
	}

	/**
	 * The stones of the line, left to right as they are placed. The font is at the reference size; size
	 * is the stone's pixels (16, 32 or 64), scale and baseline are percentages as in the screen. Trailing
	 * spaces are dropped. Empty for an empty line.
	 *
	 * <p>The line is at least width stones wide (0: as wide as it is), and align (0 left, 1 center, 2 right)
	 * says where it lies within them, down to the pixel. Empty stones in front are kept, empty ones
	 * after the line are not.
	 */
	public static List<Cell> bakeLine(Font base, int size, int scale, int baseline, String text, int align, int width) {
		String line = text.stripTrailing();
		if (line.isEmpty()) {
			return List.of();
		}
		double[] layout = layout(base, size, scale, baseline);
		Font font = base.deriveFont((float) layout[0]);
		FontRenderContext context = new FontRenderContext(null, true, true);
		TextLayout shaped = new TextLayout(line, font, context);

		int cell = size * SUPERSAMPLE;
		Rectangle2D ink = shaped.getBounds();
		double left = Math.max(0, -ink.getMinX());
		double extent = left + Math.max(shaped.getAdvance(), ink.getMaxX());
		int cells = Math.max(Math.max(1, width), (int) Math.ceil(extent / cell - 1e-9));
		boolean leftToRight = shaped.isLeftToRight();
		double slack = cells * (double) cell - extent;
		double origin = left + (align == 2 ? slack : align == 1 ? slack / 2 : 0);

		BufferedImage canvas = new BufferedImage(cells * cell, cell, BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D g = canvas.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		g.setColor(Color.WHITE);
		shaped.draw(g, (float) origin, (float) layout[1]);
		g.dispose();

		List<Cluster> clusters = clusters(line, shaped, origin);
		List<Cell> result = new ArrayList<>(cells);
		for (int i = 0; i < cells; i++) {
			Letter letter = reduce(canvas.getRaster(), i * cell, size);
			result.add(new Cell(letter, letter == null ? "" : label(clusters, i * (double) cell, (i + 1) * (double) cell, leftToRight)));
		}
		// Rounding up or the width can leave empty stones at the end; the pencil needn't lay them.
		while (result.size() > 1 && result.get(result.size() - 1).letter() == null) {
			result.remove(result.size() - 1);
		}
		return result;
	}

	/** A character with the marks that belong to it, and where it lies on the canvas. */
	private record Cluster(String text, double x0, double x1) {
	}

	private static List<Cluster> clusters(String line, TextLayout shaped, double origin) {
		List<Cluster> clusters = new ArrayList<>();
		BreakIterator breaks = BreakIterator.getCharacterInstance(Locale.ROOT);
		breaks.setText(line);
		int start = breaks.first();
		for (int end = breaks.next(); end != BreakIterator.DONE; start = end, end = breaks.next()) {
			Rectangle2D box = shaped.getLogicalHighlightShape(start, end).getBounds2D();
			String text = line.substring(start, end);
			if (box.getWidth() <= 0 && !text.isBlank() && !clusters.isEmpty()) {
				// TextLayout gives no bounds inside some ligatures with marks: ride along with the previous one.
				Cluster last = clusters.remove(clusters.size() - 1);
				clusters.add(new Cluster(last.text() + text, last.x0(), last.x1()));
				continue;
			}
			clusters.add(new Cluster(text, origin + box.getMinX(), origin + box.getMaxX()));
		}
		return clusters;
	}

	/** The characters touching [from, to), in reading order, cut ones marked with "…" on the side they go on. */
	private static String label(List<Cluster> clusters, double from, double to, boolean leftToRight) {
		StringBuilder label = new StringBuilder();
		for (Cluster cluster : clusters) {
			double width = cluster.x1() - cluster.x0();
			double overlap = Math.min(cluster.x1(), to) - Math.max(cluster.x0(), from);
			if (width <= 0 || overlap <= TOUCH * width) {
				continue;
			}
			if (cluster.text().isBlank()) {
				label.append(' ');
				continue;
			}
			boolean cutLeft = cluster.x0() < from - 0.5;
			boolean cutRight = cluster.x1() > to + 0.5;
			// In a right-to-left line the left neighbour comes later in reading order.
			boolean before = leftToRight ? cutLeft : cutRight;
			boolean after = leftToRight ? cutRight : cutLeft;
			label.append(before ? CUT : "").append(cluster.text()).append(after ? CUT : "");
		}
		String text = label.toString().replaceAll(" +", " ").strip();
		if (text.length() > MAX_LABEL) {
			int end = MAX_LABEL - CUT.length();
			if (Character.isHighSurrogate(text.charAt(end - 1))) {
				end--;
			}
			text = text.substring(0, end) + CUT;
		}
		return text.isEmpty() ? CUT : text;
	}
}
