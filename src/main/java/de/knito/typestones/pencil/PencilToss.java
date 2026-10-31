package de.knito.typestones.pencil;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import de.knito.typestones.ModComponents;
import de.knito.typestones.ModPencil;
import de.knito.typestones.block.Dyes;

/**
 * An empty pencil thrown away (Q, dragged out of the inventory, or right-clicked like an egg) flies a
 * moment, then bursts into a tiny firework in its two colors and is gone. A pencil whose line isn't
 * written drops like any item, and so does everything a dying player drops.
 */
public final class PencilToss {
	/** Ticks of flight before the burst. */
	private static final int FLIGHT = 12;
	private static final int NO_BACKGROUND = 0x969696;

	private static final List<Flight> FLYING = new ArrayList<>();

	private record Flight(ItemEntity entity, long burstAt) {
	}

	private PencilToss() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(PencilToss::tick);
		// Every way a player drops an item ends in a new item entity with that player as its owner.
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof ItemEntity item && item.getAge() < 2
					&& item.getOwner() instanceof ServerPlayer player && !player.isDeadOrDying()) {
				thrown(item);
			}
		});
	}

	/** Throws one empty pencil from the player's hand like an egg. */
	public static void throwFromHand(ServerLevel level, Player player, ItemStack pencil) {
		ItemEntity entity = new ItemEntity(level, player.getX(), player.getEyeY() - 0.3, player.getZ(), pencil.split(1));
		Vec3 look = player.getLookAngle();
		entity.setDeltaMovement(look.x * 0.6, look.y * 0.6 + 0.1, look.z * 0.6);
		level.addFreshEntity(entity);
		thrown(entity);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EGG_THROW, SoundSource.PLAYERS, 0.5f, 0.6f);
	}

	public static boolean isEmptyPencil(ItemStack stack) {
		return stack.is(ModPencil.ITEM) && !stack.has(ModComponents.PENCIL_LINE);
	}

	/** Called for every item a player throws (not on death). */
	public static void thrown(ItemEntity entity) {
		if (isEmptyPencil(entity.getItem()) && entity.level() instanceof ServerLevel level
				&& FLYING.stream().noneMatch(flight -> flight.entity() == entity)) {
			entity.setNeverPickUp();
			FLYING.add(new Flight(entity, level.getGameTime() + FLIGHT));
		}
	}

	private static void tick(MinecraftServer server) {
		FLYING.removeIf(flight -> {
			ItemEntity entity = flight.entity();
			if (entity.isRemoved() || !(entity.level() instanceof ServerLevel level)) {
				return true;
			}
			if (level.getGameTime() < flight.burstAt()) {
				return false;
			}
			burst(level, entity);
			entity.discard();
			return true;
		});
	}

	private static void burst(ServerLevel level, ItemEntity entity) {
		Dyes dyes = entity.getItem().getOrDefault(ModComponents.DYES, Dyes.DEFAULT);
		int letter = dyes.letter().getTextureDiffuseColor();
		int background = dyes.background().map(d -> d.getTextureDiffuseColor()).orElse(NO_BACKGROUND);
		double x = entity.getX();
		double y = entity.getY() + 0.25;
		double z = entity.getZ();
		level.sendParticles(new DustParticleOptions(letter, 1.0f), x, y, z, 14, 0.3, 0.3, 0.3, 0);
		level.sendParticles(new DustParticleOptions(background, 1.0f), x, y, z, 14, 0.3, 0.3, 0.3, 0);
		level.sendParticles(ParticleTypes.FIREWORK, x, y, z, 8, 0.05, 0.05, 0.05, 0.08);
		level.playSound(null, x, y, z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.4f, 1.8f);
	}
}
