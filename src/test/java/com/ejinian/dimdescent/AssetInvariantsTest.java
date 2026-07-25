package com.ejinian.dimdescent;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

// Static checks over the resource tree. No Minecraft, no game launch - these read the assets as plain
// files, so the whole suite runs in about a second and can be trusted not to rot when game APIs move.
//
// This exists because of a real regression: the cutout render layers for datura, the dark iron bars
// and the Daemonlight were registered in Java, inside the Rift Door's client class. Retiring the door
// deleted that class and silently took the render layers with it, so every transparent block in the
// mod started drawing its alpha as opaque black. Nothing failed to compile and nothing logged a
// warning - the only symptom was a screenshot.
//
// The fix was to move that declaration into the block models ("render_type": "minecraft:cutout"),
// where it sits beside the texture it describes. These tests then make the invariant enforceable:
// a texture with transparent pixels must be drawn by a model that declares a see-through render type.
class AssetInvariantsTest {

    private static final Path ASSETS = Paths.get("src/main/resources/assets/dimdescent");
    private static final Path MODELS = ASSETS.resolve("models");
    private static final Path BLOCKSTATES = ASSETS.resolve("blockstates");

    // Texture entries are "<slot>": "dimdescent:block/foo", where the slot name is arbitrary ("cross",
    // "layer0", "particle"...). "parent" and "model" use the same shape but point at MODELS, not
    // textures, so they have to be excluded or every parented model looks like a missing texture.
    private static final Pattern TEXTURE_REF =
            Pattern.compile("\"([a-z0-9_]+)\"\\s*:\\s*\"dimdescent:(block|item)/([a-z0-9_/]+)\"");
    private static final Set<String> NON_TEXTURE_KEYS = Set.of("parent", "model");

    private static final Pattern MODEL_REF = Pattern.compile("\"model\"\\s*:\\s*\"dimdescent:([a-z0-9_/]+)\"");
    private static final Pattern PARENT_REF = Pattern.compile("\"parent\"\\s*:\\s*\"dimdescent:([a-z0-9_/]+)\"");

    // A BLOCK texture with any pixel below full opacity cannot be drawn on the default (solid) layer,
    // or those pixels render as their raw RGB - which for our textures is black. Item models are
    // exempt: the item renderer already handles alpha, and they never declare a render type.
    @Test
    void blockModelsUsingTransparentTexturesDeclareASeeThroughRenderType() throws IOException {
        Set<String> transparent = transparentTextureNames();
        List<String> offenders = new ArrayList<>();

        for (Path model : jsonFiles(MODELS.resolve("block"))) {
            String json = Files.readString(model);
            if (json.contains("\"render_type\"")) {
                continue;
            }
            for (String texture : referencedTextures(json)) {
                if (transparent.contains(texture)) {
                    offenders.add(ASSETS.relativize(model) + " draws transparent texture '" + texture
                            + "' but declares no render_type (add \"render_type\": \"minecraft:cutout\")");
                    break;
                }
            }
        }
        assertTrue(offenders.isEmpty(), "Transparent textures on the solid render layer:\n  "
                + String.join("\n  ", offenders));
    }

    @Test
    void everyTextureReferencedByAModelExists() throws IOException {
        List<String> missing = new ArrayList<>();
        for (Path model : jsonFiles(MODELS)) {
            String json = Files.readString(model);
            Matcher m = TEXTURE_REF.matcher(json);
            while (m.find()) {
                if (NON_TEXTURE_KEYS.contains(m.group(1))) {
                    continue;
                }
                Path texture = ASSETS.resolve("textures").resolve(m.group(2)).resolve(m.group(3) + ".png");
                if (!Files.exists(texture)) {
                    missing.add(ASSETS.relativize(model) + " -> missing texture " + ASSETS.relativize(texture));
                }
            }
        }
        assertTrue(missing.isEmpty(), "Models referencing textures that do not exist:\n  "
                + String.join("\n  ", missing));
    }

    @Test
    void everyModelReferencedByABlockstateOrParentExists() throws IOException {
        List<String> missing = new ArrayList<>();
        List<Path> sources = new ArrayList<>(jsonFiles(BLOCKSTATES));
        sources.addAll(jsonFiles(MODELS));

        for (Path source : sources) {
            String json = Files.readString(source);
            for (Pattern pattern : List.of(MODEL_REF, PARENT_REF)) {
                Matcher m = pattern.matcher(json);
                while (m.find()) {
                    Path model = MODELS.resolve(m.group(1) + ".json");
                    if (!Files.exists(model)) {
                        missing.add(ASSETS.relativize(source) + " -> missing model " + ASSETS.relativize(model));
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "References to models that do not exist:\n  "
                + String.join("\n  ", missing));
    }

    private static Set<String> transparentTextureNames() throws IOException {
        Set<String> transparent = new TreeSet<>();
        Path textures = ASSETS.resolve("textures");
        if (!Files.isDirectory(textures)) {
            return transparent;
        }
        try (Stream<Path> walk = Files.walk(textures)) {
            for (Path png : walk.filter(p -> p.toString().endsWith(".png")).toList()) {
                BufferedImage image = ImageIO.read(png.toFile());
                if (image == null || !image.getColorModel().hasAlpha()) {
                    continue;
                }
                if (hasTransparentPixel(image)) {
                    String name = png.getFileName().toString();
                    transparent.add(name.substring(0, name.length() - ".png".length()));
                }
            }
        }
        return transparent;
    }

    private static boolean hasTransparentPixel(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) < 255) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Set<String> referencedTextures(String json) {
        Set<String> names = new HashSet<>();
        Matcher m = TEXTURE_REF.matcher(json);
        while (m.find()) {
            if (NON_TEXTURE_KEYS.contains(m.group(1))) {
                continue;
            }
            String path = m.group(3);
            names.add(path.substring(path.lastIndexOf('/') + 1));
        }
        return names;
    }

    private static List<Path> jsonFiles(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
    }
}
