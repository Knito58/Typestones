package de.knito.typestones.pencil;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import de.knito.typestones.typewriter.TypewriterBlockEntity;

/**
 * What a pencil remembers: its Typewriter, the line it writes (one letter hash per stone,
 * {@link de.knito.typestones.letter.Letters#NONE} for a plain one) and where it placed the stones so
 * far, for undo. The stones themselves stay in the Typewriter's output inventory until placed.
 */
public record PencilLine(GlobalPos typewriter, List<String> letters, List<BlockPos> placed) {
	private static final Codec<List<String>> LETTERS_CODEC = Codec.STRING.listOf(0, TypewriterBlockEntity.MAX_TEXT_LENGTH);
	private static final Codec<List<BlockPos>> PLACED_CODEC = BlockPos.CODEC.listOf(0, TypewriterBlockEntity.MAX_TEXT_LENGTH);

	public static final Codec<PencilLine> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			GlobalPos.CODEC.fieldOf("typewriter").forGetter(PencilLine::typewriter),
			LETTERS_CODEC.fieldOf("letters").forGetter(PencilLine::letters),
			PLACED_CODEC.optionalFieldOf("placed", List.of()).forGetter(PencilLine::placed)
	).apply(instance, PencilLine::new));

	public static final StreamCodec<ByteBuf, PencilLine> STREAM_CODEC = StreamCodec.composite(
			GlobalPos.STREAM_CODEC, PencilLine::typewriter,
			ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(TypewriterBlockEntity.MAX_TEXT_LENGTH)), PencilLine::letters,
			BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(TypewriterBlockEntity.MAX_TEXT_LENGTH)), PencilLine::placed,
			PencilLine::new
	);

	public PencilLine {
		letters = List.copyOf(letters);
		placed = List.copyOf(placed.subList(0, Math.min(placed.size(), letters.size())));
	}

	/** Index of the next stone to place. */
	public int next() {
		return placed.size();
	}

	/** True when every stone of the line is placed. */
	public boolean done() {
		return placed.size() >= letters.size();
	}

	public PencilLine withPlaced(BlockPos pos) {
		List<BlockPos> more = new ArrayList<>(placed);
		more.add(pos.immutable());
		return new PencilLine(typewriter, letters, more);
	}

	public PencilLine withoutLast() {
		return new PencilLine(typewriter, letters, placed.subList(0, Math.max(0, placed.size() - 1)));
	}
}
