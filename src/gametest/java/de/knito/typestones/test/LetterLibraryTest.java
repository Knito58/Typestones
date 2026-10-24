package de.knito.typestones.test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import de.knito.typestones.ModLetters;
import de.knito.typestones.letter.Letter;
import de.knito.typestones.letter.LetterInfo;
import de.knito.typestones.letter.LetterLibrary;
import de.knito.typestones.letter.Letters;
import de.knito.typestones.letter.LibraryLetter;

/** The world's letter library: adding, finding, saving, sending and checking uploads. */
public class LetterLibraryTest {
	/** A 32x32 letter that is not built in: a frame with a dot. */
	private static LibraryLetter sample() {
		boolean[] ink = new boolean[32 * 32];
		for (int i = 0; i < 32; i++) {
			ink[i] = ink[31 * 32 + i] = ink[i * 32] = ink[i * 32 + 31] = true;
		}
		ink[16 * 32 + 16] = true;
		return new LibraryLetter(Letter.of(32, ink), new LetterInfo("中", "Test Font", "zh_tw", "Peter", "2026-10-24"));
	}

	@GameTest
	public void addFindRemove(GameTestHelper helper) {
		LetterLibrary library = new LetterLibrary();
		LibraryLetter letter = sample();
		helper.assertTrue(library.find(letter.hash()) == null, "A new library already knows the letter");
		helper.assertTrue(library.add(letter) == LetterLibrary.Added.NEW, "First add is not NEW");
		helper.assertTrue(library.add(letter) == LetterLibrary.Added.KNOWN, "Second add is not KNOWN");
		helper.assertTrue(letter.equals(library.find(letter.hash())), "Found a different letter");

		LibraryLetter classicA = Letters.builtIn(Letters.classic('A'));
		helper.assertTrue(classicA != null && library.find(classicA.hash()) != null, "The library doesn't find built-in letters");
		helper.assertTrue(library.add(classicA) == LetterLibrary.Added.KNOWN, "A built-in letter was added again");
		helper.assertTrue(!library.remove(classicA.hash()), "A built-in letter was removed");

		helper.assertTrue(library.remove(letter.hash()) && library.find(letter.hash()) == null, "Remove didn't remove");
		helper.succeed();
	}

	@GameTest
	public void savesAndLoads(GameTestHelper helper) {
		LetterLibrary library = new LetterLibrary();
		LibraryLetter letter = sample();
		library.add(letter);
		Tag saved = LetterLibrary.TYPE.codec().encodeStart(NbtOps.INSTANCE, library).getOrThrow();
		LetterLibrary loaded = LetterLibrary.TYPE.codec().parse(NbtOps.INSTANCE, saved).getOrThrow();
		helper.assertTrue(loaded.size() == 1 && letter.equals(loaded.find(letter.hash())),
				"The letter didn't survive saving: " + saved);
		helper.succeed();
	}

	@GameTest
	public void travelsOverNetwork(GameTestHelper helper) {
		LibraryLetter letter = sample();
		ByteBuf buf = Unpooled.buffer();
		LibraryLetter.STREAM_CODEC.encode(buf, letter);
		LibraryLetter received = LibraryLetter.STREAM_CODEC.decode(buf);
		helper.assertTrue(letter.equals(received) && letter.hash().equals(received.hash()), "The letter changed on the way");
		helper.succeed();
	}

	@GameTest
	public void uploadsAreChecked(GameTestHelper helper) {
		LibraryLetter good = sample();
		LibraryLetter checked = ModLetters.checked(good, "Alex", "2026-10-25");
		helper.assertTrue(checked != null && checked.info().baker().equals("Alex") && checked.info().baked().equals("2026-10-25"),
				"A good upload was refused or kept the sender's baker: " + checked);

		LibraryLetter tooSmall = new LibraryLetter(Letter.of(8, new boolean[64]), good.info());
		helper.assertTrue(ModLetters.checked(tooSmall, "Alex", "") == null, "An 8x8 letter was accepted");
		LibraryLetter noText = new LibraryLetter(good.letter(), new LetterInfo("", "F", "en_us", "", ""));
		helper.assertTrue(ModLetters.checked(noText, "Alex", "") == null, "A letter without text was accepted");
		LibraryLetter badLocale = new LibraryLetter(good.letter(), new LetterInfo("A", "F", "../../x", "", ""));
		helper.assertTrue(ModLetters.checked(badLocale, "Alex", "") == null, "A bad language code was accepted");
		helper.succeed();
	}

	@GameTest
	public void infoDerivesNameAndScript(GameTestHelper helper) {
		LetterInfo info = sample().info();
		helper.assertTrue(info.unicodeName().equals("CJK UNIFIED IDEOGRAPH-4E2D"), "Unicode name: " + info.unicodeName());
		helper.assertTrue(info.script().equals("HAN"), "Script: " + info.script());
		helper.succeed();
	}
}
