package de.knito.typestones.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import de.knito.typestones.Typestones;

/** The packet that brings an item's icon, drawn by a player's game, into the world's picture library. */
public final class IconPayloads {
	/** Largest PNG accepted: one stone at 64 px is far below this. */
	public static final int MAX_PNG = 32 * 1024;

	private IconPayloads() {
	}

	/** Client to server: the icon of the item with this id (such as "minecraft:iron_ingot") as a one-stone picture PNG. */
	public record Upload(String itemId, byte[] png) implements CustomPacketPayload {
		public static final Type<Upload> TYPE = new Type<>(Typestones.id("icon_upload"));
		public static final StreamCodec<ByteBuf, Upload> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.stringUtf8(128), Upload::itemId,
				ByteBufCodecs.byteArray(MAX_PNG), Upload::png,
				Upload::new
		);

		@Override
		public Type<Upload> type() {
			return TYPE;
		}
	}
}
