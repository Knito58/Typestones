package de.knito.typestones.picture;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** What the library knows about a picture besides its pixels: its size, name, who made it and when. */
public record PictureInfo(String hash, int cols, int rows, String name, String maker, String made) {
	public static final int MAX_NAME = 64;

	public static final Codec<PictureInfo> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("hash").forGetter(PictureInfo::hash),
			Codec.intRange(1, Picture.MAX_STONES).fieldOf("cols").forGetter(PictureInfo::cols),
			Codec.intRange(1, Picture.MAX_STONES).fieldOf("rows").forGetter(PictureInfo::rows),
			Codec.STRING.optionalFieldOf("name", "").forGetter(PictureInfo::name),
			Codec.STRING.optionalFieldOf("maker", "").forGetter(PictureInfo::maker),
			Codec.STRING.optionalFieldOf("made", "").forGetter(PictureInfo::made)
	).apply(instance, PictureInfo::new));

	public static PictureInfo of(Picture picture, String name, String maker, String made) {
		String shortName = name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name;
		return new PictureInfo(picture.hash(), picture.cols(), picture.rows(), shortName, maker, made);
	}
}
