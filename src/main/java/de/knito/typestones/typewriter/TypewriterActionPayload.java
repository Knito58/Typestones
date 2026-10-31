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

	/**
	 * What the Pencil button can make a pencil for: a line (empty hash, its text, stones as cols, rows 1)
	 * or a picture (its hash and size in stones).
	 */
	public record PencilChoice(String hash, String text, int cols, int rows) {
		public static final StreamCodec<ByteBuf, PencilChoice> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.stringUtf8(64), PencilChoice::hash,
				ByteBufCodecs.stringUtf8(TypewriterBlockEntity.MAX_TEXT_LENGTH * 4), PencilChoice::text,
				ByteBufCodecs.VAR_INT, PencilChoice::cols,
				ByteBufCodecs.VAR_INT, PencilChoice::rows,
				PencilChoice::new
		);

		public boolean isLine() {
			return hash.isEmpty();
		}
	}

	/** Server to client: the pencil choices, the last job first, then the other pictures in the output. */
	public record PencilChoices(List<PencilChoice> choices) implements CustomPacketPayload {
		public static final Type<PencilChoices> TYPE = new Type<>(Typestones.id("typewriter_pencil_choices"));
		public static final StreamCodec<ByteBuf, PencilChoices> STREAM_CODEC = StreamCodec.composite(
				PencilChoice.STREAM_CODEC.apply(ByteBufCodecs.list(TypewriterBlockEntity.MAX_PENCIL_PICTURES + 1)), PencilChoices::choices,
				PencilChoices::new
		);

		@Override
		public Type<PencilChoices> type() {
			return TYPE;
		}
	}
}
