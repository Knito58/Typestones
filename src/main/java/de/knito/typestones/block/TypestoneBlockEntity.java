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
import de.knito.typestones.letter.Letters;

/**
 * Holds the letter (its hash, see {@link Letters}) and the two dyes of a Typestone. They travel with
 * the item as the typestones:letter and typestones:dyes components, so a broken block drops itself
 * with its letter and colors (see the block's loot table).
 */
public class TypestoneBlockEntity extends BlockEntity implements RenderDataBlockEntity {
	private String letter = Letters.NONE;
	private Dyes dyes = Dyes.DEFAULT;

	public TypestoneBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.TYPESTONE_ENTITY, pos, state);
	}

	public String letter() {
		return letter;
	}

	public Dyes dyes() {
		return dyes;
	}

	public void setLook(String letter, Dyes dyes) {
		this.letter = letter;
		this.dyes = dyes;
		setChanged();
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		letter = input.getStringOr("letter", Letters.NONE);
		dyes = input.read("dyes", Dyes.CODEC).orElse(Dyes.DEFAULT);
		if (level != null && level.isClientSide()) {
			// A new letter or new colors need a new chunk mesh.
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 0);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (!letter.equals(Letters.NONE)) {
			output.putString("letter", letter);
		}
		output.store("dyes", Dyes.CODEC, dyes);
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter components) {
		super.applyImplicitComponents(components);
		letter = components.getOrDefault(ModComponents.LETTER, Letters.NONE);
		dyes = components.getOrDefault(ModComponents.DYES, Dyes.DEFAULT);
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder components) {
		super.collectImplicitComponents(components);
		// Only what differs from a plain stone, so that all plain stones stack.
		if (!letter.equals(Letters.NONE)) {
			components.set(ModComponents.LETTER, letter);
		}
		if (!dyes.equals(Dyes.DEFAULT)) {
			components.set(ModComponents.DYES, dyes);
		}
	}

	@Override
	public void removeComponentsFromTag(ValueOutput output) {
		output.discard("letter");
		output.discard("dyes");
	}

	/** Read by the block model on the chunk meshing threads. */
	@Override
	public Look getRenderData() {
		return new Look(letter, dyes);
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
