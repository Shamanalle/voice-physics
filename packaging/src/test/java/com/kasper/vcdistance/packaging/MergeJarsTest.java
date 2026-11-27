package com.kasper.vcdistance.packaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.module.ResolutionException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class MergeJarsTest {

    @TempDir
    Path dir;

    private static byte[] emptyClass(String name, int version) {
        ClassWriter w = new ClassWriter(0);
        w.visit(version, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        w.visitEnd();
        return w.toByteArray();
    }

    private Path folder(String name, Map<String, byte[]> files) throws IOException {
        Path root = dir.resolve(name);
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            Path p = root.resolve(e.getKey());
            Files.createDirectories(p.getParent());
            Files.write(p, e.getValue());
        }
        return root;
    }

    private Path jarFile(String fileName, Map<String, byte[]> files) throws IOException {
        Path jar = dir.resolve(fileName);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        return jar;
    }

    private static byte[] text(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void jabelCompileOnlyStubIsStrippedAndForeignPackagesAreRejected() throws IOException {
        Map<String, byte[]> commonFiles = new LinkedHashMap<>();
        commonFiles.put("com/kasper/vcdistance/Common.class", emptyClass("com/kasper/vcdistance/Common", Opcodes.V1_8));
        commonFiles.put("com/github/bsideup/jabel/Desugar.class", emptyClass("com/github/bsideup/jabel/Desugar", Opcodes.V1_8));
        Path common = folder("commonWithJabel", commonFiles);

        Map<String, byte[]> baseFiles = new LinkedHashMap<>();
        baseFiles.put("vcd-bands.properties", text("bands=mc1_21\n"));
        baseFiles.put("com/github/bsideup/jabel/Desugar.class", emptyClass("com/github/bsideup/jabel/Desugar", Opcodes.V1_8));
        Path base = folder("baseWithJabel", baseFiles);

        Map<String, byte[]> bandFiles = new LinkedHashMap<>();
        bandFiles.put("com/kasper/vcdistance/Common.class", emptyClass("com/kasper/vcdistance/Common", Opcodes.V21));
        bandFiles.put("com/kasper/vcdistance/AudioDistanceMod.class", emptyClass("com/kasper/vcdistance/AudioDistanceMod", Opcodes.V21));
        bandFiles.put("com/github/bsideup/jabel/Desugar.class", emptyClass("com/github/bsideup/jabel/Desugar", Opcodes.V21));
        bandFiles.put("fabric.mod.json", text("{}"));
        Path bandDir = folder("band21WithJabel", bandFiles);

        Path out = dir.resolve("clean.jar");
        new MergeJars().run(out, List.of(common), List.of(base), Map.of("mc1_21", bandDir),
                new HashMap<>(), Integer.MAX_VALUE, Integer.MAX_VALUE, null);

        try (ZipFile zip = new ZipFile(out.toFile())) {
            assertNull(zip.getEntry("com/github/bsideup/jabel/Desugar.class"));
            assertNull(zip.getEntry("com/github/bsideup/jabel/"));
            assertNull(zip.getEntry("com/github/bsideup/"));
            assertNull(zip.getEntry("com/github/"));
            assertNotNull(zip.getEntry("com/kasper/vcdistance/Common.class"));
            assertNotNull(zip.getEntry("com/kasper/vcdistance/b/mc1_21/AudioDistanceMod.class"));
            zip.stream().filter(e -> e.getName().endsWith(".class")).forEach(e ->
                    assertTrue(e.getName().startsWith("com/kasper/vcdistance/"), "Unexpected class: " + e.getName()));
        }

        Path pollutedCommon = folder("pollutedCommon", Map.of(
                "com/kasper/vcdistance/Common.class", emptyClass("com/kasper/vcdistance/Common", Opcodes.V1_8),
                "org/example/Foreign.class", emptyClass("org/example/Foreign", Opcodes.V1_8)));
        IllegalStateException err = assertThrows(IllegalStateException.class, () -> new MergeJars().run(
                dir.resolve("bad-common.jar"), List.of(pollutedCommon), List.of(base),
                Map.of("mc1_21", bandDir), new HashMap<>(), Integer.MAX_VALUE, Integer.MAX_VALUE, null));
        assertTrue(err.getMessage().contains("org/example/Foreign.class"), err.getMessage());

        Path pollutedBand = folder("pollutedBand", Map.of(
                "com/kasper/vcdistance/AudioDistanceMod.class", emptyClass("com/kasper/vcdistance/AudioDistanceMod", Opcodes.V21),
                "org/example/BandForeign.class", emptyClass("org/example/BandForeign", Opcodes.V21)));
        IllegalStateException bandErr = assertThrows(IllegalStateException.class, () -> new MergeJars().run(
                dir.resolve("bad-band.jar"), List.of(common), List.of(base),
                Map.of("mc1_21", pollutedBand), new HashMap<>(), Integer.MAX_VALUE, Integer.MAX_VALUE, null));
        assertTrue(bandErr.getMessage().contains("org/example/BandForeign"), bandErr.getMessage());
    }

    @Test
    void mergedJarResolvesInJpmsAlongsideCompanionModsWithoutSplitPackages() throws IOException {
        // 1. Reproduce the exact Forge/NeoForge JPMS crash when two jars both ship com.github.bsideup.jabel.Desugar
        Path unstrippedVp = jarFile("unstripped-vp.jar", Map.of(
                "com/kasper/vcdistance/Common.class", emptyClass("com/kasper/vcdistance/Common", Opcodes.V1_8),
                "com/github/bsideup/jabel/Desugar.class", emptyClass("com/github/bsideup/jabel/Desugar", Opcodes.V1_8)));
        Path unstrippedMs = jarFile("unstripped-ms.jar", Map.of(
                "com/kasper/mobsenses/Common.class", emptyClass("com/kasper/mobsenses/Common", Opcodes.V1_8),
                "com/github/bsideup/jabel/Desugar.class", emptyClass("com/github/bsideup/jabel/Desugar", Opcodes.V1_8)));
        ModuleFinder brokenFinder = ModuleFinder.of(unstrippedVp, unstrippedMs);
        ResolutionException crash = assertThrows(ResolutionException.class, () ->
                ModuleLayer.boot().configuration().resolve(brokenFinder, ModuleFinder.of(), Set.of("unstripped.vp", "unstripped.ms")));
        assertTrue(crash.getMessage().contains("com.github.bsideup.jabel"), crash.getMessage());

        // 2. Verify MergeJars output resolves cleanly alongside companion mods in the same JPMS ModuleLayer
        Path common = folder("jpmsCommon", Map.of(
                "com/kasper/vcdistance/Common.class", emptyClass("com/kasper/vcdistance/Common", Opcodes.V1_8),
                "com/github/bsideup/jabel/Desugar.class", emptyClass("com/github/bsideup/jabel/Desugar", Opcodes.V1_8)));
        Path base = folder("jpmsBase", Map.of("vcd-bands.properties", text("bands=forge_1_21\n")));
        Path band = folder("jpmsBand", Map.of(
                "com/kasper/vcdistance/ForgeEntry.class", emptyClass("com/kasper/vcdistance/ForgeEntry", Opcodes.V21),
                "com/github/bsideup/jabel/Desugar.class", emptyClass("com/github/bsideup/jabel/Desugar", Opcodes.V21)));
        Path cleanVp = dir.resolve("voice-physics-clean.jar");
        new MergeJars().run(cleanVp, List.of(common), List.of(base), Map.of("forge_1_21", band),
                new HashMap<>(), Integer.MAX_VALUE, Integer.MAX_VALUE, null);

        Path cleanMs = jarFile("mob-senses-clean.jar", Map.of(
                "com/kasper/mobsenses/Common.class", emptyClass("com/kasper/mobsenses/Common", Opcodes.V1_8),
                "com/kasper/mobsenses/b/forge_1_21/ForgeEntry.class", emptyClass("com/kasper/mobsenses/b/forge_1_21/ForgeEntry", Opcodes.V21)));

        ModuleFinder cleanFinder = ModuleFinder.of(cleanVp, cleanMs);
        Configuration resolved = ModuleLayer.boot().configuration().resolve(
                cleanFinder, ModuleFinder.of(), Set.of("voice.physics.clean", "mob.senses.clean"));
        ModuleReference vpRef = resolved.findModule("voice.physics.clean").orElseThrow().reference();
        Set<String> pkgs = vpRef.descriptor().packages();
        assertEquals(Set.of("com.kasper.vcdistance", "com.kasper.vcdistance.b.forge_1_21"), pkgs);
    }
}
