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

import de.knito.typestones.ModComponents;
import de.knito.typestones.picture.PictureTile;

/**
 * A smooth stone block with a letter on all six sides. Only the facing is in the block state; the letter
 * (as a hash, see Letters) and the two dyes live in the block entity, and the client draws the letter
 * as rectangles. FACING is the direction the placing player looked, so on floors and ceilings the letter
 * reads from where they stood.
 *
 * <p>A picture stone shows its tile on two opposite sides only: front and back on a wall, top and bottom
 * when {@link #FLAT} (floor or ceiling). The other four sides are plain smooth stone.
 */
public class TypestoneBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	/** For picture stones: the picture lies flat (floor or ceiling) instead of standing like a wall. */
	public static final BooleanProperty FLAT = BooleanProperty.create("flat");

	public TypestoneBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FLAT, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, FLAT);
	}

	/**
	 * Letter, dyes and picture come from the item's components through the block entity. A picture
	 * stone placed against a stone of the same picture lies the same way as that one, so a picture
	 * can't get mixed up; otherwise clicking a floor or ceiling makes it flat, clicking a wall makes it
	 * stand.
	 */
	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection());
		PictureTile tile = context.getItemInHand().get(ModComponents.PICTURE);
		if (tile == null) {
			return state;
		}
		BlockPos against = context.getClickedPos().relative(context.getClickedFace().getOpposite());
		BlockState clicked = context.getLevel().getBlockState(against);
		if (clicked.getBlock() instanceof TypestoneBlock
				&& context.getLevel().getBlockEntity(against) instanceof TypestoneBlockEntity stone
				&& stone.picture() != null && stone.picture().hash().equals(tile.hash())) {
			return state.setValue(FACING, clicked.getValue(FACING)).setValue(FLAT, clicked.getValue(FLAT));
		}
		return state.setValue(FLAT, context.getClickedFace().getAxis().isVertical());
	}

	/** The side on which a picture stone shows its tile as seen from the front of the picture. */
	public static Direction pictureFront(BlockState state) {
		return state.getValue(FLAT) ? Direction.UP : state.getValue(FACING).getOpposite();
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
