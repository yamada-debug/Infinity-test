package dev.gojoinfinity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * All server-side Infinity logic.
 *
 * <h2>How the "approach is slowed" effect works</h2>
 * Every server tick, for each player with Infinity active, we look at the entities near them
 * (one AABB query, not a scan of every entity in the world). For each affected entity we measure how far
 * it moved <em>toward</em> the player during the tick (using the position we recorded last tick) and
 * give back part of that movement:
 * <pre>
 *   gap       = distance(entity edge, player centre) - coreRadius
 *   t         = clamp(gap / (radius - coreRadius), 0, 1)
 *   keep      = t * t            // 1 at the outer radius, 0 at the core
 *   allowed   = min(approach * keep, gap * 0.5)
 * </pre>
 * The inward part of the entity's movement is therefore reduced smoothly the closer it gets, and it can never
 * reach the core radius (each tick it may cover at most half of the remaining gap). Mobs visibly decelerate,
 * arrows creep forward and then hang in the air.
 * This is done in a post-movement correction so it works no matter how the entity moves (AI navigation,
 * physics, projectile motion).
 */
public final class InfinityManager {
	/** Extra distance beyond {@code radius} where fast projectiles are already speed-capped (anti-tunnelling). */
	private static final double LOOKAHEAD = 3.0;
	private static final double PLAYER_HALF_WIDTH = 0.3;

	private static final Set<UUID> ACTIVE = new HashSet<>();
	private static final Map<UUID, Long> LAST_TOGGLE = new HashMap<>();
	/** Positions of affected entities at the end of the previous tick. */
	private static Map<UUID, Vec3> lastPositions = new HashMap<>();
	/** Entities (arrows etc.) whose gravity we switched off while they hover; restored afterwards. */
	private static final Set<UUID> HELD = new HashSet<>();

	private InfinityManager() { }

	// ------------------------------------------------------------------ state

	public static boolean isActive(ServerPlayer player) {
		return ACTIVE.contains(player.getUUID());
	}

	public static boolean canUse(ServerPlayer player) {
		if (player.isSpectator()) {
			return false;
		}
		return player.isCreative() || Boolean.TRUE.equals(player.getAttached(InfinityData.UNLOCKED));
	}

