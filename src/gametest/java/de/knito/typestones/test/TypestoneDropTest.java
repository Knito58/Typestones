package de.knito.typestones.test;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import de.knito.typestones.ModBlocks;
import de.knito.typestones.Typestones;
import de.knito.typestones.block.Dyes;
import de.knito.typestones.block.TypestoneBlockEntity;
import de.knito.typestones.block.TypestoneItem;
import de.knito.typestones.letter.Letter;
import de.knito.typestones.letter.Letters;

/** A broken Typestone must drop itself with its letter and colors. */
public class TypestoneDropTest {
	@GameTest
	public void dropKeepsLetterAndDyes(GameTestHelper helper) {
		String letter = Letters.classic('A');
		Dyes dyes = new Dyes(DyeColor.RED, Optional.of(DyeColor.BLUE));
		ItemStack drop = placeAndBreak(helper, letter, dyes);
		helper.assertTrue(letter.equals(TypestoneItem.letter(drop)),
				"The drop lost its letter: " + TypestoneItem.letter(drop) + ", patch " + drop.getComponentsPatch());
		helper.assertTrue(dyes.equals(TypestoneItem.dyes(drop)),
				"The drop lost its dyes: " + TypestoneItem.dyes(drop) + ", patch " + drop.getComponentsPatch());
		helper.succeed();
	}

	/** A plain stone drops without components, so it stacks with new plain stones. */
	@GameTest
	public void plainDropStacks(GameTestHelper helper) {
		ItemStack drop = placeAndBreak(helper, Letters.NONE, Dyes.DEFAULT);
		ItemStack fresh = ModBlocks.TYPESTONE_ITEM.stack(Letters.NONE, Dyes.DEFAULT, 1);
		helper.assertTrue(ItemStack.isSameItemSameComponents(drop, fresh),
				"A plain drop doesn't stack with a new plain stone: patch " + drop.getComponentsPatch());
		helper.succeed();
	}

	/** The built-in letters come from the mod jar on the server too. */
	@GameTest
	public void classicLettersLoad(GameTestHelper helper) {
		for (char c : "AZaz09".toCharArray()) {
			Letter letter = Letters.get(Letters.classic(c));
			helper.assertTrue(letter != null && !letter.rectangles().isEmpty(), "No classic letter for " + c);
			helper.assertTrue(String.valueOf(c).equals(Letters.text(letter.hash())), "Wrong text for " + c);
		}
		helper.assertTrue(Letters.classic(' ').equals(Letters.NONE), "The space must be the plain stone");
		helper.succeed();
	}

	/** The typewriter's recipe must load; the game skips a recipe file it can't read. */
	@GameTest
	public void typewriterRecipeLoads(GameTestHelper helper) {
		var key = ResourceKey.create(Registries.RECIPE, Typestones.id("typewriter"));
		helper.assertTrue(helper.getLevel().recipeAccess().byKey(key).isPresent(), "The Typewriter recipe did not load");
		helper.succeed();
	}

	private static ItemStack placeAndBreak(GameTestHelper helper, String letter, Dyes dyes) {
		BlockPos absolute = helper.absolutePos(new BlockPos(1, 2, 1));
		helper.getLevel().setBlockAndUpdate(absolute, ModBlocks.TYPESTONE.defaultBlockState());
		if (helper.getLevel().getBlockEntity(absolute) instanceof TypestoneBlockEntity entity) {
			entity.setLook(letter, dyes);
		} else {
			throw helper.assertionException(Component.literal("The Typestone has no block entity"));
		}
		helper.getLevel().destroyBlock(absolute, true);

		List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(2));
		helper.assertTrue(drops.size() == 1, "Expected 1 drop, got " + drops.size());
		ItemStack drop = drops.getFirst().getItem();
		helper.assertTrue(drop.getItem() instanceof TypestoneItem, "The drop is " + drop + ", not a Typestone");
		return drop;
	}
}
