package de.knito.typestones.client.picture;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import de.knito.typestones.Typestones;
import de.knito.typestones.network.PicturePayloads;
import de.knito.typestones.picture.Picture;

/**
 * Fetches the pictures of the stones in view from the server, once per hash and connection, and keeps
 * each as a texture of its own. Main thread only: the renderer asks while it gathers what to draw, and
 * the parts arrive on the main thread too. Stones show plain until their picture is there.
 */
public final class PictureSync {
	/** Hashes asked for on this connection; never asked twice, also when the server doesn't know one. */
	private static final Set<String> REQUESTED = new HashSet<>();
	private static final List<String> TO_REQUEST = new ArrayList<>();
	private static final Map<String, Identifier> TEXTURES = new HashMap<>();
	/** Parts of pictures still on their way. */
	private static final Map<String, byte[][]> PARTS = new HashMap<>();

	private PictureSync() {
	}

	public static void initialize() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> sendRequests());
		ClientPlayNetworking.registerGlobalReceiver(PicturePayloads.Part.TYPE, (payload, context) -> received(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(PictureSync::forget));
	}

	/** The texture of the picture, or null while it is not here yet; then it is asked for. */
	public static @Nullable Identifier texture(String hash) {
		Identifier texture = TEXTURES.get(hash);
		// Only hashes as the library makes them; anything else can't be a texture name.
		if (texture == null && hash.matches("[0-9a-f]{1,64}") && REQUESTED.add(hash)) {
			TO_REQUEST.add(hash);
		}
		return texture;
	}

	private static void sendRequests() {
		if (TO_REQUEST.isEmpty() || !ClientPlayNetworking.canSend(PicturePayloads.Request.TYPE)) {
			return;
		}
		Typestones.LOGGER.info("Asking the server for {} pictures", TO_REQUEST.size());
		for (int i = 0; i < TO_REQUEST.size(); i += PicturePayloads.MAX_REQUEST) {
			List<String> batch = TO_REQUEST.subList(i, Math.min(TO_REQUEST.size(), i + PicturePayloads.MAX_REQUEST));
			ClientPlayNetworking.send(new PicturePayloads.Request(List.copyOf(batch)));
		}
		TO_REQUEST.clear();
	}

	private static void received(PicturePayloads.Part part) {
		String hash = part.hash();
		if (!REQUESTED.contains(hash) || TEXTURES.containsKey(hash) || part.count() < 1 || part.count() > 64
				|| part.index() < 0 || part.index() >= part.count()) {
			return;
		}
		byte[][] parts = PARTS.computeIfAbsent(hash, h -> new byte[part.count()][]);
		if (parts.length != part.count()) {
			PARTS.remove(hash);
			return;
		}
		parts[part.index()] = part.png();
		ByteArrayOutputStream png = new ByteArrayOutputStream();
		for (byte[] bytes : parts) {
			if (bytes == null) {
				return;
			}
			png.writeBytes(bytes);
		}
		PARTS.remove(hash);
		load(hash, png.toByteArray());
	}

	private static void load(String hash, byte[] png) {
		NativeImage image;
		try {
			image = NativeImage.read(png);
		} catch (IOException e) {
			Typestones.LOGGER.warn("The server sent a broken picture {}: {}", hash, e.toString());
			return;
		}
		if (image.getWidth() % Picture.PIXELS != 0 || image.getHeight() % Picture.PIXELS != 0
				|| image.getWidth() > Picture.MAX_STONES * Picture.PIXELS || image.getHeight() > Picture.MAX_STONES * Picture.PIXELS) {
			Typestones.LOGGER.warn("The server sent picture {} in a wrong size", hash);
			image.close();
			return;
		}
		Identifier id = Typestones.id("picture/" + hash);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "Typestones picture " + hash, image));
		TEXTURES.put(hash, id);
		Typestones.LOGGER.info("Received picture {}", hash);
	}

	private static void forget() {
		for (Identifier id : TEXTURES.values()) {
			Minecraft.getInstance().getTextureManager().release(id);
		}
		TEXTURES.clear();
		REQUESTED.clear();
		TO_REQUEST.clear();
		PARTS.clear();
	}
}
