package de.knito.typestones.picture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import net.minecraft.world.level.storage.LevelResource;

import de.knito.typestones.Typestones;

/**
 * All pictures of this world. The list is saved as data/typestones/pictures.dat; the pixels of each
 * picture are a PNG file of its own in data/typestones/pictures/, named after its hash, so the list
 * stays small and a picture is only read when a player needs it.
 */
public class PictureLibrary extends SavedData {
	/** At most 16 x 16 stones each; a full library of big photos is a few hundred MB of PNG files. */
	public static final int MAX_PICTURES = 1024;

	private static final Codec<PictureLibrary> CODEC = PictureInfo.CODEC.listOf().fieldOf("pictures").codec()
			.xmap(PictureLibrary::new, library -> List.copyOf(library.pictures.values()));

	// The data fixer type is required; command storage's fixes leave our fields alone.
	public static final SavedDataType<PictureLibrary> TYPE = new SavedDataType<>(Typestones.id("pictures"),
			PictureLibrary::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	public enum Added {
		NEW, KNOWN, FULL
	}

	private final Map<String, PictureInfo> pictures = new LinkedHashMap<>();

	public PictureLibrary() {
	}

	private PictureLibrary(List<PictureInfo> saved) {
		for (PictureInfo info : saved) {
			pictures.put(info.hash(), info);
		}
	}

	public static PictureLibrary get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** The folder with the PNG files of the pictures. */
	public static Path folder(MinecraftServer server) {
		return server.getWorldPath(LevelResource.DATA).resolve(Typestones.MOD_ID).resolve("pictures");
	}

	/** Where operators put PNG files for /typestones picture load. */
	public static Path importFolder(MinecraftServer server) {
		return server.getWorldPath(LevelResource.DATA).resolve(Typestones.MOD_ID).resolve("import");
	}

	public @Nullable PictureInfo find(String hash) {
		return pictures.get(hash);
	}

	/** Adds the picture to the list and writes its PNG file into folder. */
	public Added add(Picture picture, PictureInfo info, Path folder) throws IOException {
		if (pictures.containsKey(picture.hash())) {
			return Added.KNOWN;
		}
		if (pictures.size() >= MAX_PICTURES) {
			return Added.FULL;
		}
		Files.createDirectories(folder);
		Files.write(file(folder, picture.hash()), picture.toPng());
		pictures.put(picture.hash(), info);
		setDirty();
		return Added.NEW;
	}

	/** The PNG of a picture in the list, or null when it isn't in the list or its file is gone. */
	public byte @Nullable [] png(String hash, Path folder) {
		if (!pictures.containsKey(hash)) {
			return null;
		}
		try {
			return Files.readAllBytes(file(folder, hash));
		} catch (IOException e) {
			Typestones.LOGGER.warn("Can't read picture {}: {}", hash, e.toString());
			return null;
		}
	}

	/** Stones with a removed picture show as plain stone. */
	public boolean remove(String hash, Path folder) {
		if (pictures.remove(hash) == null) {
			return false;
		}
		try {
			Files.deleteIfExists(file(folder, hash));
		} catch (IOException e) {
			Typestones.LOGGER.warn("Can't delete picture {}: {}", hash, e.toString());
		}
		setDirty();
		return true;
	}

	public int size() {
		return pictures.size();
	}

	public Collection<PictureInfo> all() {
		return Collections.unmodifiableCollection(pictures.values());
	}

	private static Path file(Path folder, String hash) {
		// Hashes come from players' packets too: only hex digits go into a file name.
		if (!hash.matches("[0-9a-f]{1,64}")) {
			throw new IllegalArgumentException("Not a picture hash: " + hash);
		}
		return folder.resolve(hash + ".png");
	}
}
