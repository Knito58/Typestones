package de.knito.typestones.typewriter;

import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import de.knito.typestones.Typestones;

/**
 * A button press in the Typewriter screen. Always carries the current settings. On Start with a font
 * other than classic it also carries the hash of each character's letter, which the player's game
 * baked and uploaded just before; {@link de.knito.typestones.letter.Letters#NONE} stands for a blank.
 * The Pencil button names the picture chosen for the pencil, or nothing for the last job.
 */
public record TypewriterActionPayload(Action action, String text, int repeat, FontSettings font, List<String> letters,
		String pencilPicture) implements CustomPacketPayload {
	public enum Action {
		SAVE, START, STOP, GET_PENCIL
	}

	public static final Type<TypewriterActionPayload> TYPE = new Type<>(Typestones.id("typewriter_action"));

	public static final StreamCodec<ByteBuf, TypewriterActionPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.idMapper(i -> Action.values()[Math.clamp(i, 0, Action.values().length - 1)], Action::ordinal), TypewriterActionPayload::action,
			ByteBufCodecs.stringUtf8(TypewriterBlockEntity.MAX_TEXT_LENGTH * 4), TypewriterActionPayload::text,
			ByteBufCodecs.VAR_INT, TypewriterActionPayload::repeat,
			FontSettings.STREAM_CODEC, TypewriterActionPayload::font,
			ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(TypewriterBlockEntity.MAX_TEXT_LENGTH)), TypewriterActionPayload::letters,
			ByteBufCodecs.stringUtf8(64), TypewriterActionPayload::pencilPicture,
			TypewriterActionPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	/** Server to client: the pictures with stones in the output, by hash, for the pencil chooser. */
	public record PencilPictures(List<String> hashes) implements CustomPacketPayload {
		public static final Type<PencilPictures> TYPE = new Type<>(Typestones.id("typewriter_pencil_pictures"));
		public static final StreamCodec<ByteBuf, PencilPictures> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(TypewriterBlockEntity.MAX_PENCIL_PICTURES)), PencilPictures::hashes,
				PencilPictures::new
		);

		@Override
		public Type<PencilPictures> type() {
			return TYPE;
		}
	}
}
