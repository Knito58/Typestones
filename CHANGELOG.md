# Changelog

## 0.1.0 (unreleased)

First version for Minecraft 26.3 (Fabric). Needed on client and server.

- Typestones: smooth stone blocks with a letter, digit or sign on all six sides, in one of the 16 dye
  colors, on plain smooth stone or on a background in a second dye color. Top and bottom follow the
  placement direction. A broken block drops itself with letter and colors.
- Typewriter: makes Typestones from a typed line of text, one block every 5 ticks. Takes smooth
  stone and dyes from inventories on any side and puts the blocks into the inventory at its back.
  Dye settings as ghost slots, buttons for A–Z, 0–9 and all characters, repeat up to 64 times.
- Letters are drawn as flat rectangles in the letter dye, not as textures. A stone stores a hash of
  its letter's pixels, so the game needs no block or texture per character.
- Characters: the set of Billboard Engine (A–Z, a–z, 0–9, punctuation, Ä Ö Ü ä ö ü ß, ° · €).
