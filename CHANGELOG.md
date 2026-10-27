# Changelog

## 0.1.0 (unreleased)

First version for Minecraft 26.3 (Fabric). Needed on client and server.

- Typestones: smooth stone blocks with a letter, digit or sign on all six sides, in one of the 16 dye
  colors, on plain smooth stone or on a background in a second dye color. Top and bottom follow the
  placement direction. A broken block drops itself with letter and colors.
- Typewriter: makes Typestones from a typed line of text, one block every 5 ticks. Takes smooth
  stone and dyes from inventories on any side and puts the blocks into the inventory at its back.
  Dye settings as ghost slots (shift-click fills letter dye, then background dye; the two must
  differ), buttons for A–Z, 0–9 and all characters, repeat up to 64 times.
- Letters are drawn as flat rectangles in the letter dye, not as textures. A stone stores a hash of
  its letter's pixels, so the game needs no block or texture per character.
- Fonts: the Typewriter bakes letters from any font installed on the player's computer, in any
  script, at 16, 32 or 64 pixels, regular, bold, italic or bold italic, with scale and baseline settings and a preview with the cell and
  baseline. New letters go to the world's letter library when Start is pressed. The built-in classic
  face needs no baking.
- Letter library per world: letters baked by players are saved with the world, and each player's game
  fetches a letter from the server the first time it shows a stone with it (limits per player and
  minute, at most 32,768 letters per world).
- Pencil: the Typewriter's Pencil button makes one from a stick and a smooth stone. It places the last
  started line stone by stone from the Typewriter's output inventory: click the last stone to go on to
  its right, click elsewhere to start a new row, sneak-click to put the last stone back. It keeps the
  Typewriter's chunk loaded for 5 minutes after each use (same dimension only). An empty pencil thrown
  away bursts into a tiny firework in its colors; put into a flower pot it earns "Potted Pencil".
- Commands: `/typestones letters` counts the library, `/typestones letter [hash]` shows a letter's data
  (without hash: the Typestone in hand), `/typestones letter <hash> remove` (operators) removes one.
- Characters: the set of Billboard Engine (A–Z, a–z, 0–9, punctuation, Ä Ö Ü ä ö ü ß, ° · €).
