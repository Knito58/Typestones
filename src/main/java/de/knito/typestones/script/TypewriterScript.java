package de.knito.typestones.script;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Util;

import de.knito.typestones.Typestones;
import de.knito.typestones.TypestonesConfig;
import de.knito.typestones.letter.LetterLibrary;
import de.knito.typestones.picture.Picture;
import de.knito.typestones.picture.PictureInfo;
import de.knito.typestones.picture.PictureLibrary;
import de.knito.typestones.picture.PictureTile;
import de.knito.typestones.typewriter.TypewriterBlockEntity;
import de.knito.typestones.typewriter.TypewriterStatus;

/**
 * What programs can do with a Typewriter, in plain Java values, for every scripting mod: the Lua Box of
 * the Billboard API and CC: Tweaked wrap the same functions. The names follow the Billboard Engine.
 *
 * <p>Results are arrays of Lua return values: {@code {true}}, {@code {false, "reason"}}. Wrong arguments
 * throw {@link IllegalArgumentException}, which the wrappers turn into Lua errors. Everything is called
 * on the server thread; only reading an image runs beside it.
 */
public final class TypewriterScript {
	/** Larger image files are refused before they are read. */
	public static final long MAX_FILE_BYTES = 4L * 1024 * 1024;
	/** Images wider or higher than this are refused before they are decoded. */
	public static final int MAX_IMAGE_SIDE = 4096;
	/** Easy formats only. A GIF gives its first frame. */
	public static final Set<String> FORMATS = Set.of("png", "jpeg", "gif");

	private static final Object[] OK = {true};

	private final TypewriterBlockEntity typewriter;

	public TypewriterScript(TypewriterBlockEntity typewriter) {
		this.typewriter = typewriter;
	}

	// Text, as the Billboard Engine.

	public Object[] setText(String text) {
		typewriter.setText(text);
		return new Object[0];
	}

	public String getText() {
		return typewriter.text();
	}

	public Object[] go() {
		if (typewriter.isRunning()) {
			return fail("busy");
		}
		typewriter.startFromProgram(LetterLibrary.get(server()));
		TypewriterStatus status = typewriter.status();
		if (status == TypewriterStatus.RUNNING) {
			return OK;
		}
		return fail(switch (status) {
			case NO_TEXT -> "need_text";
			default -> reason(status);
		});
	}

	public Object[] stop() {
		if (!typewriter.isRunning()) {
			return fail("not_running");
		}
		typewriter.stop();
		return OK;
	}

	public boolean isRunning() {
		return typewriter.isRunning();
	}

	public boolean isPainting() {
		return typewriter.isPainting();
	}

	// Typewriter only.

	public String getStatus() {
		return reason(typewriter.status());
	}

	public Object[] getProgress() {
		return new Object[] {typewriter.made(), typewriter.total()};
	}

	public Object[] setRepeat(int repeat) {
		if (repeat < 1 || repeat > TypewriterBlockEntity.MAX_REPEAT) {
			throw new IllegalArgumentException("repeat must be 1 to " + TypewriterBlockEntity.MAX_REPEAT + ", got " + repeat);
		}
		typewriter.setRepeat(repeat);
		return new Object[0];
	}

	public int getRepeat() {
		return typewriter.repeat();
	}

	/** What a picture with these options takes and what the inputs hold now. Nothing is used. */
	public Map<String, Object> getCost(Map<String, ?> options) {
		Options parsed = Options.of(options);
		TypewriterBlockEntity.Cost cost = typewriter.pictureCost(parsed.cols * parsed.rows);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("stones", cost.stones());
		result.put("rgbDye", cost.rgbDye());
		result.put("haveStones", cost.haveStones());
		result.put("haveRgbDye", cost.haveRgbDye());
		result.put("ok", cost.enough() && !tooBig(parsed));
		TypewriterStatus missing = cost.missing();
		if (tooBig(parsed)) {
			result.put("missing", "too_big");
		} else if (missing != null) {
			result.put("missing", reason(missing));
		}
		return result;
	}

