# Changelog

## 0.1.0 (unreleased)

First version for Minecraft 26.3 (Fabric). Needed on client and server.

**Typestones**
- Smooth stone blocks with a letter, digit or sign on all six sides, in one of the 16 dye colors, on
  plain smooth stone or on a background in a second dye color.
- Top and bottom follow the placement direction, so text reads right on walls, floors and ceilings.
- A broken stone drops itself with its letter and colors. No crafting recipes per letter, so recipe
  viewers stay clean.

**Typewriter**
- Makes Typestones from a typed line of up to 128 characters, one stone every 5 ticks, repeated up
  to 64 times. Takes smooth stone and dyes from inventories on any side and puts the stones into the
  inventory at its back.
- Old Typestones in the inputs are used again: a stone just like the next one wanted is moved to the
  output for free, other Typestones (letters or pictures) are wiped and used before smooth stone.
  Stones the running job still wants as they are are kept for it.
- Dye settings as ghost slots for letter and background; buttons for A–Z, 0–9 and every character of
  the font.
- Fonts: the built-in classic face (Billboard Engine's set), or any font installed on the player's
  computer, in any script, at 16, 32 or 64 pixels, regular, bold, italic or bold italic, with scale
  and baseline settings and a preview.
- Joined text: a switch (Joined / Spaced) in the font panel. Joined sets the whole line as one piece
  and cuts it into stones, so Arabic and other connected scripts join across stones, punctuation
  stays at its word and a line needs fewer stones. Each stone's name lists the characters it touches,
  with "…" where one goes on in the neighbour. Scripts that need it switch it on at Start.
- Letters are drawn as flat rectangles in the letter dye, not as textures. Letters baked by players
  are kept in the world's letter library (up to 32,768) and sent to other players when they see them.

**Pencil**
- The Typewriter's Pencil button makes a pencil from a stick and a smooth stone, or refills an empty
  pencil found in its inputs for free (an anvil name stays). A pencil that is only half used is
  recycled the same way, after any empty one: it is the only way to get rid of one, since it doesn't
  burst when thrown.
- A pencil places the last line stone by stone from the Typewriter's output: click the last stone to
  go on to its right, click elsewhere to start a new row, sneak-click to put the last stone back. It
  keeps the Typewriter's chunk loaded for 5 minutes after each use.
- A picture pencil lays a picture row by row, starting bottom left and building up like a wall; used
  in the air before the first stone, it starts top left instead.
- ▲▼ arrows or the mouse wheel beside the Pencil button choose between the last job and any other
  picture whose stones are in the output.
- An empty pencil thrown away (Q, dragged out, or right-clicked like an egg) bursts into a tiny
  firework in its colors. Put into a flower pot, it earns "Potted Pencil".

**Pictures** (with [Billboard API](https://github.com/Knito58/Billboard-API) or CC: Tweaked)
- A program on a Lua Box (or a CC: Tweaked computer) can have the Typewriter type an image (PNG, JPEG,
  GIF first frame) as picture stones: up to 8 x 8 stones by default, at 16, 32 or 64 pixels per stone,
  fitted (`fit`), filled (`fill`) or stretched (`stretch`), for a wall, floor or ceiling.
- Each picture stone costs one smooth stone and one RGB dye (black, white, red, green and blue dye make
  8). Without Billboard API or CC: Tweaked the RGB dye and its recipe stay hidden.
- Picture stones show their part front and back, so the picture reads right from both sides, and name
  the image they come from in their tooltip. Transparent pixels show the plain stone.
- Lua functions: `claim`, `release`, `isClaimed`, `setText`, `getText`, `go`, `stop`, `isRunning`,
  `setRepeat`, `getRepeat`, `drawImage`, `isPainting`, `getCost`, `getStatus`, `getStatusText`,
  `getProgress`. A program must `claim()` the Typewriter before it changes anything; while it does,
  the Typewriter's screen shows "Program in control" and is locked.

**Settings and commands**
- `config/typestones.properties`, also through Mod Menu (optional): resolution of new pictures,
  largest picture (up to 16 x 16 stones), how far picture stones are drawn.
- `/typestones letters`, `/typestones letter [hash]`, `/typestones pictures`; for operators also
  `/typestones letter <hash> remove`, `/typestones picture load|test|remove`.
