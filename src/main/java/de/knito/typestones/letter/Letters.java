package de.knito.typestones.letter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

/**
 * The letters this game knows, by hash. A Typestone only stores the hash of its letter.
 *
 * <p>The built-in classic letters are read from the mod jar (written by GlyphGen from fonts/classic.txt).
 * All other letters live in the world's {@link LetterLibrary} on the server; a client keeps the ones
 * the server sent it here until it disconnects, and asks for unknown ones through {@link #onMissing}.
 */
public final class Letters {
	/** The "letter" of a plain block: no ink. */
	public static final String NONE = "";

	private static final String CLASSIC = "/typestones/letters/classic.txt";

	private static final Map<String, LibraryLetter> BUILT_IN = new HashMap<>();
	private static final Map<Character, String> CLASSIC_BY_CHAR = new HashMap<>();
	/** Letters the server sent; read on the chunk meshing threads. */
	private static final Map<String, LibraryLetter> RECEIVED = new ConcurrentHashMap<>();

	/** Called with the hash of a letter that isn't known (yet). The client sets it to ask the server. */
	private static volatile Consumer<String> onMissing = hash -> {
	};

	static {
		loadClassic();
	}

	private Letters() {
	}

	public static @Nullable Letter get(String hash) {
		LibraryLetter letter = find(hash);
		return letter == null ? null : letter.letter();
	}

	/** The character(s) the letter stands for, or "" when unknown or plain. */
	public static String text(String hash) {
		LibraryLetter letter = find(hash);
		return letter == null ? "" : letter.info().text();
	}

	public static @Nullable LibraryLetter find(String hash) {
		if (hash.equals(NONE)) {
			return null;
		}
		LibraryLetter letter = builtIn(hash);
		if (letter == null) {
			letter = RECEIVED.get(hash);
		}
		if (letter == null) {
			onMissing.accept(hash);
		}
		return letter;
	}

	/** True when this game has the letter, without asking the server for it. */
	public static boolean isKnown(String hash) {
		return BUILT_IN.containsKey(hash) || RECEIVED.containsKey(hash);
	}

	/** One of the letters in the mod jar, or null. */
	public static @Nullable LibraryLetter builtIn(String hash) {
		return BUILT_IN.get(hash);
	}

	/** Hash of the classic letter for c; {@link #NONE} for the space and characters it lacks. */
	public static String classic(char c) {
		return CLASSIC_BY_CHAR.getOrDefault(c, NONE);
	}

	public static boolean hasClassic(char c) {
		return c == ' ' || CLASSIC_BY_CHAR.containsKey(c);
	}

	/** Client: letters that arrived from the server. */
	public static void receive(Collection<LibraryLetter> letters) {
		for (LibraryLetter letter : letters) {
			RECEIVED.put(letter.hash(), letter);
		}
	}

	/** Client: forget the server's letters, e.g. when leaving it. */
	public static void forgetReceived() {
		RECEIVED.clear();
	}

	public static void setOnMissing(Consumer<String> handler) {
		onMissing = handler;
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
				BUILT_IN.put(letter.hash(), new LibraryLetter(letter, LetterInfo.classic(String.valueOf(c))));
				CLASSIC_BY_CHAR.put(c, letter.hash());
			}
		} catch (IOException e) {
			throw new IllegalStateException("Can't read " + CLASSIC, e);
		}
	}
}
