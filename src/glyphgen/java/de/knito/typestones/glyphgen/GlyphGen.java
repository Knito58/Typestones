package de.knito.typestones.glyphgen;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import de.knito.typestones.block.FontFace;
import de.knito.typestones.glyph.GlyphSet;

/**
 * Turns font faces into glyph textures: one 16x16 PNG per character of {@link GlyphSet}, white ink on
 * a transparent background, so the block can tint it with any dye color.
 *
 * <p>A face is either a dot-matrix text file (fonts/classic.txt) or any TrueType/OpenType font file.
 * Every font file in the fonts folder becomes a face named after the file. A font from elsewhere,
 * e.g. one installed on the computer, is given with its path and a face name.
 *
 * <p>For faces that are a block ({@link FontFace}) it also writes the block's JSON files, see
 * {@link BlockAssets}.
 *
 * <p>Arguments: {@code <resources dir> <preview dir> <fonts dir> [<font file> <face name>]}.
 * With {@code -Dglyphgen.check=true} nothing is written; it fails when a texture is missing or differs.
 */
public final class GlyphGen {
	static final int SIZE = 16;
	/** Free pixels around the ink, so neighboring blocks don't make letters touch. */
	static final int MARGIN = 1;
	/** Fonts are drawn this much bigger and then reduced, to decide each pixel by its ink coverage. */
	static final int SUPERSAMPLE = 8;

	private GlyphGen() {
	}

	public static void main(String[] args) throws IOException, FontFormatException {
		if (args.length != 3 && args.length != 5) {
			System.err.println("Arguments: <resources dir> <preview dir> <fonts dir> [<font file> <face name>]");
			System.exit(2);
		}
		Path resources = Path.of(args[0]);
		Path textures = resources.resolve("assets/typestones/textures/block/glyph");
		Path previews = Path.of(args[1]);
		Map<String, Map<Character, BufferedImage>> faces = new HashMap<>();
		if (args.length == 5) {
			String face = checkFaceName(args[4]);
			faces.put(face, fromFont(Path.of(args[3])));
		} else {
			try (Stream<Path> files = Files.list(Path.of(args[2]))) {
				for (Path file : files.sorted().toList()) {
					String name = file.getFileName().toString();
					String face = name.replaceFirst("\\.[^.]*$", "").toLowerCase();
					if (name.endsWith(".txt")) {
						faces.put(checkFaceName(face), fromDotMatrix(file));
					} else if (name.matches("(?i).*\\.(ttf|otf)$")) {
						faces.put(checkFaceName(face), fromFont(file));
					}
				}
			}
		}
		Map<String, String> json = new LinkedHashMap<>();
		for (FontFace face : FontFace.values()) {
			if (!faces.containsKey(face.folder)) {
				throw new IllegalArgumentException("Block face " + face.folder + " has no font file in the fonts folder");
			}
			json.putAll(BlockAssets.of(face));
		}
		if (Boolean.getBoolean("glyphgen.check")) {
			check(textures, faces, resources, json);
			return;
		}
		for (var file : json.entrySet()) {
			Path path = resources.resolve(file.getKey());
			Files.createDirectories(path.getParent());
			Files.writeString(path, file.getValue(), StandardCharsets.UTF_8);
		}
		for (var face : faces.entrySet()) {
			Path dir = textures.resolve(face.getKey());
			Files.createDirectories(dir);
			for (var glyph : face.getValue().entrySet()) {
				ImageIO.write(glyph.getValue(), "png", dir.resolve(GlyphSet.textureName(glyph.getKey()) + ".png").toFile());
			}
			Files.createDirectories(previews);
			ImageIO.write(preview(face.getValue()), "png", previews.resolve(face.getKey() + ".png").toFile());
			System.out.println("Face " + face.getKey() + ": " + face.getValue().size() + " glyphs in " + dir);
		}
		System.out.println(json.size() + " JSON files for the blocks of " + FontFace.values().length + " faces");
	}

