package de.knito.typestones.test;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeColor;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import de.knito.typestones.ModTypewriter;
import de.knito.typestones.letter.Letter;
import de.knito.typestones.letter.LetterInfo;
import de.knito.typestones.letter.LetterLibrary;
import de.knito.typestones.letter.Letters;
import de.knito.typestones.letter.LibraryLetter;
import de.knito.typestones.typewriter.FontSettings;
import de.knito.typestones.typewriter.TypewriterActionPayload;
import de.knito.typestones.typewriter.TypewriterBlockEntity;
import de.knito.typestones.typewriter.TypewriterStatus;

/** The Typewriter with fonts: texts, settings and starting a job from letters in the library. */
public class TypewriterFontTest {
	private static final FontSettings TEST_FONT = new FontSettings("Test Font", 32, 100, 0);

	private static TypewriterBlockEntity typewriter() {
		TypewriterBlockEntity typewriter = new TypewriterBlockEntity(BlockPos.ZERO, ModTypewriter.BLOCK.defaultBlockState());
		typewriter.dyeSamples().setItem(TypewriterBlockEntity.SLOT_LETTER, new ItemStack(TypewriterBlockEntity.dyeItem(DyeColor.BLACK)));
		return typewriter;
	}

	/** A 32x32 letter with one dot at (n, n), so each n gives another hash. */
	private static LibraryLetter dot(int n, String text) {
		boolean[] ink = new boolean[32 * 32];
		ink[n * 32 + n] = true;
		return new LibraryLetter(Letter.of(32, ink), new LetterInfo(text, TEST_FONT.font(), "de_de", "Peter", "2026-10-24"));
	}

	@GameTest
	public void cleansTextPerFont(GameTestHelper helper) {
		helper.assertTrue(TypewriterBlockEntity.cleanText("A中\n", true).equals("A"), "Classic kept what it can't show");
		helper.assertTrue(TypewriterBlockEntity.cleanText("A中\n", false).equals("A中"), "A font lost a character or kept a control");
		String emoji = "😀";
		String tooLong = "x".repeat(TypewriterBlockEntity.MAX_TEXT_LENGTH - 1) + emoji;
		helper.assertTrue(TypewriterBlockEntity.cleanText(tooLong, false).length() == TypewriterBlockEntity.MAX_TEXT_LENGTH - 1,
				"A character pair was split at the end");
		helper.succeed();
	}

	@GameTest
	public void settingsStayInRange(GameTestHelper helper) {
		FontSettings wild = new FontSettings("", 50, 1000, -99);
		helper.assertTrue(wild.isClassic() && wild.size() == 64 && wild.scale() == FontSettings.MAX_SCALE
				&& wild.baseline() == -FontSettings.MAX_BASELINE, "Settings out of range: " + wild);

		TypewriterActionPayload sent = new TypewriterActionPayload(TypewriterActionPayload.Action.START, "中 中", 3, TEST_FONT,
				List.of("a", Letters.NONE, "a"));
		ByteBuf buf = Unpooled.buffer();
		TypewriterActionPayload.STREAM_CODEC.encode(buf, sent);
		helper.assertTrue(sent.equals(TypewriterActionPayload.STREAM_CODEC.decode(buf)), "The button packet changed on the way");
		helper.succeed();
	}

	@GameTest
	public void classicIgnoresSentLetters(GameTestHelper helper) {
		TypewriterBlockEntity typewriter = typewriter();
		typewriter.applySettings("A B", 1, FontSettings.DEFAULT);
		typewriter.start(List.of("nonsense"), new LetterLibrary());
		helper.assertTrue(typewriter.status() == TypewriterStatus.RUNNING, "Classic didn't start: " + typewriter.status());
		helper.assertTrue(typewriter.jobLetters().equals(List.of(Letters.classic('A'), Letters.NONE, Letters.classic('B'))),
				"Classic job letters: " + typewriter.jobLetters());
		helper.succeed();
	}

	@GameTest
	public void fontNeedsLettersInLibrary(GameTestHelper helper) {
		LetterLibrary library = new LetterLibrary();
		LibraryLetter zhong = dot(1, "中");
		LibraryLetter wen = dot(2, "文");
		library.add(zhong);
		TypewriterBlockEntity typewriter = typewriter();
		typewriter.applySettings("中 文", 1, TEST_FONT);

		typewriter.start(List.of(zhong.hash(), Letters.NONE, wen.hash()), library);
		helper.assertTrue(typewriter.status() == TypewriterStatus.MISSING_LETTERS, "Started with a letter not in the library");
		typewriter.start(List.of(zhong.hash(), Letters.NONE), library);
		helper.assertTrue(typewriter.status() == TypewriterStatus.MISSING_LETTERS, "Started with too few letters");

		library.add(wen);
		typewriter.start(List.of(zhong.hash(), Letters.NONE, wen.hash()), library);
		helper.assertTrue(typewriter.status() == TypewriterStatus.RUNNING, "Didn't start: " + typewriter.status());
		helper.assertTrue(typewriter.jobLetters().equals(List.of(zhong.hash(), Letters.NONE, wen.hash())),
				"Job letters: " + typewriter.jobLetters());
		helper.succeed();
	}
}
