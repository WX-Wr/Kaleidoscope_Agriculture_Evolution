# Crop Style Guide

This guide records the patterns observed in `参考/参考图片/对比`. It is a
working art direction reference, not a requirement that every crop use the same
silhouette.

## Shared Traits Across the Set

### Canvas and alpha

- The inspected comparison textures are all `16x16` PNGs.
- They are flat planted-sprite textures, not cube nets.
- Pixels are hard-edged and use only alpha `0` or alpha `255`; there are no
  semi-transparent pixels.
- The subject generally grows upward from the bottom edge. Roots, soil-facing
  stems, or the plant base occupy the lowest rows.

### Shape language

- Silhouettes are irregular and slightly asymmetrical rather than mirrored.
- Branches, leaves, panicles, and stems use 1-pixel stepped diagonals.
- The subject is made from separated pixel clusters with intentional transparent
  gaps, not a continuous painted mass.
- The visual center is usually vertical, but branches and fruits extend to the
  sides to keep the sprite readable in-world.

### Color language

- Foliage is built from dark, mid, and light greens, with the dark tones in
  overlaps and the light tones on exposed leaf edges.
- Harvest parts have a compact color family with base, shade, and highlight
  patches rather than gradients.
- Tomatoes, chilies, and beets use red families; carrots use orange; wheat and
  rice use olive, yellow-green, and gold; plant bases often use brown or dark
  olive.
- A typical file uses a small palette, roughly 6 to 17 visible RGB colors.

## Crop-Specific Normal/High-Yield Patterns

| Crop family | Normal form | High-yield signal |
|---|---|---|
| Potato | Sparse upright leaves with a small base | More leaves, wider/thicker crown, larger or more numerous potato-colored base pixels |
| Wheat | Dense golden stalks and heads | Denser lower stalk/support area; top may change very little |
| Rice, Farmer's Delight style | Separate supporting stem and panicle roles | Panicle carries the change through more gold grain clusters; support can remain nearly identical |
| Rice, Shênluo Kitchen style | Down/middle/up stage parts | Every stage can become fuller; the high-yield top may show grain earlier and more strongly |
| Onion | Upright green leaves over a small base | More leaf pixels and extra green shade variation; height may stay the same |
| Beetroot | Leafy top with red root mass at the base | Larger red root area, denser leaves, and earlier/more visible harvest mass |
| Tomato, Farmer's Delight style | Branching vines with separated red fruit pixels | More fruit clusters while preserving the branch/vine direction; rope version stays hanging |
| Tomato, Shênluo Kitchen style | Compact leafy plant with sparse fruit | Wider foliage coverage and more red fruit pixels |
| Carrot | Green leaf crown with small orange roots | Taller/wider crown and larger orange base/root mass |
| Chili | Narrow branching plant with red/pink fruit marks | Denser branches, more fruit marks, and a wider silhouette |

## Applying the Pattern

When inventing a new crop in this family:

1. Choose one normal silhouette archetype: leafy root crop, branching fruit
   vine, upright grain, or segmented aquatic/grain plant.
2. Assign a compact palette based on the crop's real harvest color and the
   greens already used by the references.
3. Draw the normal form with deliberate empty gaps and a clear base.
4. Create the high-yield form by adding productive pixels in the crop's
   meaningful location: fruit on branches, grain on panicles, roots at the
   bottom, or leaves around the stem.
5. Keep support textures conservative. The high-yield signal may belong to a
   companion texture rather than every part changing equally.
6. Validate at `16x16` first; only then produce a larger multiple-of-16 version
   if the asset needs additional detail.
