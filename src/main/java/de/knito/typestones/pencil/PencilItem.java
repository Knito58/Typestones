package de.knito.typestones.pencil;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.component.TooltipDisplay;

import de.knito.typestones.ModComponents;
import de.knito.typestones.letter.Letters;

/**
 * The pencil from a Typewriter: it writes that Typewriter's line, stone by stone (see {@link PencilWriter}).
 * Without a {@link ModComponents#PENCIL_LINE} it is empty: its line is written.
 */
public class PencilItem extends Item {
	public PencilItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		ItemStack pencil = context.getItemInHand();
		PencilLine line = pencil.get(ModComponents.PENCIL_LINE);
		Player player = context.getPlayer();
		if (line == null || player == null) {
			return InteractionResult.PASS;
		}
		if (!(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		return player.isSecondaryUseActive()
				? PencilWriter.undo(level, player, pencil, line)
				: PencilWriter.write(level, player, context, pencil, line);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		PencilLine line = stack.get(ModComponents.PENCIL_LINE);
		if (line == null) {
			tooltip.accept(Component.translatable("item.typestones.pencil.empty").withStyle(ChatFormatting.GRAY));
			return;
		}
		StringBuilder text = new StringBuilder();
		for (String hash : line.letters()) {
			String shown = Letters.isKnown(hash) || hash.equals(Letters.NONE) ? Letters.text(hash) : "?";
			text.append(shown.isEmpty() ? " " : shown);
		}
		tooltip.accept(Component.literal(text.toString()).withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("item.typestones.pencil.next", line.next() + 1, line.letters().size())
				.withStyle(ChatFormatting.DARK_GRAY));
	}
}
