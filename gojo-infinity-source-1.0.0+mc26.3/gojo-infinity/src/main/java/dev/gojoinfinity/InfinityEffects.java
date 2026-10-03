package dev.gojoinfinity;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Visuals for Infinity. Everything is sent by the server as ordinary vanilla particles, which means:
 * <ul>
 *   <li>other players see the effect without extra client code,</li>
 *   <li>nothing here touches client-only classes, so it is safe on a dedicated server,</li>
 *   <li>the number of particles is hard-capped per player per tick.</li>
 * </ul>
 * Two layers: a slowly rotating shimmer on the invisible boundary (so everyone can see where space is bent), and
 * <em>ripples</em> - rings of light drawn on the boundary exactly where an entity is being stopped. The closer the
 * entity, the bigger and denser the ripple.
 */
public final class InfinityEffects {
	private static final ParticleOptions SHIMMER = ParticleTypes.END_ROD;
	private static final ParticleOptions SPARK = ParticleTypes.ELECTRIC_SPARK;

	private InfinityEffects() { }

	/** Per-player, per-tick particle budget + helpers. */
	public static Frame begin(ServerLevel level, ServerPlayer player, InfinityConfig config) {
		return new Frame(level, player, config);
	}

	/** Small flash used when a hit was nullified. */
	public static void blockedPulse(ServerLevel level, ServerPlayer player, Entity attacker) {
		InfinityConfig config = InfinityConfig.get();
		if (config.visualIntensity <= 0.0) {
			return;
		}
		Vec3 center = player.getBoundingBox().getCenter();
		Vec3 dir = attacker != null
				? attacker.getBoundingBox().getCenter().subtract(center)
				: new Vec3(0.0, 1.0, 0.0);
		if (dir.lengthSqr() < 1.0E-6) {
			dir = new Vec3(0.0, 1.0, 0.0);
		}
		Vec3 at = center.add(dir.normalize().scale(config.coreRadius));
		int sparks = Math.max(1, (int) Math.round(4 * config.visualIntensity));
		level.sendParticles(SPARK, at.x, at.y, at.z, sparks, 0.15, 0.15, 0.15, 0.02);
	}

	public static final class Frame {
		private final ServerLevel level;
		private final ServerPlayer player;
		private final InfinityConfig config;
		private int budget;

		private Frame(ServerLevel level, ServerPlayer player, InfinityConfig config) {
			this.level = level;
			this.player = player;
			this.config = config;
			this.budget = config.visualIntensity <= 0.0 ? 0 : config.maxParticlesPerTick;
		}

		/** Ring of light on the boundary, facing the entity that is being stopped. */
		public void ripple(Vec3 center, Vec3 toward, double core, double strength) {
			if (budget <= 0) {
				return;
			}
			// Spread ripples out in time: every 2nd tick is plenty and halves the packet count.
			if ((level.getGameTime() & 1L) != 0L) {
				return;
			}
			Vec3 normal = toward.scale(-1.0); // player -> entity
			Vec3 contact = center.add(normal.scale(core));

			Vec3 helper = Math.abs(normal.y) > 0.9 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
			Vec3 a = normal.cross(helper).normalize();
			Vec3 b = normal.cross(a).normalize();

			double ringRadius = 0.3 + 0.55 * strength;
			int points = 3 + (int) Math.round(6.0 * strength * config.visualIntensity);
			points = Math.min(points, budget);
			double spin = level.getGameTime() * 0.35;

			for (int i = 0; i < points; i++) {
				double angle = spin + (Math.PI * 2.0 * i) / points;
				Vec3 p = contact
						.add(a.scale(Math.cos(angle) * ringRadius))
						.add(b.scale(Math.sin(angle) * ringRadius));
				level.sendParticles(SHIMMER, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
			budget -= points;

			// Fully stopped -> a bright spark in the middle of the ripple.
			if (strength > 0.85 && budget > 0) {
				level.sendParticles(SPARK, contact.x, contact.y, contact.z, 1, 0.05, 0.05, 0.05, 0.0);
				budget--;
			}
		}

		/** Slow rotating shimmer on the boundary sphere so everyone can see that Infinity is active. */
		public void ambient() {
			if (budget <= 0) {
				return;
			}
			long time = level.getGameTime();
			if (time % 3L != 0L) {
				return;
			}
			int count = (int) Math.round(3.0 * config.particleDensity * config.visualIntensity);
			count = Math.min(count, budget);
			if (count <= 0) {
				return;
			}
			Vec3 center = player.getBoundingBox().getCenter();
			double r = config.coreRadius + 0.1;
			for (int i = 0; i < count; i++) {
				double y = Math.sin(time * 0.045 + i * 2.1) * 0.95; // -0.95..0.95 of the radius
				double ringR = Math.sqrt(1.0 - y * y);
				double angle = time * 0.09 + i * (Math.PI * 2.0 / count);
				double x = Math.cos(angle) * ringR * r;
				double z = Math.sin(angle) * ringR * r;
				level.sendParticles(SHIMMER, center.x + x, center.y + y * r, center.z + z, 1, 0.0, 0.0, 0.0, 0.0);
			}
			budget -= count;
		}
	}
}
