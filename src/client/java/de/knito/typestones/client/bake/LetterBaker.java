package de.knito.typestones.client.bake;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.jspecify.annotations.Nullable;

import de.knito.typestones.letter.Letter;
import de.knito.typestones.typewriter.FontSettings;

/**
 * Bakes letters from the fonts installed on this computer, with Java's own font rendering, so any
 * script the computer has fonts for works. Like GlyphGen, it draws each glyph much bigger and gives a
 * pixel ink when at least half of it is covered.
 *
 * <p>All letters of one font and settings share one font size and one baseline, taken from the font's
 * ascent and descent rather than from the typed text, so the same character always gives the same
 * letter (and hash). Each glyph is centered horizontally in its cell.
 */
public final class LetterBaker {
	private static final int SUPERSAMPLE = 8;
	/** Font size the metrics are measured at. */
	private static final float REFERENCE = 100f;
	private static final int MAX_CACHED = 2048;

	private record Key(FontSettings settings, int codePoint) {
	}

	/** Baked letters; empty for a blank glyph. Only used on the render thread. */
	private static final Map<Key, Optional<Letter>> CACHE = new HashMap<>();
	private static final Map<String, Map<Integer, Font>> FONTS = new HashMap<>();
	private static @Nullable List<String> families;

	private LetterBaker() {
	}

	/** The font families installed on this computer, sorted; asked once per game. */
	public static List<String> families() {
		if (families == null) {
			Set<String> sorted = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
			sorted.addAll(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(Locale.ROOT)));
			families = List.copyOf(new ArrayList<>(sorted));
		}
		return families;
	}

	/**
	 * The font in a style at the reference size, or null when this computer doesn't have the family.
	 * Java takes the family's Bold or Italic face when installed and slants or thickens the regular one
	 * otherwise. The style values of {@link FontSettings} are those of {@link Font}.
	 */
	private static @Nullable Font font(String family, int style) {
		if (!families().contains(family)) {
			return null;
		}
		return FONTS.computeIfAbsent(family, f -> new HashMap<>()).computeIfAbsent(style, s -> new Font(family, s, (int) REFERENCE));
	}

	public static boolean has(String family) {
		return font(family, Font.PLAIN) != null;
	}

	public static boolean canDisplay(String family, int codePoint) {
		Font font = font(family, Font.PLAIN);
		return font != null && font.canDisplay(codePoint);
	}

	/** The letter of one character, or null when the glyph has no ink (a space) or the font is missing. */
	public static @Nullable Letter bake(FontSettings settings, int codePoint) {
		Key key = new Key(settings.withJoined(false), codePoint);
		Optional<Letter> cached = CACHE.get(key);
		if (cached == null) {
			if (CACHE.size() >= MAX_CACHED) {
				CACHE.clear();
			}
			cached = Optional.ofNullable(draw(settings, codePoint));
			CACHE.put(key, cached);
		}
		return cached.orElse(null);
	}

	/**
	 * The whole line shaped as one and cut into cells, one per stone; empty when the font is missing.
	 * Lines are only baked when Start is pressed, so they aren't cached.
	 */
	public static List<LineBaker.Cell> bakeLine(FontSettings settings, String text) {
		Font base = font(settings.font(), settings.style());
		return base == null ? List.of() : LineBaker.bakeLine(base, settings.size(), settings.scale(), settings.baseline(), text,
				settings.align(), settings.width());
	}

	/** True when the text is in a script whose letters change with their neighbours, like Arabic. */
	public static boolean needsShaping(String text) {
		return LineBaker.needsShaping(text);
	}

	/** Where the baseline lies, in letter pixels from the top; for the ruler in the Typewriter screen. */
	public static double baseline(FontSettings settings) {
		Font font = font(settings.font(), settings.style());
		return font == null ? settings.size() * 0.75 : layout(font, settings)[1] / SUPERSAMPLE;
	}

	/** {font size, baseline y} on the supersampled canvas. */
	private static double[] layout(Font font, FontSettings settings) {
		return LineBaker.layout(font, settings.size(), settings.scale(), settings.baseline());
	}

	private static @Nullable Letter draw(FontSettings settings, int codePoint) {
		Font base = font(settings.font(), settings.style());
		if (base == null) {
			return null;
		}
		double[] layout = layout(base, settings);
		Font font = base.deriveFont((float) layout[0]);
		int big = settings.size() * SUPERSAMPLE;
		BufferedImage canvas = new BufferedImage(big, big, BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D g = canvas.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		g.setColor(Color.WHITE);
		GlyphVector vector = font.createGlyphVector(g.getFontRenderContext(), new String(Character.toChars(codePoint)));
		Rectangle2D bounds = vector.getVisualBounds();
		g.drawGlyphVector(vector, (float) ((big - bounds.getWidth()) / 2 - bounds.getMinX()), (float) layout[1]);
		g.dispose();
		return reduce(canvas.getRaster(), settings.size());
	}

	/** A pixel gets ink when at least half of its supersampled area is covered; null without any ink. */
	private static @Nullable Letter reduce(Raster raster, int size) {
		return LineBaker.reduce(raster, 0, size);
	}
}
