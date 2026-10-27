package de.knito.typestones.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import de.knito.typestones.pencil.PencilToss;

/** Fabric API has no event for a thrown item; this tells {@link PencilToss} about each one. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
	@Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZLnet/minecraft/util/Prediction;)Lnet/minecraft/world/entity/item/ItemEntity;",
			at = @At("RETURN"))
	private void typestones$thrown(ItemStack stack, boolean throwRandomly, Prediction prediction, CallbackInfoReturnable<ItemEntity> result) {
		if (!throwRandomly && result.getReturnValue() != null) {
			PencilToss.thrown(result.getReturnValue());
		}
	}
}
