package de.knito.typestones.picture;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Which part of a picture a Typestone carries: the picture's hash and size in stones, and the column
 * and row of this stone, counted from the top left as the picture is seen from the front.
 */
public record PictureTile(String hash, int cols, int rows, int col, int row) {
	private static final Codec<Integer> STONES = Codec.intRange(1, Picture.MAX_STONES);
	private static final Codec<Integer> INDEX = Codec.intRange(0, Picture.MAX_STONES - 1);

	public static final Codec<PictureTile> CODEC = RecordCodecBuilder.<PictureTile>create(instance -> instance.group(
			Codec.string(1, 64).fieldOf("hash").forGetter(PictureTile::hash),
			STONES.fieldOf("cols").forGetter(PictureTile::cols),
			STONES.fieldOf("rows").forGetter(PictureTile::rows),
			INDEX.fieldOf("col").forGetter(PictureTile::col),
			INDEX.fieldOf("row").forGetter(PictureTile::row)
	).apply(instance, PictureTile::new)).validate(tile -> tile.col < tile.cols && tile.row < tile.rows
			? DataResult.success(tile) : DataResult.error(() -> "Tile outside its picture: " + tile));

	public static final StreamCodec<ByteBuf, PictureTile> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(64), PictureTile::hash,
			ByteBufCodecs.VAR_INT, PictureTile::cols,
			ByteBufCodecs.VAR_INT, PictureTile::rows,
			ByteBufCodecs.VAR_INT, PictureTile::col,
			ByteBufCodecs.VAR_INT, PictureTile::row,
			PictureTile::new
	);

	public static PictureTile of(Picture picture, int col, int row) {
		return new PictureTile(picture.hash(), picture.cols(), picture.rows(), col, row);
	}

	/**
	 * The column whose tile this stone shows on the back of a wall picture. Seen from behind, the
	 * columns run the other way; showing the opposite stone's tile makes the picture read right from
	 * there too, instead of in mixed-up squares.
	 */
	public int backCol() {
		return cols - 1 - col;
	}

	/** The row whose tile this stone shows underneath a floor picture, so it reads right from below. */
	public int bottomRow() {
		return rows - 1 - row;
	}
}
