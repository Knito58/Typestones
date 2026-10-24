package de.knito.typestones.glyphgen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.knito.typestones.block.FontFace;
import de.knito.typestones.glyph.GlyphSet;

/**
 * Writes the JSON files of a face's block, which would be tedious by hand: 105 glyphs, each plain and
 * dyed, in four directions. Paths are relative to src/main/resources.
 *
 * <ul>
 * <li>blockstate: glyph, dyed and facing pick a model; the y rotation turns the letter on top and
 *     bottom towards the facing</li>
 * <li>block models: one per glyph and background, on top of the hand-written templates</li>
 * <li>item model: picks the same models by the item's block_state component, tinted by its dyes</li>
 * <li>loot table: the block drops itself with glyph and dyes</li>
 * </ul>
 */
final class BlockAssets {
	private static final String[] FACINGS = {"north", "east", "south", "west"};
	private static final String SMOOTH_STONE = "minecraft:block/smooth_stone";
	private static final String BLANK_DYED = "typestones:block/typestone_blank_dyed";

	private BlockAssets() {
	}

	static Map<String, String> of(FontFace face) {
		Map<String, String> files = new LinkedHashMap<>();
		String block = face.blockName;
		for (int i = 1; i < GlyphSet.size(); i++) {
			String name = GlyphSet.textureName(GlyphSet.charAt(i));
			String glyphTexture = "typestones:block/glyph/" + face.folder + "/" + name;
			files.put("assets/typestones/models/block/" + face.folder + "/" + name + ".json",
					glyphModel("typestones:block/typestone_template", SMOOTH_STONE, glyphTexture));
			files.put("assets/typestones/models/block/" + face.folder + "/" + name + "_dyed.json",
					glyphModel("typestones:block/typestone_template_dyed", "typestones:block/dyed_base", glyphTexture));
		}
		files.put("assets/typestones/blockstates/" + block + ".json", blockstate(face));
		files.put("assets/typestones/items/" + block + ".json", itemModel(face));
		files.put("data/typestones/loot_table/blocks/" + block + ".json", lootTable(block));
		return files;
	}

	static String model(FontFace face, int glyph, boolean dyed) {
		if (glyph == 0) {
			return dyed ? BLANK_DYED : SMOOTH_STONE;
		}
		return "typestones:block/" + face.folder + "/" + GlyphSet.textureName(GlyphSet.charAt(glyph)) + (dyed ? "_dyed" : "");
	}

	private static String glyphModel(String parent, String base, String glyph) {
		return "{\n"
				+ "\t\"parent\": \"" + parent + "\",\n"
				+ "\t\"textures\": {\n"
				+ "\t\t\"base\": \"" + base + "\",\n"
				+ "\t\t\"glyph\": \"" + glyph + "\"\n"
				+ "\t}\n"
				+ "}\n";
	}

	private static String blockstate(FontFace face) {
		List<String> variants = new ArrayList<>();
		for (int glyph = 0; glyph < GlyphSet.size(); glyph++) {
			for (boolean dyed : new boolean[] {false, true}) {
				for (int f = 0; f < FACINGS.length; f++) {
					String rotation = f == 0 ? "" : ", \"y\": " + f * 90;
					variants.add("\t\t\"dyed=" + dyed + ",facing=" + FACINGS[f] + ",glyph=" + glyph + "\": "
							+ "{ \"model\": \"" + model(face, glyph, dyed) + "\"" + rotation + " }");
				}
			}
		}
		return "{\n\t\"variants\": {\n" + String.join(",\n", variants) + "\n\t}\n}\n";
	}

	private static String itemModel(FontFace face) {
		return "{\n"
				+ "\t\"model\": {\n"
				+ "\t\t\"type\": \"minecraft:select\",\n"
				+ "\t\t\"property\": \"minecraft:block_state\",\n"
				+ "\t\t\"block_state_property\": \"dyed\",\n"
				+ "\t\t\"cases\": [\n"
				+ "\t\t\t{\n"
				+ "\t\t\t\t\"when\": \"true\",\n"
				+ "\t\t\t\t\"model\": " + glyphSelect(face, true, "\t\t\t\t") + "\n"
				+ "\t\t\t}\n"
				+ "\t\t],\n"
				+ "\t\t\"fallback\": " + glyphSelect(face, false, "\t\t") + "\n"
				+ "\t}\n"
				+ "}\n";
	}

	private static String glyphSelect(FontFace face, boolean dyed, String indent) {
		List<String> cases = new ArrayList<>();
		for (int glyph = 1; glyph < GlyphSet.size(); glyph++) {
			cases.add(indent + "\t\t{ \"when\": \"" + glyph + "\", \"model\": " + tinted(model(face, glyph, dyed)) + " }");
		}
		return "{\n"
				+ indent + "\t\"type\": \"minecraft:select\",\n"
				+ indent + "\t\"property\": \"minecraft:block_state\",\n"
				+ indent + "\t\"block_state_property\": \"glyph\",\n"
				+ indent + "\t\"cases\": [\n" + String.join(",\n", cases) + "\n" + indent + "\t],\n"
				+ indent + "\t\"fallback\": " + tinted(model(face, 0, dyed)) + "\n"
				+ indent + "}";
	}

	/** Tint 0 is the letter, tint 1 the background, as in the block models. */
	private static String tinted(String model) {
		return "{ \"type\": \"minecraft:model\", \"model\": \"" + model + "\", \"tints\": "
				+ "[{ \"type\": \"typestones:dye\" }, { \"type\": \"typestones:dye\", \"background\": true }] }";
	}

	/** The 26.3 loot format: "modifier" and "condition" with a "type", not "functions"/"conditions". */
	private static String lootTable(String block) {
		return """
				{
					"type": "minecraft:block",
					"pools": [
						{
							"rolls": 1,
							"entries": [
								{
									"type": "minecraft:item",
									"name": "typestones:%1$s",
									"modifier": [
										{
											"type": "minecraft:copy_state",
											"block": "typestones:%1$s",
											"properties": ["glyph", "dyed"]
										},
										{
											"type": "minecraft:copy_components",
											"source": "block_entity",
											"include": ["typestones:dyes"]
										}
									]
								}
							],
							"condition": { "type": "minecraft:survives_explosion" }
						}
					]
				}
				""".formatted(block);
	}
}
