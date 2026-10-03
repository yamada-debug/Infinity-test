package dev.gojoinfinity;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Server-side configuration, stored at {@code config/gojo_infinity.json}.
 * Gameplay + particle settings are all read by the (authoritative) server, so only the
 * server's copy of this file matters in multiplayer.
 */
public final class InfinityConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static InfinityConfig instance = new InfinityConfig();

	// ---- General ----
	/** Master switch for the whole ability. */
	public boolean enabled = true;

	// ---- Geometry (blocks, measured from the centre of the player's hitbox) ----
	/** Distance at which things start to slow down. */
	public double radius = 6.0;
	/** Distance at which things are completely stopped (the invisible "wall"). */
	public double coreRadius = 1.6;

	// ---- Survival cost ----
	/** Hunger points (half drumsticks) drained per minute while active in Survival. 0 = free. */
	public double hungerDrainPerMinute = 1.0;

	// ---- What is affected ----
	public boolean affectHostileMobs = true;
	public boolean affectPassiveMobs = true;
	public boolean affectProjectiles = true;
	/** Primed TNT, falling blocks, minecarts, boats, etc. */
	public boolean affectOtherEntities = true;

	// ---- Damage fallback ----
	/** Cancel every damage source that could not be physically intercepted (fall, fire, lava, explosions...). */
	public boolean blockAllDamage = true;
	/** Keep the player's air bar full while active (so drowning cannot start). */
	public boolean preventDrowning = true;

	// ---- Visuals (all particles are sent by the server) ----
	/** 0 disables every particle, 1 = default, 2 = extra flashy. */
	public double visualIntensity = 1.0;
	/** Multiplier for the number of ambient shell particles. */
	public double particleDensity = 1.0;
	/** Hard cap on particles sent per active player per tick. */
	public int maxParticlesPerTick = 40;
	/** Play the toggle sounds. */
	public boolean toggleSounds = true;

	public static InfinityConfig get() {
		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("gojo_infinity.json");
	}

	public static void load() {
		Path path = path();
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				InfinityConfig loaded = GSON.fromJson(reader, InfinityConfig.class);
				if (loaded != null) {
					instance = loaded;
				}
			} catch (Exception e) {
				GojoInfinityMod.LOGGER.error("Could not read {}, using defaults", path, e);
			}
		}
		instance.sanitize();
		save(); // writes defaults / fills in newly added options
	}

	public static void save() {
		try (Writer writer = Files.newBufferedWriter(path())) {
			GSON.toJson(instance, writer);
		} catch (IOException e) {
			GojoInfinityMod.LOGGER.error("Could not write config", e);
		}
	}

	private void sanitize() {
		coreRadius = clamp(coreRadius, 0.8, 4.0);
		radius = clamp(radius, coreRadius + 1.0, 16.0);
		hungerDrainPerMinute = clamp(hungerDrainPerMinute, 0.0, 40.0);
		visualIntensity = clamp(visualIntensity, 0.0, 2.0);
		particleDensity = clamp(particleDensity, 0.0, 3.0);
		maxParticlesPerTick = (int) clamp(maxParticlesPerTick, 0, 400);
	}

	private static double clamp(double v, double min, double max) {
		return Math.max(min, Math.min(max, v));
	}
}
