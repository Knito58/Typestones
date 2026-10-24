package de.knito.typestones.block;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import de.knito.typestones.glyph.GlyphSet;

/**
 * A smooth stone block with a glyph on all six sides. The glyph and the facing live in the block
 * state, so the model is chosen without code; the two dye colors live in the block entity and are
 * applied as tints. FACING is the direction the placing player looked, so on floors and ceilings the
 * top of the letter points away from them and the text reads in the direction they walk.
 */
public class TypestoneBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	/** Index into {@link GlyphSet}; 0 is the space, a plain block. */
	public static final IntegerProperty GLYPH = IntegerProperty.create("glyph", 0, GlyphSet.size() - 1);
	/** Whether there is a background dye; picks the white, tintable background texture. */
	public static final BooleanProperty DYED = BooleanProperty.create("dyed");

	public final FontFace face;

	public TypestoneBlock(FontFace face, Properties properties) {
		super(properties);
		this.face = face;
		registerDefaultState(stateDefinition.any()
				.setValue(FACING, Direction.NORTH)
				.setValue(GLYPH, 0)
				.setValue(DYED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, GLYPH, DYED);
	}

	/** Glyph and dyed come from the item's block_state component; only the facing is decided here. */
	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TypestoneBlockEntity(pos, state);
	}
}
