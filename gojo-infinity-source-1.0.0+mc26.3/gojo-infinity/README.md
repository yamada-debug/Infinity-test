# Gojo's Infinity (Fabric, Minecraft 26.3)

Gojo Satoru's **Infinity** from Jujutsu Kaisen, and nothing else (no Blue/Red/Purple/Six Eyes/Domain).

Things approaching you are slowed progressively and stopped before they touch you.

## Versions (verified against FabricMC/fabric-example-mod `26.3` branch + Fabric API `26.3` branch)

| Thing | Version |
|---|---|
| Minecraft | **26.3** (unobfuscated, Mojang names) |
| Fabric Loader | 0.19.5 or newer |
| Fabric API | 0.161.0+26.3 |
| Fabric Loom | 1.18-SNAPSHOT (plugin id `net.fabricmc.fabric-loom`) |
| Gradle | 9.7.1 (wrapper included) |
| Java | 25 |

## Dependencies
Only **Fabric Loader** and **Fabric API**. No Kotlin, no rendering/animation/shader library.
Install on **both** the server and every client (see below).

## Build
```
./gradlew build
```
Jar: `build/libs/gojo-infinity-1.0.0+mc26.3.jar` (needs JDK 25 and internet access to Fabric's maven).
No local JDK 25? Push this folder to a GitHub repo: `.github/workflows/build.yml` builds the jar and attaches it to the run as an artifact.

## Use
* Default key: **G** (Options > Controls > Key Binds > "Gojo's Infinity" > "Toggle Infinity").
* Survival: craft an **Infinity Core** and right-click it once (consumed, permanent, kept after death).
  ```
  A E A      A = Amethyst Shard
  E N E      E = Eye of Ender
  A E A      N = Nether Star
  ```
* Creative: no unlock needed; the Infinity Core is in the Tools & Utilities tab, but the key works without it.
* Hunger: while active in Survival/Adventure, drains `hungerDrainPerMinute` (default 1 point = half a drumstick per minute) via vanilla exhaustion (saturation goes first). Never in Creative. Stops the instant you toggle off. Low hunger never disables Infinity; at 0 hunger vanilla starvation applies (starvation damage is deliberately not blocked).

## Config: `config/gojo_infinity.json` (server side)
`enabled`, `radius`, `coreRadius`, `hungerDrainPerMinute`, `affectHostileMobs`, `affectPassiveMobs`, `affectProjectiles`, `affectOtherEntities`, `blockAllDamage`, `preventDrowning`, `visualIntensity`, `particleDensity`, `maxParticlesPerTick`, `toggleSounds`.

## How it works
Server-authoritative. Each tick, for each active player, one AABB query finds nearby entities; each one's inward movement that tick is reduced by `keep = t^2` (t = distance beyond the core / (radius - core)) and capped to half the remaining gap, so nothing can reach the core. Projectiles come to a stop and hang in the air (gravity disabled while held, restored when released). Anything already adjacent is gently pushed out. Remaining damage (fall, fire, lava, explosions, melee that slipped through...) is cancelled in `ALLOW_DAMAGE`.

## Limitations
* Not blocked on purpose: void damage and `/kill` (bypass invulnerability), starvation.
* Other **players** are not slowed (their movement is client-authoritative); their damage is still cancelled.
* Creeper/TNT explosions are stopped at the boundary, and damage to you is cancelled, but they can still destroy blocks around you.
* Explosion knockback/status effects (e.g. poison, wither) from direct damage sources are cancelled with the damage; area effect clouds/potions that land nearby may still apply effects if the cloud reaches you.
* Visuals are server-sent vanilla particles; a client with Particles set to "Minimal" sees fewer. No custom shader/distortion rendering (see notes in the chat response).
* Items and XP orbs are intentionally not affected so you can still pick things up.
