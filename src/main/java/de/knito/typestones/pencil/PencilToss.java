package de.knito.typestones.pencil;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import de.knito.typestones.ModComponents;
import de.knito.typestones.ModPencil;
import de.knito.typestones.block.Dyes;

/**
 * An empty pencil thrown away flies a moment, then bursts into a tiny firework in its two colors and
 * is gone. A pencil whose line isn't written drops like any item.
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
	}

	public static boolean isEmptyPencil(ItemStack stack) {
		return stack.is(ModPencil.ITEM) && !stack.has(ModComponents.PENCIL_LINE);
	}

	/** Called for every item a player throws (not on death). */
	public static void thrown(ItemEntity entity) {
		if (isEmptyPencil(entity.getItem()) && entity.level() instanceof ServerLevel level) {
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
