package de.knito.typestones.block;

import de.knito.typestones.letter.Letters;

/**
 * What a Typestone shows: the hash of its letter ({@link Letters#NONE} for a plain block) and its dyes.
 * The block entity hands it to the chunk mesher as render data.
 */
public record Look(String letter, Dyes dyes) {
	public static final Look PLAIN = new Look(Letters.NONE, Dyes.DEFAULT);
}
