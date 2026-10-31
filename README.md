# Typestones

Fabric mod for Minecraft 26.3: smooth stone blocks that carry a letter on all six sides, in two dye
colors (letter and optional background), and picture stones that together show an image. The
Typewriter makes them from a typed line of text, or from an image a program sends it.

- **Typestones**: any character of any font installed on the player's computer, at 16, 32 or 64 px,
  or the built-in classic face. Top and bottom follow the placement direction, so text reads right
  on walls, floors and ceilings. A broken stone drops itself with letter and colors. No recipes per
  letter, so recipe viewers stay clean.
- **Typewriter**: takes smooth stone and dyes from inventories on any side, makes one stone every
  5 ticks and puts it into the inventory at its back. Ghost slots for letter and background dye,
  buttons for A–Z, 0–9 and every character, repeat up to 64 times.
- **Pencil**: places the Typewriter's line or picture in the world, stone by stone, straight from its
  output. Empty pencils are refilled by the Typewriter.
- **Pictures** (needs [Billboard API](https://github.com/Knito58/Billboard-API) or CC: Tweaked): a
  program has the Typewriter turn a PNG, JPEG or GIF into picture stones, for one smooth stone and one
  RGB dye each. The Lua functions are listed in `CHANGELOG.md`.
- Optional: Mod Menu for the settings screen.

See `CHANGELOG.md` for everything in detail.

## Font faces

Letters are generated, not drawn by hand. Each face in `fonts/` becomes a file of 16×16 ink
pixels, `src/main/resources/typestones/letters/<face>.txt`. The game draws each letter as a few flat
rectangles in the letter dye, so no textures are needed. A stone stores only a hash of its letter's
pixels.

- `fonts/classic.txt`: Billboard Engine's 5×7 dot-matrix font, as editable text (`#` = ink). This is
  the built-in classic face; fonts chosen in the Typewriter are baked in the game instead.
- Any `.ttf` or `.otf` file put into `fonts/` becomes a face named after the file. The font must
  have every character of the set, otherwise the generator lists the missing ones and stops.

After changing a face, run `./gradlew generateGlyphs` and commit the letter files. A preview sheet of
each face lands in `build/glyph-preview/`. To try a font installed on the computer without copying it:

```
./gradlew generateGlyphs -Pfont=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf -Pface=dejavu_bold
```

Check the font's license before committing it or its letters. `./gradlew build` fails when the
committed letter files no longer match `fonts/`.

## Building and testing (Linux)

You need JDK 25 (`java -version` and `javac -version` must both say 25), git and IntelliJ IDEA.

1. In IntelliJ: **File → New → Project from Version Control**, URL `https://github.com/Knito58/Typestones`.
2. Wait until the Gradle import finishes; the first time it downloads Minecraft and Fabric.
3. Gradle tool window: **Tasks → fabric → runClient**.

## Releasing

Pushing to the `release` branch builds the mod, creates a GitHub release from the newest
`CHANGELOG.md` section and uploads the jar to CurseForge. The upload needs the repository secret
`CURSEFORGE_TOKEN` and the project id in `.github/workflows/release.yml` (`CURSEFORGE_PROJECT_ID`).
*Actions → check CurseForge token → Run workflow* tests the token without uploading anything.

## How this mod is made

Typestones is written by Claude AI (Anthropic), code, textures and icon included. Konitor decides
what it does and tests it.

## License

MIT, see `LICENSE`.
