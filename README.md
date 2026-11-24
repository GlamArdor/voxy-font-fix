# Voxy Font Fix

A client-side Fabric mod for Minecraft 1.21.8 and 26.3 that fixes the broken text you get when Voxy is
installed: letters that disappear after a resource pack load, or come back as a block of coloured
noise. Cyrillic suffers the most, and reloading the pack fixes it only sometimes. Nothing is ever
logged, because from OpenGL's point of view nothing went wrong.

> **Status:** 1.1.0 has been run alongside Voxy on a live server and the reported symptoms are gone.
> It is still a young mod, so please report anything you run into in the issues.

## What actually happens

`GlStateManager` keeps a shadow copy of which texture is bound to each of the twelve texture units.
When Minecraft asks it to bind a texture it already believes is bound, it skips the real
`glBindTexture` call:

```java
public static void _bindTexture(int texture) {
    if (texture != TEXTURES[activeTexture].boundTexture) {   // cache hit -> no GL call at all
        TEXTURES[activeTexture].boundTexture = texture;
        GL11.glBindTexture(GlConst.GL_TEXTURE_2D, texture);
    }
}
```

Voxy bakes a colour and depth capture of every block model whenever resources reload, and it binds
the block atlas for that with direct state access:

```java
GL33.glBindSampler(0, 0);
GL45.glBindTextureUnit(0, blockAtlasId);   // never goes through GlStateManager
```

`glBindTextureUnit` changes the real binding without telling `GlStateManager`, so its cache is now a
lie: it claims unit 0 still holds the last texture vanilla bound, while the driver holds the block
atlas.

The next glyph Minecraft bakes goes through `GlCommandEncoder.writeToTexture`, which binds its
destination with that same cached `_bindTexture`. The bind is skipped, and `glTexSubImage2D` writes
the glyph into **the block atlas instead of the glyph atlas**. The character's slot in the font
texture is never filled, so the letter renders as nothing – and the pixels that were meant to be a
letter end up smeared across a block texture as coloured garbage.

Why Cyrillic in particular: ASCII is baked into the font atlas while the font manager starts up,
before Voxy's bakery runs. Cyrillic comes from the lazily loaded unihex provider, so those glyphs
are baked later, during gameplay, exactly when Voxy is free to trample the binding. And that is also
why a pack reload fixes it *sometimes* – it is a race, not a deterministic failure.

## The red inventory

The same stale cache breaks reads as well as writes, and that one is easier to see: after a resource
pack load every item sprite in the inventory and the hotbar turns red, until you reload the pack with
F3+T. Items lying on the ground, items in your hand and flat GUI sprites are all fine.

Vanilla binds a shader's samplers through the very same cache:

```java
GlStateManager._activeTexture(GL_TEXTURE0 + samplerIndex);   // cached
GlStateManager._bindTexture(glTexture.glId);                 // cached -> may be skipped
```

When Voxy has left one of its own textures on that unit, the bind is skipped and the GUI item shader
samples Voxy's texture where the lightmap should be. The item's colour gets multiplied by whatever
colour sits in the foreign texture, which is why the sprites keep their exact shape and alpha and
only lose their green and blue channels. The world is drawn through paths that rebind every frame, so
it stays correct, and flat GUI sprites never sample the lightmap at all.

F3+T helps because reloading resources recreates the textures with new ids: the cache stops matching
and the bind happens for real. When the ids happen to line up again, it does not help – hence "only
sometimes".

## What the fix does

It never touches Voxy. It makes the real GL state agree with what the game believes:

* **On every bind.** `_activeTexture` and `_bindTexture` issue the driver call they would otherwise
  skip, and drop any sampler object left on the unit. The originals still run and still update the
  cache, so nothing downstream changes – except the bindings are now correct even after a mod has
  bound something behind the cache's back.
* **Before every texture upload** (`GlCommandEncoder.writeToTexture`, both overloads) the destination
  texture is bound for real, and any leftover sampler object is dropped from the active unit.
* **At the start of each frame** and **right after the world is drawn** – which is where Voxy renders
  – every texture unit is put back to the binding `GlStateManager` assumes it has.

Start the game with `-Dvoxyfontfix.audit=true` to have the mod log any unit where the driver and the
cache disagree, at most once per second. It is off by default because reading GL state back stalls
the pipeline.

The same desync is what breaks other things that bind outside `GlStateManager`, so the fix is written
against vanilla only: no dependency on Voxy, no mixin into it, nothing to update when Voxy changes.

## Building

```
./gradlew build
```

The jar lands in `build/libs/`.

### 26.x

From 26.1 the game ships unobfuscated, needs Java 25 and the renderer moved to
`com.mojang.renderpearl`, so 26.3 is a separate source tree in `versions/26` with its own jar
(`voxy-font-fix-<version>+26.jar`). The binding cache is the same, and Voxy 0.2.20 still binds
around it: its own cleanup after rendering goes through the cache and gets skipped, leaving one of
its textures on a unit the game believes is empty.

`versions/26` also has a client game test that walks into a world with the real Voxy and Sodium
(drop their jars into `versions/26/libs/`, Voxy's bundled jars unpacked into `libs/nested`):

```
cd versions/26
./gradlew runClientGameTest             # with the fix
./gradlew runClientGameTest -Ppassive   # fix switched off, audit still on
```

It writes what the audit saw to `build/run/clientGameTest/voxyfontfix-test-*.txt`, along with
screenshots. A "stale bind" line is the exact moment the bug bites: the cache skips a bind while
the driver holds something else.

## Licence

LGPL-3.0-or-later.
