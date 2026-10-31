package de.knito.typestones.network;

import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import de.knito.typestones.Typestones;
import de.knito.typestones.picture.PictureInfo;

/** The packets that bring pictures from the server's library to the players. */
public final class PicturePayloads {
	/** Hashes per request; a client sends more requests for more. */
	public static final int MAX_REQUEST = 64;
	/** PNG bytes per part; a big photo takes a few parts. */
	public static final int PART_SIZE = 256 * 1024;

	private PicturePayloads() {
	}

	/** Client to server: "send me these pictures". */
	public record Request(List<String> hashes) implements CustomPacketPayload {
		public static final Type<Request> TYPE = new Type<>(Typestones.id("picture_request"));
		public static final StreamCodec<ByteBuf, Request> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(MAX_REQUEST)), Request::hashes,
				Request::new
		);

		@Override
		public Type<Request> type() {
			return TYPE;
		}
	}

	/**
	 * Server to client: part index of count of a picture's PNG, with the name it was made from (such as
	 * "fruits.jpg", empty when unknown) for tooltips. Unknown pictures get no answer.
	 */
	public record Part(String hash, String name, int index, int count, byte[] png) implements CustomPacketPayload {
		public static final Type<Part> TYPE = new Type<>(Typestones.id("picture_part"));
		public static final StreamCodec<ByteBuf, Part> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.stringUtf8(64), Part::hash,
				ByteBufCodecs.stringUtf8(PictureInfo.MAX_NAME), Part::name,
				ByteBufCodecs.VAR_INT, Part::index,
				ByteBufCodecs.VAR_INT, Part::count,
				ByteBufCodecs.byteArray(PART_SIZE), Part::png,
				Part::new
		);

		@Override
		public Type<Part> type() {
			return TYPE;
		}
	}
}
