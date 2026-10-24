package de.knito.typestones.test;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import de.knito.typestones.ModBlocks;
import de.knito.typestones.ModComponents;
import de.knito.typestones.block.Dyes;
import de.knito.typestones.block.FontFace;
import de.knito.typestones.block.TypestoneBlock;
import de.knito.typestones.block.TypestoneBlockEntity;
import de.knito.typestones.block.TypestoneItem;
import de.knito.typestones.glyph.GlyphSet;

/** A broken Typestone must drop itself with its letter and colors. */
public class TypestoneDropTest {
	@GameTest
	public void dropKeepsLetterAndDyes(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 2, 1);
		int glyph = GlyphSet.indexOf('A');
		Dyes dyes = new Dyes(DyeColor.RED, Optional.of(DyeColor.BLUE));
		BlockState state = ModBlocks.TYPESTONES.get(FontFace.CLASSIC).defaultBlockState()
				.setValue(TypestoneBlock.GLYPH, glyph)
				.setValue(TypestoneBlock.DYED, true);
		helper.setBlock(pos, state);
		BlockPos absolute = helper.absolutePos(pos);
		if (helper.getLevel().getBlockEntity(absolute) instanceof TypestoneBlockEntity entity) {
			entity.setDyes(dyes);
		} else {
			helper.assertTrue(false, "The Typestone has no block entity");
		}

		// Diagnostics: which loot table the block uses and what the server made of it
		var key = state.getBlock().getLootTable().orElseThrow();
		var table = helper.getLevel().getServer().reloadableRegistries().getLootTable(key);
		String lootInfo = " | loot table " + key + (table == net.minecraft.world.level.storage.loot.LootTable.EMPTY ? " is EMPTY" : " loaded: "
				+ net.minecraft.world.level.storage.loot.LootTable.DIRECT_CODEC.encodeStart(
						helper.getLevel().registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE), table))
				+ " | direct drops " + net.minecraft.world.level.block.Block.getDrops(state, helper.getLevel(), absolute, helper.getLevel().getBlockEntity(absolute))
				.stream().map(s -> s + " " + s.getComponentsPatch()).toList();

		helper.getLevel().destroyBlock(absolute, true);

		List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(2));
		helper.assertTrue(drops.size() == 1, "Expected 1 drop, got " + drops.size());
		ItemStack drop = drops.getFirst().getItem();
		helper.assertTrue(drop.getItem() instanceof TypestoneItem, "The drop is " + drop + ", not a Typestone");
		helper.assertTrue(TypestoneItem.glyph(drop) == glyph,
				"The drop lost its letter: glyph " + TypestoneItem.glyph(drop) + ", patch " + drop.getComponentsPatch() + lootInfo);
		helper.assertTrue(dyes.equals(drop.get(ModComponents.DYES)),
				"The drop lost its dyes: " + drop.get(ModComponents.DYES) + ", patch " + drop.getComponentsPatch() + lootInfo);
		helper.succeed();
	}

	/** The station's recipe must load; the game skips a recipe file it can't read. */
	@GameTest
	public void stationRecipeLoads(GameTestHelper helper) {
		var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE,
				de.knito.typestones.Typestones.id("alpha_station"));
		helper.assertTrue(helper.getLevel().recipeAccess().byKey(key).isPresent(), "The Alpha Station recipe did not load");
		helper.succeed();
	}
}
