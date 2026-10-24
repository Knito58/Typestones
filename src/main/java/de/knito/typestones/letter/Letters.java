package de.knito.typestones.letter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

/**
 * The letters this side knows, by hash. A Typestone only stores the hash of its letter.
 *
 * <p>For now these are the built-in classic letters, read from the mod jar on the server and the
 * client alike (written by GlyphGen from fonts/classic.txt). Letters baked from other fonts come later.
 */
public final class Letters {
	/** The "letter" of a plain block: no ink. */
	public static final String NONE = "";

	private static final String CLASSIC = "/typestones/letters/classic.txt";

	private static final Map<String, Letter> BY_HASH = new ConcurrentHashMap<>();
	private static final Map<String, String> TEXT = new ConcurrentHashMap<>();
	private static final Map<Character, String> CLASSIC_BY_CHAR = new HashMap<>();

	static {
		loadClassic();
	}

	private Letters() {
	}

	public static @Nullable Letter get(String hash) {
		return BY_HASH.get(hash);
	}

	/** The character(s) the letter stands for, or "" when unknown or plain. */
	public static String text(String hash) {
		return TEXT.getOrDefault(hash, "");
	}

	/** Hash of the classic letter for c; {@link #NONE} for the space and characters it lacks. */
	public static String classic(char c) {
		return CLASSIC_BY_CHAR.getOrDefault(c, NONE);
	}

	public static boolean hasClassic(char c) {
		return c == ' ' || CLASSIC_BY_CHAR.containsKey(c);
	}

	/** Lines "U+0041 16 <hex pixels>", see {@link Letter#fromHex}. */
	private static void loadClassic() {
		try (InputStream in = Letters.class.getResourceAsStream(CLASSIC)) {
			if (in == null) {
				throw new IllegalStateException(CLASSIC + " is missing from the mod jar");
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			for (String line = reader.readLine(); line != null; line = reader.readLine()) {
				String[] parts = line.trim().split(" ");
				if (parts.length != 3 || !parts[0].startsWith("U+")) {
					continue;
				}
				char c = (char) Integer.parseInt(parts[0].substring(2), 16);
				Letter letter = Letter.fromHex(Integer.parseInt(parts[1]), parts[2]);
				BY_HASH.put(letter.hash(), letter);
				TEXT.put(letter.hash(), String.valueOf(c));
				CLASSIC_BY_CHAR.put(c, letter.hash());
			}
		} catch (IOException e) {
			throw new IllegalStateException("Can't read " + CLASSIC, e);
		}
	}
}
