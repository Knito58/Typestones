package de.knito.typestones.pencil;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.GlobalPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import de.knito.typestones.typewriter.TypewriterBlockEntity;

/**
 * What a pencil remembers: its Typewriter, the line it writes (one letter hash per stone,
 * {@link de.knito.typestones.letter.Letters#NONE} for a plain one) and which stone comes next.
 * The stones themselves stay in the Typewriter's output inventory.
 */
public record PencilLine(GlobalPos typewriter, List<String> letters, int next) {
	private static final Codec<List<String>> LETTERS_CODEC = Codec.STRING.listOf(0, TypewriterBlockEntity.MAX_TEXT_LENGTH);

	public static final Codec<PencilLine> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			GlobalPos.CODEC.fieldOf("typewriter").forGetter(PencilLine::typewriter),
			LETTERS_CODEC.fieldOf("letters").forGetter(PencilLine::letters),
			Codec.INT.optionalFieldOf("next", 0).forGetter(PencilLine::next)
	).apply(instance, PencilLine::new));

	public static final StreamCodec<ByteBuf, PencilLine> STREAM_CODEC = StreamCodec.composite(
			GlobalPos.STREAM_CODEC, PencilLine::typewriter,
			ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(TypewriterBlockEntity.MAX_TEXT_LENGTH)), PencilLine::letters,
			ByteBufCodecs.VAR_INT, PencilLine::next,
			PencilLine::new
	);

	public PencilLine {
		letters = List.copyOf(letters);
		next = Math.clamp(next, 0, letters.size());
	}

	/** True when every stone of the line is placed. */
	public boolean done() {
		return next >= letters.size();
	}
}