	/** Called when the toggle key packet arrives. */
	public static void toggle(ServerPlayer player) {
		InfinityConfig config = InfinityConfig.get();
		long now = player.level().getGameTime();
		Long last = LAST_TOGGLE.get(player.getUUID());
		if (last != null && now - last < 4) {
			return; // key-mash / packet spam guard
		}
		LAST_TOGGLE.put(player.getUUID(), now);

		if (!config.enabled) {
			player.sendOverlayMessage(Component.translatable("message.gojo_infinity.disabled_by_server")
					.withStyle(ChatFormatting.RED));
			return;
		}

		if (isActive(player)) {
			ACTIVE.remove(player.getUUID());
			player.sendOverlayMessage(Component.translatable("message.gojo_infinity.off").withStyle(ChatFormatting.GRAY));
			if (config.toggleSounds) {
				player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6F, 1.8F);
			}
		} else if (canUse(player)) {
			ACTIVE.add(player.getUUID());
			player.sendOverlayMessage(Component.translatable("message.gojo_infinity.on").withStyle(ChatFormatting.AQUA));
			if (config.toggleSounds) {
				player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6F, 1.8F);
			}
		} else {
			player.sendOverlayMessage(Component.translatable("message.gojo_infinity.locked").withStyle(ChatFormatting.RED));
		}
	}

	/** Releases every entity we are holding (server shutdown / safety net). */
	public static void releaseAll(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			for (UUID id : HELD) {
				Entity entity = level.getEntity(id);
				if (entity != null) {
					entity.setNoGravity(false);
				}
			}
		}
		HELD.clear();
		ACTIVE.clear();
		lastPositions.clear();
		LAST_TOGGLE.clear();
	}

	// ------------------------------------------------------------------ damage

	/**
	 * Hooked into {@code ServerLivingEntityEvents.ALLOW_DAMAGE}. Returns false to cancel damage.
	 * This is the fallback for everything that cannot be physically intercepted (fall damage, fire, lava,
	 * explosions, melee from a mob that was already adjacent when Infinity turned on, ...).
	 */
	public static boolean allowDamage(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof ServerPlayer player) || !isActive(player)) {
			return true; // never protects anything but an Infinity-active player
		}
		InfinityConfig config = InfinityConfig.get();
		if (!config.enabled || !config.blockAllDamage) {
			return true;
		}
		// Void / /kill must still work, otherwise a player could become unkillable and stuck.
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		// Starvation is the "cost" of Infinity, never block it.
		if (source.is(DamageTypes.STARVE)) {
			return true;
		}
		if (player.level() instanceof ServerLevel level) {
			InfinityEffects.blockedPulse(level, player, source.getEntity());
		}
		return false;
	}

	// ------------------------------------------------------------------ tick

	public static void tick(MinecraftServer server) {
		if (ACTIVE.isEmpty() && HELD.isEmpty()) {
			if (!lastPositions.isEmpty()) {
				lastPositions = new HashMap<>(); // never keep stale positions across an inactive period
			}
			return;
		}
		InfinityConfig config = InfinityConfig.get();
		Map<UUID, Vec3> nextPositions = new HashMap<>();
		Set<UUID> seenHeld = new HashSet<>();

		Iterator<UUID> it = ACTIVE.iterator();
		while (it.hasNext()) {
			ServerPlayer player = server.getPlayerList().getPlayer(it.next());
			if (player == null || !player.isAlive() || player.isSpectator() || !config.enabled) {
				it.remove(); // disconnected, died, spectating, or disabled by config
				continue;
			}
			if (!(player.level() instanceof ServerLevel level)) {
				continue;
			}
			tickPlayer(level, player, config, nextPositions, seenHeld);
		}

		// Release anything that was held but is no longer inside an active Infinity.
		if (!HELD.isEmpty()) {
			Iterator<UUID> held = HELD.iterator();
			while (held.hasNext()) {
				UUID id = held.next();
				if (seenHeld.contains(id)) {
					continue;
				}
				for (ServerLevel level : server.getAllLevels()) {
					Entity entity = level.getEntity(id);
					if (entity != null) {
						entity.setNoGravity(false);
						break;
					}
				}
				held.remove();
			}
		}
		lastPositions = nextPositions;
	}

	private static void tickPlayer(ServerLevel level, ServerPlayer player, InfinityConfig config,
			Map<UUID, Vec3> nextPositions, Set<UUID> seenHeld) {
		// ---- self effects: fall, fire, air
		player.resetFallDistance();
		if (player.isOnFire()) {
			player.clearFire();
		}
		if (config.preventDrowning) {
			player.setAirSupply(player.getMaxAirSupply());
		}

		// ---- hunger (Survival/Adventure only; never in Creative)
		if (config.hungerDrainPerMinute > 0 && !player.isCreative()) {
			// 4.0 exhaustion == 1 hunger point. 1200 ticks per minute.
			player.causeFoodExhaustion((float) (config.hungerDrainPerMinute * 4.0 / 1200.0));
		}

		// ---- approaching entities
		Vec3 center = player.getBoundingBox().getCenter();
		double radius = config.radius;
		double core = config.coreRadius;
		double query = radius + LOOKAHEAD;

		AABB box = player.getBoundingBox().inflate(query);
		List<Entity> nearby = level.getEntities(player, box, e -> shouldAffect(e, player, config));

		InfinityEffects.Frame frame = InfinityEffects.begin(level, player, config);

		for (Entity entity : nearby) {
			Vec3 entityCenter = entity.getBoundingBox().getCenter();
			Vec3 toPlayer = center.subtract(entityCenter);
			double centerDist = toPlayer.length();
			if (centerDist < 1.0E-4 || centerDist > query) {
				continue;
			}
			Vec3 toward = toPlayer.scale(1.0 / centerDist); // unit vector: entity -> player

			// distance from the entity's edge to the player's centre
			double dist = centerDist - entity.getBbWidth() * 0.5;
			double gap = Math.max(0.0, dist - core);
			double t = Math.min(1.0, gap / (radius - core));
			double keep = t * t; // 1 at the outer radius -> 0 at the core

			UUID id = entity.getUUID();
			Vec3 pos = entity.position();
			Vec3 previous = lastPositions.get(id);
			boolean projectile = entity instanceof Projectile;

			// How far did it move toward the player this tick?
			Vec3 displacement;
			if (previous != null) {
				displacement = pos.subtract(previous);
			} else if (projectile) {
				displacement = entity.getDeltaMovement(); // first sighting of a fast projectile
			} else {
				displacement = Vec3.ZERO;
			}
			if (displacement.lengthSqr() > 100.0) {
				displacement = Vec3.ZERO; // teleported (ender pearl, portal, /tp): not a real approach
			}
			double approach = displacement.dot(toward);

			// -- 1) give back part of the inward movement of this tick
			if (approach > 1.0E-4) {
				double allowed = Math.min(approach * keep, gap * 0.5);
				double excess = approach - allowed;
				if (excess > 1.0E-4) {
					Vec3 correction = toward.scale(-excess);
					if (entity instanceof LivingEntity) {
						entity.move(MoverType.SELF, correction); // respects block collisions
					} else {
						entity.setPos(pos.add(correction));
					}
					pos = entity.position();
				}
			}

			// -- 2) bleed off inward velocity so momentum does not build up behind the barrier
			Vec3 velocity = entity.getDeltaMovement();
			double inwardSpeed = velocity.dot(toward);
			if (inwardSpeed > 0.0 && keep < 1.0) {
				double bled = keep < 0.02 ? inwardSpeed : inwardSpeed * (1.0 - keep);
				entity.setDeltaMovement(velocity.subtract(toward.scale(bled)));
			}

			// -- 3) projectiles hang motionless in the air when (almost) stopped
			if (projectile) {
				if (keep < 0.06) {
					if (!entity.isNoGravity() || HELD.contains(id)) {
						if (HELD.add(id)) {
							entity.setNoGravity(true);
						}
						seenHeld.add(id);
					}
					entity.setDeltaMovement(Vec3.ZERO);
				} else if (HELD.contains(id)) {
					seenHeld.add(id);
				}
			}

			// -- 4) hard push-out if something is already inside the core (spawned/teleported/Infinity just enabled,
			//       or the player walked into it)
			if (dist < core - 0.05 && !projectile) {
				double push = Math.min(0.5, (core - dist) * 0.35);
				Vec3 away = toward.scale(-push);
				if (entity instanceof LivingEntity) {
					entity.move(MoverType.SELF, away);
				} else {
					entity.setPos(entity.position().add(away));
				}
				pos = entity.position();
			}

			nextPositions.put(id, pos);

			// -- visuals: ripple where the entity is being stopped, stronger the more it is slowed
			if (keep < 0.85) {
				frame.ripple(center, toward, core, 1.0 - keep);
			}
		}

		frame.ambient();
	}

	private static boolean shouldAffect(Entity entity, ServerPlayer player, InfinityConfig config) {
		if (entity == player || entity.isRemoved() || entity.isSpectator()) {
			return false;
		}
		// Other players are client-authoritative for movement; they are covered by the damage fallback only.
		if (entity instanceof Player) {
			return false;
		}
		// Let the player still pick things up.
		if (entity instanceof ItemEntity || entity instanceof ExperienceOrb) {
			return false;
		}
		// Whatever the player is riding / carrying must not be pushed away.
		if (entity.getRootVehicle() == player.getRootVehicle()) {
			return false;
		}
		if (entity instanceof Projectile projectile) {
			if (projectile.getOwner() == player) {
				return false; // your own arrows leave normally
			}
			return config.affectProjectiles;
		}
		if (entity instanceof LivingEntity) {
			return entity.getType().getCategory() == MobCategory.MONSTER
					? config.affectHostileMobs
					: config.affectPassiveMobs;
		}
		return config.affectOtherEntities;
	}
}
