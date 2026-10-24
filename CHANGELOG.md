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
- Letter library per world: letters baked by players are saved with the world, and each player's game
  fetches a letter from the server the first time it shows a stone with it (limits per player and
  minute, at most 32,768 letters per world).
- Commands: `/typestones letters` counts the library, `/typestones letter [hash]` shows a letter's data
  (without hash: the Typestone in hand), `/typestones letter <hash> remove` (operators) removes one.
- Characters: the set of Billboard Engine (A–Z, a–z, 0–9, punctuation, Ä Ö Ü ä ö ü ß, ° · €).
