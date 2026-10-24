package de.knito.typestones.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.fabricmc.fabric.api.blockgetter.v2.RenderDataBlockEntity;

import de.knito.typestones.ModBlocks;
import de.knito.typestones.ModComponents;

/**
 * Holds the two dyes of a Typestone. They travel with the item as the typestones:dyes component, so a
 * broken block drops itself with its colors (see the block's loot table).
 */
public class TypestoneBlockEntity extends BlockEntity implements RenderDataBlockEntity {
	private Dyes dyes = Dyes.DEFAULT;

	public TypestoneBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.TYPESTONE_ENTITY, pos, state);
	}

	public Dyes dyes() {
		return dyes;
	}

	public void setDyes(Dyes dyes) {
		this.dyes = dyes;
		setChanged();
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		dyes = input.read("dyes", Dyes.CODEC).orElse(Dyes.DEFAULT);
		if (level != null && level.isClientSide()) {
			// New colors need a new chunk mesh.
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 0);
		}
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

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder components) {
		super.collectImplicitComponents(components);
		components.set(ModComponents.DYES, dyes);
	}

	@Override
	public void removeComponentsFromTag(ValueOutput output) {
		output.discard("dyes");
	}

	/** Read by the block tints on the chunk meshing threads. */
	@Override
	public Dyes getRenderData() {
		return dyes;
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveCustomOnly(registries);
	}
}
