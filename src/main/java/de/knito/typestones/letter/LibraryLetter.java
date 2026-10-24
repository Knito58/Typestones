package de.knito.typestones.letter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** A letter with its library data, as saved with the world and as sent to players. */
public record LibraryLetter(Letter letter, LetterInfo info) {
	/** Saved as {"size": 16, "pixels": "<hex>"}. */
	private static final MapCodec<Letter> LETTER_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.intRange(1, Letter.MAX_SIZE).fieldOf("size").forGetter(Letter::size),
			Codec.STRING.fieldOf("pixels").forGetter(Letter::toHex)
	).apply(instance, Letter::fromHex));

	private static final StreamCodec<ByteBuf, Letter> LETTER_STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Letter::size,
			ByteBufCodecs.byteArray(Letter.MAX_SIZE * Letter.MAX_SIZE / 8), Letter::toBytes,
			Letter::fromBytes
	);

	public static final Codec<LibraryLetter> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			LETTER_CODEC.forGetter(LibraryLetter::letter),
			LetterInfo.MAP_CODEC.forGetter(LibraryLetter::info)
	).apply(instance, LibraryLetter::new));

	public static final StreamCodec<ByteBuf, LibraryLetter> STREAM_CODEC = StreamCodec.composite(
			LETTER_STREAM_CODEC, LibraryLetter::letter,
			LetterInfo.STREAM_CODEC, LibraryLetter::info,
			LibraryLetter::new
	);

	public String hash() {
		return letter.hash();
	}
}
