---
name: minecraft-crop-texture
description: "Create or edit vanilla-faithful Minecraft crop textures from local reference images, including normal/high-yield variants and .NET-generated PNG assets. Use for crop plants, stems, leaves, fruits, roots, grains, and related block texture concepts."
metadata:
  short-description: "Generate Minecraft crop textures from reference images"
---

# Minecraft Crop Texture

Use this skill when a Minecraft mod needs pixel-art crop textures based on the
project's local references. The target is a restrained, vanilla-faithful result
that can be generated deterministically with a local .NET drawing library.

## Scope

- Use for: crop plants, growth-stage crops, stems, leaves, fruits, roots,
  grains, vines, and normal/high-yield crop variants.
- Use for: analyzing local comparison images before drawing a new crop in the
  same visual language.
- Use for: generating PNG files and the C#/.NET source that creates them.
- Do not use for: block/item model JSON, blockstates, loot tables, recipes, or
  other resource-pack wiring unless the user also asks for those changes.
- Do not use for: promotional art, logos, banners, or painterly illustrations.

## Non-Negotiable Output Rules

- Preserve Minecraft's crisp pixel-art treatment: hard pixel edges, no
  anti-aliasing, no blur, and no smooth resampling.
- Unless the user explicitly requests otherwise, every pixel must be either
  fully transparent or fully opaque. Never create semi-transparent edge pixels.
- Use a square canvas whose side length is a multiple of 16. Crop plant
  textures normally use `16x16`; use `32x32`, `64x64`, or another multiple only
  when the requested asset needs more detail.
- Generate the image with a local .NET raster library, preferably
  `System.Drawing.Common` on this Windows project. Configure nearest-neighbor
  sampling and disable smoothing/interpolation.
- Keep the deliverable deterministic: commit the generator source and save the
  generated PNG in the project rather than relying on an external image service.
- For an actual block texture, use a cube net with correctly corresponding
  faces. Do not treat a flat crop sprite as a cube net.

## Reference-First Workflow

1. Locate the user's reference directory before drawing. For this project, the
   known comparison set is `参考/参考图片/对比`; do not assume every image in
   `参考/` is relevant.
2. Pair normal and high-yield files by crop and role. Account for multi-part
   assets such as top/middle/bottom rice, supporting stems, panicles, or vines.
3. Inspect dimensions, alpha values, dominant colors, silhouette, and opaque
   pixel coverage. Confirm whether a difference is a real design pattern or a
   role-specific exception.
4. Preserve the normal crop's identity: growth direction, branch placement,
   root/stem position, signature colors, and overall silhouette.
5. Build the high-yield version by increasing visual abundance inside the same
   design language: add fruits, grains, leaves, branches, or root mass; make
   stems sturdier or denser where the reference supports it; reduce empty gaps
   without turning the crop into a solid blob.
6. Render and validate the PNG with a small .NET check or equivalent local
   inspection. Verify dimensions, no partial alpha, crisp pixels, and that the
   subject remains anchored to the bottom when it is a planted crop.

## Normal vs High-Yield Direction

Treat high yield as an enriched version, not an unrelated redesign:

- More visible harvest material: fruits, root blocks, berries, pods, or grain
  heads.
- Denser foliage and branch clusters, with a wider or fuller silhouette when
  appropriate.
- Stronger stems or supporting structures only when the crop's reference shows
  that change.
- More deliberate highlight/shadow patches can improve fullness, but adding
  colors alone is not enough to communicate high yield.
- Keep stage-specific responsibilities intact. A rice support texture may stay
  nearly unchanged while its panicle texture carries the high-yield signal;
  some wheat top textures similarly change very little.
- Do not force every high-yield file to have a larger opaque area. Multi-part
  crops and role-specific support textures are valid exceptions.

Read [references/crop-style-guide.md](references/crop-style-guide.md) when a
task needs the detailed shared style rules or the observed crop-by-crop
patterns.

## Palette and Pixel Construction

- Start from the local reference palette. Do not introduce a new hue family
  unless the crop itself requires it.
- Foliage and stems commonly use dark, middle, and light greens. Use the dark
  tone in inner overlaps or shaded sides and the light tone on exposed edges.
- Harvest parts use a compact family of base, shadow, and highlight colors:
  red for tomato/chili/beet-like crops, orange for carrots, yellow-gold for
  wheat/rice, and brown/olive for soil-facing stems and roots.
- Use small stepped clusters and 1-pixel stair-step contours. Avoid smooth
  curves, outlines that are uniformly black, and random noise unrelated to the
  reference.
- Keep the background transparent for planted sprites unless the user asks for
  a block face or a solid presentation background.

## .NET Generation Requirements

Prefer a small, reviewable C# generator with explicit pixel coordinates and a
small named palette. The generator should:

- create an ARGB bitmap with the requested square dimensions;
- write transparent pixels explicitly and opaque colors with alpha `255`;
- draw rectangles or individual pixels at integer coordinates;
- use nearest-neighbor only for any preview scaling;
- save PNG output with a descriptive, versioned filename;
- avoid hidden dependence on fonts, network assets, or machine-specific input;
- include a validation pass that reports size, opaque/transparent counts, and
  any alpha value other than `0` or `255`.

## Review Checklist

- The image is square and its side is a multiple of 16.
- The output reads correctly at its native size and when nearest-neighbor
  enlarged for inspection.
- No anti-aliased or semi-transparent pixels exist unless explicitly requested.
- The crop is recognizable from its silhouette and harvest color.
- A high-yield variant is visibly fuller or more productive where its role
  allows, while support/stage-specific exceptions remain believable.
- Colors are compact, layered, and consistent with the local reference set.
- Generated PNGs and the .NET source are saved in the project.