	/**
	 * Types a picture from image bytes. The image is read beside the server thread; the future gives
	 * {@code {true}}, {@code {true, "needs_..."}} when it started but waits for items, or {@code {false, reason}}.
	 *
	 * @param name  shown in the picture library
	 * @param bytes read beside the server thread, so a file is read there too
	 */
	public CompletableFuture<Object[]> drawImage(String name, ImageSource bytes, Map<String, ?> options, String maker) {
		Options parsed = Options.of(options);
		Object @Nullable [] refused = refused(parsed);
		if (refused != null) {
			return CompletableFuture.completedFuture(refused);
		}
		MinecraftServer server = server();
		int pixels = TypestonesConfig.get().pictureResolution();
		return CompletableFuture.supplyAsync(() -> {
			try {
				return (Object) Picture.of(read(bytes.read()), parsed.cols, parsed.rows, pixels, parsed.mode);
			} catch (ImageProblem problem) {
				return problem.reason;
			}
		}, Util.backgroundExecutor()).thenApplyAsync(result -> {
			if (!(result instanceof Picture picture)) {
				return fail((String) result);
			}
			// The world went on while the image was read.
			Object @Nullable [] late = refused(parsed);
			if (late != null) {
				return late;
			}
			return start(picture, name, parsed, maker, server);
		}, server);
	}

	private Object[] start(Picture picture, String name, Options options, String maker, MinecraftServer server) {
		PictureLibrary library = PictureLibrary.get(server);
		try {
			PictureInfo info = PictureInfo.of(picture, name, maker, LocalDate.now().toString());
			if (library.add(picture, info, PictureLibrary.folder(server)) == PictureLibrary.Added.FULL) {
				return fail("library_full");
			}
		} catch (IOException e) {
			Typestones.LOGGER.error("Can't save picture {}", picture.hash(), e);
			return fail("not_saved");
		}
		typewriter.startPicture(picture, options.plane);
		TypewriterStatus missing = typewriter.pictureCost(picture.cols() * picture.rows()).missing();
		return missing == null ? OK : new Object[] {true, reason(missing)};
	}

	/** Why a picture with these options can't start now, or null. */
	private Object @Nullable [] refused(Options options) {
		if (typewriter.isRemoved()) {
			return fail("not_attached");
		}
		if (tooBig(options)) {
			return fail("too_big");
		}
		if (typewriter.isRunning()) {
			return fail("busy");
		}
		if (typewriter.output() == null) {
			return fail("no_output");
		}
		return null;
	}

	private static boolean tooBig(Options options) {
		TypestonesConfig config = TypestonesConfig.get();
		return options.cols > config.maxCols() || options.rows > config.maxRows();
	}

