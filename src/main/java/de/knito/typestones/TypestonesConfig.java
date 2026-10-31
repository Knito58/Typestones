package de.knito.typestones;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;

import de.knito.typestones.picture.Picture;

/**
 * The settings in config/typestones.properties, editable in game through Mod Menu.
 *
 * <p>Resolution and picture size are used where pictures are made, so on a dedicated server the server's
 * file decides them. The view distance only matters for the game that draws the pictures.
 *
 * @param pictureResolution pixels per stone side of new pictures: 16, 32 or 64. Pictures keep the
 *                          resolution they were made with, so changing it never touches existing stones.
 * @param maxCols           most stones a new picture may be wide
 * @param maxRows           most stones a new picture may be high
 * @param viewChunks        how far away picture stones are still drawn, in chunks
 */
public record TypestonesConfig(int pictureResolution, int maxCols, int maxRows, int viewChunks) {
	public static final List<Integer> RESOLUTIONS = List.of(16, 32, 64);
	/** Above this, many pictures in view cost noticeable frames and memory. */
	public static final int RESOLUTION_WARNING = 64;
	public static final int MAX_VIEW_CHUNKS = 16;

	public static final TypestonesConfig DEFAULT = new TypestonesConfig(32, 8, 8, 4);

	private static volatile TypestonesConfig current = DEFAULT;

	public TypestonesConfig {
		pictureResolution = RESOLUTIONS.contains(pictureResolution) ? pictureResolution : DEFAULT.pictureResolution;
		maxCols = Math.clamp(maxCols, 1, Picture.MAX_STONES);
		maxRows = Math.clamp(maxRows, 1, Picture.MAX_STONES);
		viewChunks = Math.clamp(viewChunks, 1, MAX_VIEW_CHUNKS);
	}

	public static TypestonesConfig get() {
		return current;
	}

	/** View distance of picture stones in blocks. */
	public int viewBlocks() {
		return viewChunks * 16;
	}

	public static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(Typestones.MOD_ID + ".properties");
	}

	/** Reads the file; a missing file is written with the defaults, so players find it to edit. */
	public static void load() {
		Path file = file();
		if (!Files.exists(file)) {
			set(DEFAULT);
			return;
		}
		Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			properties.load(reader);
		} catch (IOException e) {
			Typestones.LOGGER.warn("Can't read {}, using defaults: {}", file, e.toString());
			return;
		}
		current = new TypestonesConfig(
				number(properties, "picture_resolution", DEFAULT.pictureResolution),
				number(properties, "picture_max_cols", DEFAULT.maxCols),
				number(properties, "picture_max_rows", DEFAULT.maxRows),
				number(properties, "picture_view_chunks", DEFAULT.viewChunks));
	}

	/** Uses the settings from now on and writes them to the file. */
	public static void set(TypestonesConfig config) {
		current = config;
		Path file = file();
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				writer.write("""
						# Typestones settings. Mod Menu edits them in game.
						# Pixels per stone side of NEW pictures: 16, 32 or 64. Existing pictures keep theirs.
						# 64 looks sharp but costs memory and frames when many pictures are in view.
						picture_resolution=%d
						# Largest new picture in stones (1 to 16).
						picture_max_cols=%d
						picture_max_rows=%d
						# Picture stones further away than this many chunks are not drawn (1 to 16).
						picture_view_chunks=%d
						""".formatted(config.pictureResolution, config.maxCols, config.maxRows, config.viewChunks));
			}
		} catch (IOException e) {
			Typestones.LOGGER.warn("Can't write {}: {}", file, e.toString());
		}
	}

	private static int number(Properties properties, String key, int fallback) {
		try {
			return Integer.parseInt(properties.getProperty(key, "").trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}
