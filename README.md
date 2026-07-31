# Rainbow Trim

Armour-trim add-on for **Rainbow Dye**. Minecraft **26.2**, Fabric.

Adds one item: the **Rainbow Trim Upgrade**. Use it in a smithing table exactly like a
diamond or an emerald, and the armour comes out with a trim that sweeps smoothly through
the entire spectrum — a rainbow gradient that animates on its own, on all 18 vanilla trim
patterns.

## Requires

- **Rainbow Dye 1.6.0+** (the recipe uses Rainbow Dye)
- Fabric API
- Java 21+ toolchain to build; the mod itself runs on Java 25 / MC 26.2

## Recipe

```
      [ ]  [Netherite Upgrade]  [ ]
   [Netherite Ingot] [Rainbow Dye] [Netherite Ingot]
      [ ]  [Netherite Ingot]     [ ]
```

Yields 1. The recipe appears in the recipe book the moment a Rainbow Dye enters your
inventory.

## How the animation works

Armour trims are normally coloured by the `paletted_permutations` atlas source: it takes
a greyscale pattern texture and swaps the greys for a material's palette. That swap
happens **once, at resource load**, which is why a "rainbow palette" alone can never
animate.

So this mod skips the palette step for its own material and ships pre-rendered animated
sprites instead — 16 frames each, `interpolate: true`, one full spectrum every ~4.8
seconds. They're bound to the exact sprite names vanilla looks up for `pattern + rainbow`,
so as far as the game is concerned they simply *are* the rainbow trim.

- **Worn armour** — 36 sprites (18 patterns × humanoid + leggings), declared in
  `assets/minecraft/atlases/armor_trims.json`.
- **Item icons** — *not* animated, and deliberately so. `assets/minecraft/atlases/items.json`
  adds a `rainbow` permutation to vanilla's own item-trim source, so the four icon sprites
  are generated from an 8-colour palette at load. That's one 8×1 PNG instead of four
  animated sheets, and the icon still reads as a rainbow because the palette spreads the
  spectrum across the trim's shading levels. Note this is a **separate** atlas from the
  worn trims.

### Item icons need a model too, not just a sprite

Getting the sprite into the atlas isn't enough. Each armour item's definition at
`assets/minecraft/items/<item>.json` is a `minecraft:select` on `minecraft:trim_material`,
with one case per material pointing at a model that stacks the trim sprite over the base
item texture. A material with no case just falls through to the untrimmed icon — which is
why 1.2.0 shipped correct sprites and showed nothing.

So the mod overrides all 29 trimmable armour item definitions to add a
`rainbowtrim:rainbow` case, and ships a matching model per item.

**These files replace vanilla's rather than merging** (unlike atlases and tags). Two
consequences worth knowing:

- Another mod that overrides the same item definitions will conflict — last pack loaded
  wins, and one mod's trim material will stop showing an icon.
- If Mojang changes those files in a future version, these copies go stale and need
  regenerating.

Each case is cloned from vanilla's own first case rather than written from scratch, so
per-item extras survive — notably leather's `minecraft:dye` tint, which lives on the case
wrapper and not in the model file. Dyed leather with a rainbow trim keeps its dye colour.

### Why the worn trim is one colour at a time

The armour UV sheet unwraps every body part into six separate faces laid side by side, so
a hue that varies with x gives the back of the helmet a different colour from its front,
and the top of the leggings a different colour from the bottom. Version 1.1.0 did exactly
that and looked out of step with itself.

The worn trim now takes its hue from **time alone** — every pixel of every face of every
piece is the same hue on the same frame, so a full set always matches. Brightness and
saturation still come from the greyscale, so it keeps its shading and doesn't look flat.

Item icons are static — a rainbow gradient straight from the palette, no animation and no
texture files of their own.

No mixins. No client code. No renderer hooks. The only Java in the mod is one item
registration.

### The atlas file, and why it also contains vanilla's source

`assets/minecraft/atlases/armor_trims.json` includes a full copy of vanilla's own
`paletted_permutations` source with a `rainbow` permutation appended, and *then* the 36
animated sprites.

- Atlas files merge across packs, so vanilla's source appearing twice is a harmless
  duplicate.
- If some pack ever replaced the file rather than merging, vanilla's trims still work,
  because the complete source is right there.
- If an animated sprite ever fails to load, the permutation underneath it produces a
  static rainbow gradient instead of a missing texture.

The animated sources are declared last, so they win the name collision.

## Building

Set the version block in `gradle.properties` from <https://fabricmc.net/develop> if you
retarget. `./gradlew build` with JDK 25; jar lands in `build/libs/`.

Everything sits at the **repository root** — `build.gradle`, `settings.gradle`, `gradlew`,
`src/`, `.github/`. If GitHub shows a folder you have to click into before you see
`build.gradle`, CI fails with "does not contain a Gradle build".

## How the item becomes a trim ingredient

`minecraft:provides_trim_material` is typed `Holder<TrimMaterial>`, and `trim_material` is
a **datapack** registry — so no Holder for it exists yet when items are registered. It
therefore cannot be a default component on the item. (1.0.0 tried a `ResourceKey` there;
it registered cleanly and then threw `ClassCastException` inside `SmithingTrimRecipe` every
time a smithing table looked at it, kicking the player out of the world.)

The component is applied where a real registry lookup exists instead:

- **Crafted upgrades** — the recipe's `result.components` carries it, so the game builds
  the Holder itself with the correct codec.
- **Creative tab** — resolved through the tab's own `HolderLookup.Provider`.

Both paths produce a genuine Holder. If the creative lookup ever fails it logs a warning
and falls back to a plain item — not a valid ingredient, but never a crash.

**Upgrades crafted with 1.0.0 are inert.** They relied on the broken default component,
which no longer exists. Craft fresh ones.

## File count

Roughly 143 files, and it breaks down like this:

| what | files | why |
|---|---|---|
| worn-armour animation | 72 | 18 patterns × 2 layers, each a PNG + `.mcmeta` |
| icon plumbing | 59 | 29 item definitions + 30 models |
| everything else | ~12 | item texture, palette, atlases, recipe, tag, lang, trim material |

Both big groups are load-bearing. The 72 are the animation itself. The 59 are what make
any icon appear at all — an armour item with no case for our material just falls through
to the untrimmed icon.

The one lever left is frame count: regenerating the worn sprites at 8 frames instead of 16
(`frametime: 12` keeps the same 4.8 s loop) halves their size, though not their count.
Interpolation keeps it smooth.