	/** An image file's bytes as a picture source: size, dimensions and format are checked before decoding. */
	public static BufferedImage read(byte[] bytes) throws ImageProblem {
		if (bytes.length > MAX_FILE_BYTES) {
			throw new ImageProblem("file_too_large");
		}
		try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
			Iterator<ImageReader> readers = input == null ? null : ImageIO.getImageReaders(input);
			if (readers == null || !readers.hasNext()) {
				throw new ImageProblem("bad_image");
			}
			ImageReader reader = readers.next();
			try {
				if (!FORMATS.contains(reader.getFormatName().toLowerCase(Locale.ROOT))) {
					throw new ImageProblem("bad_image");
				}
				reader.setInput(input, true, true);
				if (reader.getWidth(0) > MAX_IMAGE_SIDE || reader.getHeight(0) > MAX_IMAGE_SIDE) {
					throw new ImageProblem("image_too_large");
				}
				return reader.read(0);
			} finally {
				reader.dispose();
			}
		} catch (IOException | RuntimeException e) {
			throw new ImageProblem("bad_image");
		}
	}

	/** Lua names of the statuses: "running", "needs_rgb_dye", ... */
	public static String reason(TypewriterStatus status) {
		return status.name().toLowerCase(Locale.ROOT);
	}

	private static Object[] fail(String reason) {
		return new Object[] {false, reason};
	}

	private MinecraftServer server() {
		if (typewriter.getLevel() == null || typewriter.getLevel().getServer() == null) {
			throw new IllegalStateException("Typewriter without a server");
		}
		return typewriter.getLevel().getServer();
	}

	/** Where the bytes of an image come from: a file of the Lua Box, or a program's string. */
	@FunctionalInterface
	public interface ImageSource {
		byte[] read() throws ImageProblem;

		/** A file, refused before reading when it is larger than {@link #MAX_FILE_BYTES}. */
		static ImageSource file(java.nio.file.Path file) {
			return () -> {
				try {
					if (java.nio.file.Files.size(file) > MAX_FILE_BYTES) {
						throw new ImageProblem("file_too_large");
					}
					return java.nio.file.Files.readAllBytes(file);
				} catch (IOException e) {
					throw new ImageProblem("not_found");
				}
			};
		}
	}

	/** Who holds a claimed Typewriter: one run of one program. */
	public interface Holder {
		boolean isActive();

		String describe();
	}

	/** Takes the Typewriter for this program. Never waits. */
	public Object[] claim(Holder holder) {
		Holder current = holder();
		if (current != null && !current.equals(holder)) {
			return new Object[] {false, current.describe()};
		}
		typewriter.setHolder(holder);
		return OK;
	}

	public Object[] release(Holder holder) {
		if (!holder.equals(holder())) {
			return new Object[] {false};
		}
		typewriter.setHolder(null);
		return OK;
	}

	public Object[] isClaimed() {
		Holder current = holder();
		return current == null ? new Object[] {false} : new Object[] {true, current.describe()};
	}

	/** Throws for a program that may not change the Typewriter now, as the Billboard Engine does. */
	public void checkMayChange(Holder caller) {
		Holder current = holder();
		if (current != null && !current.equals(caller)) {
			throw new IllegalArgumentException("typewriter is claimed by " + current.describe());
		}
	}

	private @Nullable Holder holder() {
		Holder current = typewriter.holder();
		if (current != null && !current.isActive()) {
			typewriter.setHolder(null);
			return null;
		}
		return current;
	}

	/** An image that can't become a picture, with the reason a program gets. */
	public static final class ImageProblem extends Exception {
		private static final long serialVersionUID = 1L;

		public final String reason;

		public ImageProblem(String reason) {
			super(reason, null, false, false);
			this.reason = reason;
		}
	}

	/**
	 * The options table of drawImage and getCost: {@code width} and {@code height} in stones,
	 * {@code fit} ("fit", "fill", "stretch"), {@code facing} ("wall", "floor", "ceiling"). The Billboard
	 * Engine's {@code dither} and {@code loop} are accepted and have no effect.
	 */
	public record Options(int cols, int rows, Picture.Mode mode, PictureTile.Plane plane) {
		public static Options of(Map<String, ?> options) {
			int cols = stones(options, "width");
			int rows = stones(options, "height");
			Object fit = options.get("fit");
			Picture.Mode mode = fit == null ? Picture.Mode.FIT : Picture.Mode.of(String.valueOf(fit))
					.orElseThrow(() -> new IllegalArgumentException("fit must be \"fit\", \"fill\" or \"stretch\", got " + fit));
			Object facing = options.get("facing");
			PictureTile.Plane plane = switch (facing == null ? "wall" : String.valueOf(facing)) {
				case "wall" -> PictureTile.Plane.WALL;
				case "floor", "ceiling" -> PictureTile.Plane.FLAT;
				default -> throw new IllegalArgumentException("facing must be \"wall\", \"floor\" or \"ceiling\", got " + facing);
			};
			return new Options(cols, rows, mode, plane);
		}

		private static int stones(Map<String, ?> options, String key) {
			if (!(options.get(key) instanceof Number number) || number.doubleValue() != Math.rint(number.doubleValue())) {
				throw new IllegalArgumentException("options." + key + " must be a whole number of stones");
			}
			long value = number.longValue();
			if (value < 1) {
				throw new IllegalArgumentException("options." + key + " must be at least 1, got " + value);
			}
			// Over the size setting is no error but the reason "too_big", as on the Billboard Engine.
			return (int) Math.min(value, 1024);
		}
	}
}