	/** Compares pixels, not file bytes, since PNG files from different Java versions may differ. */
	private static void check(Path textures, Map<String, Map<Character, BufferedImage>> faces, Path resources,
			Map<String, String> json) throws IOException {
		List<String> problems = new ArrayList<>();
		for (var file : json.entrySet()) {
			Path path = resources.resolve(file.getKey());
			if (!Files.exists(path)) {
				problems.add("missing " + path);
			} else if (!Files.readString(path, StandardCharsets.UTF_8).equals(file.getValue())) {
				problems.add("differs " + path);
			}
		}
		for (var face : faces.entrySet()) {
			for (var glyph : face.getValue().entrySet()) {
				Path file = textures.resolve(face.getKey()).resolve(GlyphSet.textureName(glyph.getKey()) + ".png");
				if (!Files.exists(file)) {
					problems.add("missing " + file);
				} else if (!samePixels(ImageIO.read(file.toFile()), glyph.getValue())) {
					problems.add("differs " + file);
				}
			}
		}
		if (!problems.isEmpty()) {
			problems.forEach(System.err::println);
			System.err.println("Glyph textures or block files are out of date: run ./gradlew generateGlyphs and commit them.");
			System.exit(1);
		}
		System.out.println("Glyph textures and block files are up to date.");
	}

