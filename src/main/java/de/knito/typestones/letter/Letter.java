package de.knito.typestones.letter;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/**
 * A letter as a square of pixels that are ink or not, e.g. 16x16 or 32x32. It is drawn as flat
 * rectangles in the letter dye on the stone faces, so any number of letters works without textures.
 * Immutable; two letters with the same pixels have the same {@link #hash()}. Plain Java on purpose:
 * GlyphGen uses it without Minecraft on the classpath; the codecs are in {@link LibraryLetter}.
 */
public final class Letter {
	public static final int MAX_SIZE = 64;

	private final int size;
	/** Row by row from the top, one bit per pixel. */
	private final byte[] bits;
	private final String hash;
	private final List<Rect> rectangles;

	/** A rectangle of ink from (x0, y0) to (x1, y1), ends excluded, y counted from the top. */
	public record Rect(int x0, int y0, int x1, int y1) {
	}

	private Letter(int size, byte[] bits) {
		if (size < 1 || size > MAX_SIZE || bits.length != (size * size + 7) / 8) {
			throw new IllegalArgumentException("Bad letter size " + size + " for " + bits.length + " bytes");
		}
		this.size = size;
		this.bits = bits;
		this.hash = hashOf(size, bits);
		this.rectangles = merge();
	}

	/** {@code ink[y * size + x]} is true where the letter has ink. */
	public static Letter of(int size, boolean[] ink) {
		byte[] bits = new byte[(size * size + 7) / 8];
		for (int i = 0; i < ink.length; i++) {
			if (ink[i]) {
				bits[i >> 3] |= (byte) (0x80 >>> (i & 7));
			}
		}
		return new Letter(size, bits);
	}

	/** Reads the hex form of {@link #toHex()}. */
	public static Letter fromHex(int size, String hex) {
		return new Letter(size, HexFormat.of().parseHex(hex));
	}

	public String toHex() {
		return HexFormat.of().formatHex(bits);
	}

	/** Reads the form of {@link #toBytes()}; fails on a size the bytes don't fit. */
	public static Letter fromBytes(int size, byte[] bits) {
		return new Letter(size, bits.clone());
	}

	/** The pixels, row by row from the top, one bit per pixel, most significant bit first. */
	public byte[] toBytes() {
		return bits.clone();
	}

	public int size() {
		return size;
	}

	public boolean ink(int x, int y) {
		int i = y * size + x;
		return (bits[i >> 3] & (0x80 >>> (i & 7))) != 0;
	}

	/** 32 hex characters, the same for the same size and pixels, whoever made the letter. */
	public String hash() {
		return hash;
	}

	/** The ink as few rectangles as a simple greedy merge finds: rows of ink, grown downwards. */
	public List<Rect> rectangles() {
		return rectangles;
	}

	private List<Rect> merge() {
		boolean[] used = new boolean[size * size];
		List<Rect> result = new ArrayList<>();
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				if (!ink(x, y) || used[y * size + x]) {
					continue;
				}
				int x1 = x;
				while (x1 < size && ink(x1, y) && !used[y * size + x1]) {
					x1++;
				}
				int y1 = y + 1;
				while (y1 < size && rowFree(x, x1, y1, used)) {
					y1++;
				}
				for (int yy = y; yy < y1; yy++) {
					Arrays.fill(used, yy * size + x, yy * size + x1, true);
				}
				result.add(new Rect(x, y, x1, y1));
				x = x1 - 1;
			}
		}
		return List.copyOf(result);
	}

	private boolean rowFree(int x0, int x1, int y, boolean[] used) {
		for (int x = x0; x < x1; x++) {
			if (!ink(x, y) || used[y * size + x]) {
				return false;
			}
		}
		return true;
	}

	private static String hashOf(int size, byte[] bits) {
		try {
			MessageDigest sha = MessageDigest.getInstance("SHA-256");
			sha.update(ByteBuffer.allocate(4).putInt(size).array());
			byte[] digest = sha.digest(bits);
			return HexFormat.of().formatHex(digest, 0, 16);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("Java without SHA-256", e);
		}
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Letter other && size == other.size && Arrays.equals(bits, other.bits);
	}

	@Override
	public int hashCode() {
		return hash.hashCode();
	}
}
