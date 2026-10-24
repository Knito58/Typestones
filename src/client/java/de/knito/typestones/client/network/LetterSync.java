package de.knito.typestones.client.network;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import de.knito.typestones.Typestones;
import de.knito.typestones.letter.Letters;
import de.knito.typestones.letter.LibraryLetter;
import de.knito.typestones.network.LetterPayloads;

/**
 * Fetches letters this client doesn't know from the server, once per hash and connection, and redraws
 * the stones that waited for them. Unknown letters show as plain stones until they arrive.
 */
public final class LetterSync {
	/** Hashes asked for on this connection; never asked twice, also when the server doesn't know one. */
	private static final Set<String> REQUESTED = ConcurrentHashMap.newKeySet();
	private static final Queue<String> TO_REQUEST = new ConcurrentLinkedQueue<>();
	/** Stones drawn without their letter, to redraw when it arrives. Filled on the meshing threads. */
	private static final Map<String, Set<BlockPos>> WAITING = new ConcurrentHashMap<>();

	private LetterSync() {
	}

	public static void initialize() {
		Letters.setOnMissing(hash -> {
			if (REQUESTED.add(hash)) {
				TO_REQUEST.add(hash);
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> sendRequests());
		ClientPlayNetworking.registerGlobalReceiver(LetterPayloads.Answer.TYPE, (payload, context) -> received(payload.letters()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			Letters.forgetReceived();
			REQUESTED.clear();
			TO_REQUEST.clear();
			WAITING.clear();
		});
	}

	/** Called by the block model when it drew the stone at pos without its letter. */
	public static void waitFor(String hash, BlockPos pos) {
		WAITING.computeIfAbsent(hash, h -> ConcurrentHashMap.newKeySet()).add(pos.immutable());
		// The letter may have arrived between the model's look-up and this call; then nobody else redraws.
		if (Letters.isKnown(hash)) {
			Minecraft.getInstance().execute(() -> redraw(List.of(hash)));
		}
	}

	/**
	 * Sends letters baked here to the server's library, and keeps them, so stones with them show
	 * without asking the server. Packets go out in order, so they arrive before a following Start.
	 */
	public static void upload(Collection<LibraryLetter> letters) {
		Letters.receive(letters);
		List<LibraryLetter> batch = new ArrayList<>();
		for (LibraryLetter letter : letters) {
			batch.add(letter);
			if (batch.size() == LetterPayloads.MAX_UPLOAD) {
				ClientPlayNetworking.send(new LetterPayloads.Upload(List.copyOf(batch)));
				batch.clear();
			}
		}
		if (!batch.isEmpty()) {
			ClientPlayNetworking.send(new LetterPayloads.Upload(batch));
		}
	}

	private static void sendRequests() {
		if (TO_REQUEST.isEmpty() || !ClientPlayNetworking.canSend(LetterPayloads.Request.TYPE)) {
			return;
		}
		List<String> hashes = new ArrayList<>();
		Typestones.LOGGER.info("Asking the server for {} letters", TO_REQUEST.size());
		for (String hash = TO_REQUEST.poll(); hash != null; hash = TO_REQUEST.poll()) {
			hashes.add(hash);
			if (hashes.size() == LetterPayloads.MAX_REQUEST) {
				ClientPlayNetworking.send(new LetterPayloads.Request(List.copyOf(hashes)));
				hashes.clear();
			}
		}
		if (!hashes.isEmpty()) {
			ClientPlayNetworking.send(new LetterPayloads.Request(hashes));
		}
	}

	private static void received(List<LibraryLetter> letters) {
		Typestones.LOGGER.info("Received {} letters from the server", letters.size());
		Letters.receive(letters);
		redraw(letters.stream().map(LibraryLetter::hash).toList());
	}

	/** Redraws the stones that waited for these letters. Main thread only. */
	private static void redraw(List<String> hashes) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		Set<Long> sections = new HashSet<>();
		for (String hash : hashes) {
			Set<BlockPos> positions = WAITING.remove(hash);
			if (positions != null) {
				for (BlockPos pos : positions) {
					sections.add(SectionPos.asLong(pos));
				}
			}
		}
		Typestones.LOGGER.info("Redrawing {} sections for {} letters", sections.size(), hashes.size());
		for (long section : sections) {
			level.setSectionDirtyWithNeighbors(SectionPos.x(section), SectionPos.y(section), SectionPos.z(section));
		}
	}
}