	private static boolean samePixels(BufferedImage a, BufferedImage b) {
		if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
			return false;
		}
		for (int y = 0; y < a.getHeight(); y++) {
			for (int x = 0; x < a.getWidth(); x++) {
				int pa = a.getRGB(x, y), pb = b.getRGB(x, y);
				// Fully transparent pixels are equal whatever color they hold.
				if (pa != pb && ((pa | pb) >>> 24) != 0) {
					return false;
				}
			}
		}
		return true;
	}

	/** Face names become texture folders and later block names, so they must be valid resource paths. */
	private static String checkFaceName(String face) {
		if (!face.matches("[a-z0-9_]+")) {
			throw new IllegalArgumentException("Face name \"" + face + "\" may only use a-z, 0-9 and _");
		}
		return face;
	}

	/** Every character of the set except the space, which needs no texture. */
	private static List<Character> inked() {
		List<Character> chars = new ArrayList<>();
		for (int i = 0; i < GlyphSet.size(); i++) {
			if (GlyphSet.charAt(i) != ' ') {
				chars.add(GlyphSet.charAt(i));
			}
		}
		return chars;
	}

	/**
	 * Reads a dot-matrix face: a line "U+0041 A" per glyph, followed by its rows of '#' (ink) and '.'.
	 * Each dot becomes a square of pixels, as big as fits, and the glyph is centered.
	 */
	static Map<Character, BufferedImage> fromDotMatrix(Path file) throws IOException {
		Map<Character, List<String>> rows = new HashMap<>();
		List<String> current = null;
		for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
			if (line.startsWith("U+")) {
				current = new ArrayList<>();
				rows.put((char) Integer.parseInt(line.substring(2, 6), 16), current);
			} else if (current != null && line.matches("[#.]+")) {
				current.add(line);
			}
		}
		Map<Character, BufferedImage> glyphs = new HashMap<>();
		List<Character> missing = new ArrayList<>();
		for (char c : inked()) {
			List<String> dots = rows.get(c);
			if (dots == null || dots.isEmpty()) {
				missing.add(c);
				continue;
			}
			int width = dots.getFirst().length();
			int height = dots.size();
			int inner = SIZE - 2 * MARGIN;
			int scale = Math.max(1, Math.min(inner / width, inner / height));
			int left = (SIZE - width * scale) / 2;
			int top = (SIZE - height * scale) / 2;
			BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					if (dots.get(y).charAt(x) == '#') {
						fill(image, left + x * scale, top + y * scale, scale);
					}
				}
			}
			glyphs.put(c, image);
		}
		failOnMissing(file, missing);
		return glyphs;
	}

	private static void fill(BufferedImage image, int x0, int y0, int scale) {
		for (int y = y0; y < y0 + scale; y++) {
			for (int x = x0; x < x0 + scale; x++) {
				image.setRGB(x, y, 0xFFFFFFFF);
			}
		}
	}

	/**
	 * Draws every glyph with a TrueType or OpenType font. All glyphs share one font size and one
	 * baseline, chosen so the tallest and the widest glyph of the set still fit, so letters of one face
	 * line up when blocks stand next to each other.
	 */
	static Map<Character, BufferedImage> fromFont(Path file) throws IOException, FontFormatException {
		Font base = Font.createFont(Font.TRUETYPE_FONT, file.toFile());
		List<Character> missing = new ArrayList<>();
		for (char c : inked()) {
			if (!base.canDisplay(c)) {
				missing.add(c);
			}
		}
		failOnMissing(file, missing);

		// Measure at a reference size, then scale so the extremes fit into the inner square.
		FontRenderContext frc = new FontRenderContext(null, true, true);
		Font ref = base.deriveFont(100f);
		double top = 0, bottom = 0, widest = 0;
		for (char c : inked()) {
			Rectangle2D b = ref.createGlyphVector(frc, String.valueOf(c)).getVisualBounds();
			top = Math.min(top, b.getMinY());
			bottom = Math.max(bottom, b.getMaxY());
			widest = Math.max(widest, b.getWidth());
		}
		int inner = (SIZE - 2 * MARGIN) * SUPERSAMPLE;
		double scale = Math.min(inner / (bottom - top), inner / widest);
		Font font = base.deriveFont((float) (100 * scale));
		double baseline = MARGIN * SUPERSAMPLE + (inner - (bottom - top) * scale) / 2 - top * scale;

		Map<Character, BufferedImage> glyphs = new HashMap<>();
		int big = SIZE * SUPERSAMPLE;
		for (char c : inked()) {
			BufferedImage canvas = new BufferedImage(big, big, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = canvas.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
			g.setColor(Color.WHITE);
			GlyphVector vector = font.createGlyphVector(g.getFontRenderContext(), String.valueOf(c));
			Rectangle2D b = vector.getVisualBounds();
			float x = (float) ((big - b.getWidth()) / 2 - b.getMinX());
			g.drawGlyphVector(vector, x, (float) baseline);
			g.dispose();
			glyphs.put(c, reduce(canvas));
		}
		return glyphs;
	}

	/** A pixel gets ink when at least half of its supersampled area is covered. */
	private static BufferedImage reduce(BufferedImage canvas) {
		BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		int area = SUPERSAMPLE * SUPERSAMPLE;
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				int coverage = 0;
				for (int sy = 0; sy < SUPERSAMPLE; sy++) {
					for (int sx = 0; sx < SUPERSAMPLE; sx++) {
						coverage += canvas.getRGB(x * SUPERSAMPLE + sx, y * SUPERSAMPLE + sy) >>> 24;
					}
				}
				if (coverage * 2 >= area * 255) {
					image.setRGB(x, y, 0xFFFFFFFF);
				}
			}
		}
		return image;
	}

	private static void failOnMissing(Path file, List<Character> missing) {
		if (!missing.isEmpty()) {
			StringBuilder list = new StringBuilder();
			missing.forEach(c -> list.append(' ').append(c));
			throw new IllegalArgumentException(file + " has no glyph for:" + list);
		}
	}

	/** All glyphs of a face, enlarged 4x on a dark background, in the order of the set. */
	private static BufferedImage preview(Map<Character, BufferedImage> glyphs) {
		int zoom = 4, cell = (SIZE + 2) * zoom, perRow = 16;
		List<Character> chars = inked();
		int rows = (chars.size() + perRow - 1) / perRow;
		BufferedImage sheet = new BufferedImage(perRow * cell, rows * cell, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = sheet.createGraphics();
		g.setColor(new Color(0x3A3A3A));
		g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
		for (int i = 0; i < chars.size(); i++) {
			int x = (i % perRow) * cell + zoom, y = (i / perRow) * cell + zoom;
			g.setColor(new Color(0x6E6E6E));
			g.fillRect(x, y, SIZE * zoom, SIZE * zoom);
			g.drawImage(glyphs.get(chars.get(i)), x, y, SIZE * zoom, SIZE * zoom, null);
		}
		g.dispose();
		return sheet;
	}
}
