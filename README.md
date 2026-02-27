# Nightfall

A Paper 1.21.11 plugin that makes Minecraft night genuinely dangerous.

Nightfall adds adaptive mob AI, heat-based targeting, and progressive block-breaking siege mechanics. Mobs learn where you are, hunt light sources, and will breach your base walls to get to you.

## Features

- **Siege mode** -- night activates increased mob damage multipliers and aggressive behavior.
- **Heat system** -- player actions (breaking blocks, sprinting, shooting, taking damage) generate heat nodes that mobs pathfind toward.
- **Light attraction** -- mobs without a target drift toward torches and light sources at night.
- **Torch hate** -- mobs prioritize breaking light sources that block their line of sight.
- **Breach mechanics** -- mobs stuck outside your base will progressively break through doors, glass, planks, and more. Block-breaking is telegraphed with crack animations and sound.
- **Obsidian protection** -- obsidian and crying obsidian cannot be broken by mobs.
- **Strict performance caps** -- per-mob, per-chunk, and global rate limits. No large scans every tick.

## Requirements

- Paper 1.21.11
- Java 21

## Installation

Drop `Nightfall-x.x.jar` into your server's `plugins/` folder and restart.

## Configuration

All behavior is configurable in `plugins/Nightfall/config.yml`. Key options:

| Key | Default | Description |
|---|---|---|
| `night.start-tick` | `13000` | When night siege activates |
| `night.end-tick` | `23000` | When siege ends |
| `siege.mob-damage-multiplier` | `1.4` | Outgoing mob damage multiplier at night |
| `breach.enabled` | `true` | Enable block-breaking |
| `breach.max-breaks-per-night` | `8` | Max blocks a single mob breaks per night |

## Commands

| Command | Description |
|---|---|
| `/nightfall reload` | Reload config without restart |
| `/nightfall debug` | Toggle debug logging |
| `/nightfall status` | Show night state and active breaches |

Permission: `nightfall.admin` (default: op)
