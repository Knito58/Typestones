# Typestones

Fabric mod for Minecraft 26.3: smooth stone blocks that carry a letter on all six sides, in two dye
colors (letter and optional background). The Alpha Station makes them from a typed line of text.

Work in progress. Planned for the first version:

- **Typestone blocks** with the character set of Billboard Engine (A–Z, a–z, 0–9, punctuation,
  Ä Ö Ü ä ö ü ß, ° · €). Top and bottom follow the placement direction, so text on floors and
  ceilings reads correctly. A broken block drops itself with letter and colors.
- **Alpha Station**: takes smooth stone and dyes from inventories on any side, makes one block every
  5 ticks and puts it into the inventory at its back. Ghost slots for letter and background dye,
  buttons for A–Z, 0–9 and all characters, repeat up to 64 times. A space makes a plain block.
- No crafting recipes for the letter blocks, so recipe viewers stay clean.

## Font faces

Glyph textures are generated, not drawn by hand. Each face in `fonts/` becomes a folder of 16×16
textures (white ink on transparent; the block tints it with the dye color):

- `fonts/classic.txt`: Billboard Engine's 5×7 dot-matrix font, as editable text (`#` = ink).
- Any `.ttf` or `.otf` file put into `fonts/` becomes a face named after the file. The font must
  have every character of the set, otherwise the generator lists the missing ones and stops.

After changing a face, run `./gradlew generateGlyphs` and commit the textures. A preview sheet of each
face lands in `build/glyph-preview/`. To try a font installed on the computer without copying it:

```
./gradlew generateGlyphs -Pfont=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf -Pface=dejavu_bold
```

Check the font's license before committing it or its glyphs. `./gradlew build` fails when the committed
textures no longer match `fonts/`.

## Building and testing (Linux)

You need JDK 25 (`java -version` and `javac -version` must both say 25), git and IntelliJ IDEA.

1. In IntelliJ: **File → New → Project from Version Control**, URL `https://github.com/Knito58/typestones`.
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
