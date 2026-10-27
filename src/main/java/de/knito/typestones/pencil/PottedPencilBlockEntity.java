package de.knito.typestones.pencil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import de.knito.typestones.ModComponents;
import de.knito.typestones.ModPencil;
import de.knito.typestones.block.Dyes;

/** Keeps the dyes of the potted pencil, so it comes out of the pot in its colors. */
public class PottedPencilBlockEntity extends BlockEntity {
	private Dyes dyes = Dyes.DEFAULT;

	public PottedPencilBlockEntity(BlockPos pos, BlockState state) {
		super(ModPencil.POTTED_ENTITY, pos, state);
	}

	public void setPencil(ItemStack pencil) {
		dyes = pencil.getOrDefault(ModComponents.DYES, Dyes.DEFAULT);
		setChanged();
	}

	/** The empty pencil in the pot. */
	public ItemStack pencil() {
		ItemStack pencil = new ItemStack(ModPencil.ITEM);
		if (!dyes.equals(Dyes.DEFAULT)) {
			pencil.set(ModComponents.DYES, dyes);
		}
		return pencil;
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		dyes = input.read("dyes", Dyes.CODEC).orElse(Dyes.DEFAULT);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store("dyes", Dyes.CODEC, dyes);
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter components) {
		super.applyImplicitComponents(components);
		dyes = components.getOrDefault(ModComponents.DYES, Dyes.DEFAULT);
	}

	/** The loot table copies these onto the dropped pencil. */
	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder components) {
		super.collectImplicitComponents(components);
		if (!dyes.equals(Dyes.DEFAULT)) {
			components.set(ModComponents.DYES, dyes);
		}
	}

	@Override
	public void removeComponentsFromTag(ValueOutput output) {
		output.discard("dyes");
	}
}
