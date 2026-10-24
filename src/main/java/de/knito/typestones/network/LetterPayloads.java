package de.knito.typestones.network;

import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import de.knito.typestones.Typestones;
import de.knito.typestones.letter.LibraryLetter;

/** The packets that move letters between the server's library and the players. */
public final class LetterPayloads {
	/** Hashes per request; a client sends more requests for more. */
	public static final int MAX_REQUEST = 256;
	/** Letters per answer: 64 letters of 64x64 are about 40 kB. */
	public static final int MAX_ANSWER = 64;
	/** Letters per upload; client packets may not exceed 32 kB. */
	public static final int MAX_UPLOAD = 16;

	private LetterPayloads() {
	}

	/** Client to server: "send me these letters". */
	public record Request(List<String> hashes) implements CustomPacketPayload {
		public static final Type<Request> TYPE = new Type<>(Typestones.id("letter_request"));
		public static final StreamCodec<ByteBuf, Request> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(MAX_REQUEST)), Request::hashes,
				Request::new
		);

		@Override
		public Type<Request> type() {
			return TYPE;
		}
	}

	/** Server to client: letters from the library. Unknown hashes are left out. */
	public record Answer(List<LibraryLetter> letters) implements CustomPacketPayload {
		public static final Type<Answer> TYPE = new Type<>(Typestones.id("letters"));
		public static final StreamCodec<ByteBuf, Answer> STREAM_CODEC = StreamCodec.composite(
				LibraryLetter.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ANSWER)), Answer::letters,
				Answer::new
		);

		@Override
		public Type<Answer> type() {
			return TYPE;
		}
	}

	/** Client to server: newly baked letters for the library. The server fills in baker and date. */
	public record Upload(List<LibraryLetter> letters) implements CustomPacketPayload {
		public static final Type<Upload> TYPE = new Type<>(Typestones.id("letter_upload"));
		public static final StreamCodec<ByteBuf, Upload> STREAM_CODEC = StreamCodec.composite(
				LibraryLetter.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_UPLOAD)), Upload::letters,
				Upload::new
		);

		@Override
		public Type<Upload> type() {
			return TYPE;
		}
	}
}
