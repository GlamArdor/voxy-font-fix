# Voxy Font Fix

A client-side Fabric mod for Minecraft 1.21.8 that fixes the broken text you get when Voxy is
installed: letters that disappear after a resource pack load, or come back as a block of coloured
noise. Cyrillic suffers the most, and reloading the pack fixes it only sometimes. Nothing is ever
logged, because from OpenGL's point of view nothing went wrong.

> **Status: still being tested.** The mod loads clean and leaves no GL errors behind on a dev
> client, but it has not had a long run alongside Voxy itself yet, so problems are possible. Please
> report anything you run into in the issues.

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

## What the fix does

It never touches Voxy. It makes the real GL state agree with what the game believes, at three points:

* **Before every texture upload** (`GlCommandEncoder.writeToTexture`, both overloads) the destination
  texture is bound for real, and any leftover sampler object is dropped from the active unit. Then
  `_bindTexture` is called so the cache and the driver end up agreeing either way.
* **At the start of each frame** and **right after the world is drawn** – which is where Voxy renders
  – every texture unit is put back to the binding `GlStateManager` assumes it has. Twelve units,
  three GL calls each, twice a frame.

The same desync is what breaks other things that bind outside `GlStateManager`, so the fix is written
against vanilla only: no dependency on Voxy, no mixin into it, nothing to update when Voxy changes.

## Building

```
./gradlew build
```

The jar lands in `build/libs/`.

## Licence

LGPL-3.0-or-later.
