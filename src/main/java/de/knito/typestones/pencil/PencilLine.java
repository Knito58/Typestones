package de.knito.typestones.pencil;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import de.knito.typestones.picture.Picture;
import de.knito.typestones.typewriter.TypewriterBlockEntity;
import de.knito.typestones.typewriter.TypewriterBlockEntity.PictureJob;

/**
 * What a pencil remembers: its Typewriter, what it writes and where it placed the stones so far, for
 * undo. It writes either a line (one letter hash per stone, {@link de.knito.typestones.letter.Letters#NONE}
 * for a plain one) or a picture, whose stones it lays row by row from the top left. The stones
 * themselves stay in the Typewriter's output inventory until placed.
 */
public record PencilLine(GlobalPos typewriter, List<String> letters, List<BlockPos> placed, Optional<PictureJob> picture) {
	private static final int MAX_STONES = Math.max(TypewriterBlockEntity.MAX_TEXT_LENGTH, Picture.MAX_STONES * Picture.MAX_STONES);
	private static final Codec<List<String>> LETTERS_CODEC = Codec.STRING.listOf(0, TypewriterBlockEntity.MAX_TEXT_LENGTH);
	private static final Codec<List<BlockPos>> PLACED_CODEC = BlockPos.CODEC.listOf(0, MAX_STONES);

	public static final Codec<PencilLine> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			GlobalPos.CODEC.fieldOf("typewriter").forGetter(PencilLine::typewriter),
			LETTERS_CODEC.optionalFieldOf("letters", List.of()).forGetter(PencilLine::letters),
			PLACED_CODEC.optionalFieldOf("placed", List.of()).forGetter(PencilLine::placed),
			PictureJob.CODEC.optionalFieldOf("picture").forGetter(PencilLine::picture)
	).apply(instance, PencilLine::new));

	public static final StreamCodec<ByteBuf, PencilLine> STREAM_CODEC = StreamCodec.composite(
			GlobalPos.STREAM_CODEC, PencilLine::typewriter,
			ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(TypewriterBlockEntity.MAX_TEXT_LENGTH)), PencilLine::letters,
			BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_STONES)), PencilLine::placed,
			ByteBufCodecs.optional(PictureJob.STREAM_CODEC), PencilLine::picture,
			PencilLine::new
	);

	public PencilLine(GlobalPos typewriter, List<String> letters, List<BlockPos> placed) {
		this(typewriter, letters, placed, Optional.empty());
	}

	public PencilLine {
		letters = picture.isPresent() ? List.of() : List.copyOf(letters);
		int size = picture.map(PictureJob::stones).orElse(letters.size());
		placed = List.copyOf(placed.subList(0, Math.min(placed.size(), size)));
	}

	public static PencilLine of(GlobalPos typewriter, PictureJob picture) {
		return new PencilLine(typewriter, List.of(), List.of(), Optional.of(picture));
	}

	/** Stones it writes in all. */
	public int size() {
		return picture.map(PictureJob::stones).orElse(letters.size());
	}

	/** Index of the next stone to place. */
	public int next() {
		return placed.size();
	}

	/** True when every stone of the line is placed. */
	public boolean done() {
		return placed.size() >= size();
	}

	public PencilLine withPlaced(BlockPos pos) {
		List<BlockPos> more = new ArrayList<>(placed);
		more.add(pos.immutable());
		return new PencilLine(typewriter, letters, more, picture);
	}

	public PencilLine withoutLast() {
		return new PencilLine(typewriter, letters, placed.subList(0, Math.max(0, placed.size() - 1)), picture);
	}
}
