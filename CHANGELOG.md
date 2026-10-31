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
  Typewriter's chunk loaded for 5 minutes after each use (same dimension only). An empty pencil in the
  inputs is refilled instead, for free, and keeps its anvil name. An empty pencil thrown
  away bursts into a tiny firework in its colors; put into a flower pot it earns "Potted Pencil".
- Commands: `/typestones letters` counts the library, `/typestones letter [hash]` shows a letter's data
  (without hash: the Typestone in hand), `/typestones letter <hash> remove` (operators) removes one.
- Pictures (first step; typing them will come with the Lua box of the Billboard API): a picture
  covers several stones at 32 x 32 full-color pixels per stone. A picture stone shows its part on two
  opposite sides only, front and back on a wall, top and bottom on a floor or ceiling; the back and
  the underside show the opposite stone's part, so the picture reads right and without seams from both
  sides. The other four sides are plain smooth stone. A stone placed on a floor or ceiling lies flat,
  on a wall it stands, and against a stone of the same picture it lies like that one. Pictures are
  kept per world (data/typestones/pictures, at most 1,024) and sent to players when they see them.
  Operator commands to try them: `/typestones picture test <cols> <rows>`, `/typestones picture load
  <file> <cols> <rows>` (PNG or JPEG from the world's data/typestones/import folder, scaled to fill
  and cut at two sides to keep its proportions), `/typestones picture remove <hash>`, and
  `/typestones pictures` counts them.
- RGB dye: black, white, red, green and blue dye make 8 RGB dye (shapeless). Picture stones will cost
  one each. The dye and its recipe only show when Billboard API or CC: Tweaked is installed, because
  only their programs can type pictures.
- Settings in `config/typestones.properties`, also in game through Mod Menu (optional): resolution of
  new pictures (16, 32 or 64 px per stone; pictures keep the resolution they were made with), largest
  picture (8 x 8 stones by default, up to 16 x 16), and how far picture stones are drawn (4 chunks by
  default).
- Pictures keep their own resolution (16, 32 or 64 px per stone; the first pictures are 32 px), may
  have transparent pixels that show the plain stone beneath, and can be made for a wall or lying
  flat, so their stones stand or lie that way whatever side they are clicked on. Images go in as
  `fit` (whole image, transparent bars), `fill` (covers all, cuts what sticks out) or `stretch`,
  the words of the Billboard Engine.
- The Typewriter types pictures for programs: one smooth stone and one RGB dye per picture stone,
  transparent pixels included. A picture job starts also when items are missing (the program gets a
  warning), waits, and goes on by itself when the inputs are refilled. Programs can ask for the cost
  first. Image files: PNG, JPEG or GIF (first frame), at most 4 MB and 4096 x 4096 pixels.
  `getStatus()` gives a code for programs (`needs_rgb_dye`), `getStatusText()` the sentence for
  people ("Waiting for RGB dye").
- A program must `claim()` the Typewriter before it changes anything (text, start, stop, repeat,
  pictures); reading works without. While a program holds it, the Typewriter's screen shows
  "Program in control" with the job's status and only watches: buttons, text and dye slots are
  locked until the program calls `release()` or ends.
- The pencil also lays pictures: after a picture job the Pencil button makes a pencil that takes its
  stones from the output row by row from the top left. The first click places the top left stone;
  after that, any click lays the next stone in its place, standing or lying like the picture.
  A picture pencil starts at the bottom left and builds up, like a wall; used in the air before the
  first stone, it switches to start at the top left and go down.
- Picture stones and picture pencils name the image they were made from in their tooltip ("From
  fruits.jpg"), so stones of several pictures in one chest can't get mixed up.
- The Pencil button shows a pencil and what it will lay: the line's text or the image name. The ▲▼
  arrows beside it, or the mouse wheel over it, choose between the last job and any other picture
  whose stones are in the output (up to 8); the status line says which one for a moment. The ×N
  repeat moved beside Start and Stop, and the progress is now shown in the status line.
- Characters: the set of Billboard Engine (A–Z, a–z, 0–9, punctuation, Ä Ö Ü ä ö ü ß, ° · €).
