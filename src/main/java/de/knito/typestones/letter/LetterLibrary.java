package de.knito.typestones.letter;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import de.knito.typestones.Typestones;

/**
 * All letters baked in this world, by hash, saved as data/typestones/letters.dat. The built-in classic
 * letters are not stored here; {@link #find} knows them anyway.
 */
public class LetterLibrary extends SavedData {
	/** At 32x32 about 4 MB in memory; enough for several complete CJK fonts. */
	public static final int MAX_LETTERS = 32768;

	private static final Codec<LetterLibrary> CODEC = LibraryLetter.CODEC.listOf().fieldOf("letters").codec()
			.xmap(LetterLibrary::new, library -> List.copyOf(library.letters.values()));

	// The data fixer type is required; command storage's fixes leave our fields alone.
	public static final SavedDataType<LetterLibrary> TYPE = new SavedDataType<>(Typestones.id("letters"),
			LetterLibrary::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	public enum Added {
		NEW, KNOWN, FULL
	}

	private final Map<String, LibraryLetter> letters = new LinkedHashMap<>();

	public LetterLibrary() {
	}

	private LetterLibrary(List<LibraryLetter> saved) {
		for (LibraryLetter letter : saved) {
			letters.put(letter.hash(), letter);
		}
	}

	public static LetterLibrary get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** A letter of this world or a built-in one, or null. */
	public @Nullable LibraryLetter find(String hash) {
		LibraryLetter builtIn = Letters.builtIn(hash);
		return builtIn != null ? builtIn : letters.get(hash);
	}

	public Added add(LibraryLetter letter) {
		if (find(letter.hash()) != null) {
			return Added.KNOWN;
		}
		if (letters.size() >= MAX_LETTERS) {
			return Added.FULL;
		}
		letters.put(letter.hash(), letter);
		setDirty();
		return Added.NEW;
	}

	/** Stones with a removed letter show as plain. Built-in letters can't be removed. */
	public boolean remove(String hash) {
		if (letters.remove(hash) == null) {
			return false;
		}
		setDirty();
		return true;
	}

	public int size() {
		return letters.size();
	}

	public Collection<LibraryLetter> all() {
		return Collections.unmodifiableCollection(letters.values());
	}
}
