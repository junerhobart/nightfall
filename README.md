# Nightfall

Night should be dangerous again.

Nightfall makes hostile mobs remember where you were, follow noise and light, and sometimes come through the wall instead of standing outside like idiots.

## What it does

- Turns night into an actual threat
- Adds a heat system so player actions leave a trail mobs can follow
- Pulls idle mobs toward torches and light sources
- Lets some mobs break through softer blocks
- Gives mobs different breach roles
- Caps a lot of the expensive stuff so the server does not immediately explode

This is not meant to be subtle. If you want nights to feel tense, this is the point.

## What heat means

Mobs do not just forget you because you stepped behind a wall.

Things like:

- sprinting
- breaking blocks
- placing blocks
- shooting
- getting hurt

can leave heat behind. Mobs can drift toward that even after losing line of sight.

## Breach behavior

Some mobs can break softer blocks when they get stuck outside.

That includes things like:

- doors
- glass
- wool
- other low-tier blocks from the breach config

Protected blocks stay protected. If you do not want mobs chewing through builds, turn breach settings down or turn them off.

## Install

- Use Paper `1.21.11+`
- Drop the jar into `plugins/`
- Start the server once
- Edit `plugins/Nightfall/config.yml`
- Run `/nightfall reload`

## Commands

Permission: `nightfall.admin`

- `/nightfall reload`
- `/nightfall debug`
- `/nightfall status`

## Config

You can change:

- what counts as night
- which worlds are enabled
- mob damage multipliers
- heat decay and scan radius
- light attraction blocks
- breach tiers and protected blocks
- mob roles
- global and per-chunk caps

If you are running a bigger server, test this before you trust it. I mean that.

## Build

```bash
mvn clean package
```

Output goes to `target/Nightfall-1.02.jar`.
