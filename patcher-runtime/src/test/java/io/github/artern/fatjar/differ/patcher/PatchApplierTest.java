package io.github.artern.fatjar.differ.patcher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.artern.fatjar.differ.core.ExecutablePatchJarBuilder;
import io.github.artern.fatjar.differ.core.JarSnapshot;
import io.github.artern.fatjar.differ.core.SpringBootFatJarScanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PatchApplierTest {

  @TempDir Path tempDir;

  @Test
  void appliesPatchBundleAndMatchesTargetSnapshot() throws Exception {
    Path baselineJar = tempDir.resolve("baseline.jar");
    Path targetJar = tempDir.resolve("target.jar");
    Path templateJar = tempDir.resolve("template.jar");
    Path patchBundle = tempDir.resolve("spring-boot-fat-jar-patcher.jar");
    Path outputJar = tempDir.resolve("output.jar");
    List<String> progressMessages = new ArrayList<String>();

    writeJar(baselineJar, baselineEntries());
    writeJar(targetJar, targetEntries());
    writeTemplateJar(templateJar);

    ExecutablePatchJarBuilder builder = new ExecutablePatchJarBuilder();
    try (InputStream templateStream = Files.newInputStream(templateJar)) {
      builder.build(baselineJar, targetJar, templateStream, patchBundle, "test-version");
    }

    new PatchApplier(progressMessages::add).apply(baselineJar, patchBundle, outputJar);

    SpringBootFatJarScanner scanner = new SpringBootFatJarScanner();
    JarSnapshot actual = scanner.scan(outputJar);
    JarSnapshot expected = scanner.scan(targetJar);
    assertEquals(expected.getAllEntries().size(), actual.getAllEntries().size());
    assertEquals(expected.getAllEntries().keySet(), actual.getAllEntries().keySet());
    for (Map.Entry<String, io.github.artern.fatjar.differ.core.JarEntrySnapshot> entry :
        expected.getAllEntries().entrySet()) {
      assertEquals(
          entry.getValue().getCrc32(), actual.getAllEntries().get(entry.getKey()).getCrc32());
      assertEquals(
          entry.getValue().getSize(), actual.getAllEntries().get(entry.getKey()).getSize());
    }
    assertStored(outputJar, "BOOT-INF/lib/dependency.jar");
    assertContains(progressMessages, "Baseline validation passed.");
    assertContains(progressMessages, "[REPLACE_ENTRY] BOOT-INF/lib/dependency.jar (method=STORED)");
    assertEquals(
        expected.getArchivePreamble().getSha256(), actual.getArchivePreamble().getSha256());
  }

  @Test
  void appliesPatchBundleForExecutableWarWithLaunchScript() throws Exception {
    Path baselineWar = tempDir.resolve("baseline.war");
    Path targetWar = tempDir.resolve("target.war");
    Path templateJar = tempDir.resolve("template.jar");
    Path patchBundle = tempDir.resolve("spring-boot-fat-jar-patcher.jar");
    Path outputWar = tempDir.resolve("output.war");

    writeArchive(baselineWar, warEntries("old"), "#!/bin/sh\necho old\n");
    writeArchive(targetWar, warEntries("new"), "#!/bin/sh\necho new\n");
    writeTemplateJar(templateJar);

    ExecutablePatchJarBuilder builder = new ExecutablePatchJarBuilder();
    try (InputStream templateStream = Files.newInputStream(templateJar)) {
      builder.build(baselineWar, targetWar, templateStream, patchBundle, "test-version");
    }

    new PatchApplier().apply(baselineWar, patchBundle, outputWar);

    SpringBootFatJarScanner scanner = new SpringBootFatJarScanner();
    JarSnapshot actual = scanner.scan(outputWar);
    JarSnapshot expected = scanner.scan(targetWar);
    assertEquals(
        expected.getArchivePreamble().getSha256(), actual.getArchivePreamble().getSha256());
    assertEquals(
        expected
            .getLogicalUnit(io.github.artern.fatjar.differ.core.LogicalArea.WEB_INF_CLASSES)
            .getFingerprint(),
        actual
            .getLogicalUnit(io.github.artern.fatjar.differ.core.LogicalArea.WEB_INF_CLASSES)
            .getFingerprint());
    assertEquals(expected.getAllEntries().keySet(), actual.getAllEntries().keySet());
  }

  @Test
  void preservesExplicitDirectoryMetadataInsideLogicalTrees() throws Exception {
    Path baselineJar = tempDir.resolve("baseline-dir.jar");
    Path targetJar = tempDir.resolve("target-dir.jar");
    Path templateJar = tempDir.resolve("template.jar");
    Path patchBundle = tempDir.resolve("spring-boot-fat-jar-patcher.jar");
    Path outputJar = tempDir.resolve("output-dir.jar");

    writeArchive(
        baselineJar,
        classTreeEntries("old-app"),
        null,
        Collections.singleton("BOOT-INF/classes/com/"));
    writeArchive(
        targetJar,
        classTreeEntries("new-app"),
        null,
        Collections.singleton("BOOT-INF/classes/com/"));
    writeTemplateJar(templateJar);

    ExecutablePatchJarBuilder builder = new ExecutablePatchJarBuilder();
    try (InputStream templateStream = Files.newInputStream(templateJar)) {
      builder.build(baselineJar, targetJar, templateStream, patchBundle, "test-version");
    }

    new PatchApplier().apply(baselineJar, patchBundle, outputJar);

    assertStored(outputJar, "BOOT-INF/classes/com/");

    SpringBootFatJarScanner scanner = new SpringBootFatJarScanner();
    JarSnapshot actual = scanner.scan(outputJar);
    JarSnapshot expected = scanner.scan(targetJar);
    assertEquals(expected.getAllEntries().keySet(), actual.getAllEntries().keySet());
    assertEquals(
        expected.getAllEntries().get("BOOT-INF/classes/com/").getMethod(),
        actual.getAllEntries().get("BOOT-INF/classes/com/").getMethod());
  }

  private Map<String, String> baselineEntries() {
    Map<String, String> entries = new LinkedHashMap<String, String>();
    entries.put("META-INF/", null);
    entries.put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nMain-Class: sample.Old\n");
    entries.put("BOOT-INF/", null);
    entries.put("BOOT-INF/classes/", null);
    entries.put("BOOT-INF/classes/sample/App.class", "old-app");
    entries.put("BOOT-INF/lib/dependency.jar", "dep-old");
    entries.put("application.properties", "mode=old\n");
    entries.put("removed.txt", "remove-me\n");
    return entries;
  }

  private Map<String, String> targetEntries() {
    Map<String, String> entries = new LinkedHashMap<String, String>();
    entries.put("META-INF/", null);
    entries.put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nMain-Class: sample.New\n");
    entries.put("BOOT-INF/", null);
    entries.put("BOOT-INF/classes/", null);
    entries.put("BOOT-INF/classes/sample/App.class", "new-app");
    entries.put("BOOT-INF/lib/dependency.jar", "dep-new");
    entries.put("application.properties", "mode=new\n");
    entries.put("added.txt", "add-me\n");
    return entries;
  }

  private Map<String, String> classTreeEntries(String appContent) {
    Map<String, String> entries = new LinkedHashMap<String, String>();
    entries.put("META-INF/", null);
    entries.put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nMain-Class: sample.App\n");
    entries.put("BOOT-INF/", null);
    entries.put("BOOT-INF/classes/", null);
    entries.put("BOOT-INF/classes/com/", null);
    entries.put("BOOT-INF/classes/com/example/", null);
    entries.put("BOOT-INF/classes/com/example/App.class", appContent);
    return entries;
  }

  private void writeJar(Path jarFile, Map<String, String> entries) throws IOException {
    writeArchive(jarFile, entries, null, Collections.singleton("BOOT-INF/lib/dependency.jar"));
  }

  private Map<String, String> warEntries(String marker) {
    Map<String, String> entries = new LinkedHashMap<String, String>();
    entries.put("META-INF/", null);
    entries.put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nMain-Class: sample.War\n");
    entries.put("WEB-INF/", null);
    entries.put("WEB-INF/classes/", null);
    entries.put("WEB-INF/classes/sample/App.class", marker + "-war-app");
    entries.put("WEB-INF/lib/dependency.jar", "dep");
    entries.put("application.properties", "mode=" + marker + "\n");
    return entries;
  }

  private void writeArchive(Path jarFile, Map<String, String> entries, String preamble)
      throws IOException {
    writeArchive(jarFile, entries, preamble, Collections.<String>emptySet());
  }

  private void writeArchive(
      Path jarFile, Map<String, String> entries, String preamble, Set<String> storedEntries)
      throws IOException {
    if (preamble != null) {
      Files.write(jarFile, preamble.getBytes(StandardCharsets.UTF_8));
    }
    try (ZipOutputStream outputStream =
        new ZipOutputStream(
            Files.newOutputStream(jarFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
      for (Map.Entry<String, String> entry : entries.entrySet()) {
        byte[] bytes =
            entry.getValue() == null
                ? new byte[0]
                : entry.getValue().getBytes(StandardCharsets.UTF_8);
        ZipEntry zipEntry = new ZipEntry(entry.getKey());
        if (storedEntries.contains(entry.getKey())) {
          CRC32 crc32 = new CRC32();
          crc32.update(bytes);
          zipEntry.setMethod(ZipEntry.STORED);
          zipEntry.setSize(bytes.length);
          zipEntry.setCompressedSize(bytes.length);
          zipEntry.setCrc(crc32.getValue());
        }
        outputStream.putNextEntry(zipEntry);
        if (bytes.length > 0) {
          outputStream.write(bytes);
        }
        outputStream.closeEntry();
      }
    }
  }

  private void assertStored(Path jarFile, String entryName) throws IOException {
    try (ZipFile zipFile = new ZipFile(jarFile.toFile())) {
      ZipEntry zipEntry = zipFile.getEntry(entryName);
      assertNotNull(zipEntry, entryName + " should exist");
      assertEquals(ZipEntry.STORED, zipEntry.getMethod(), entryName + " should stay STORED");
    }
  }

  private void assertContains(List<String> messages, String expectedFragment) {
    for (String message : messages) {
      if (message.contains(expectedFragment)) {
        return;
      }
    }
    throw new AssertionError("Missing log fragment: " + expectedFragment + " in " + messages);
  }

  private void writeTemplateJar(Path templateJar) throws IOException {
    try (ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(templateJar))) {
      outputStream.putNextEntry(new ZipEntry("META-INF/"));
      outputStream.closeEntry();
      outputStream.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
      outputStream.write(
          "Manifest-Version: 1.0\nMain-Class: sample.Template\n".getBytes(StandardCharsets.UTF_8));
      outputStream.closeEntry();
      outputStream.putNextEntry(new ZipEntry("sample/Placeholder.class"));
      outputStream.write("placeholder".getBytes(StandardCharsets.UTF_8));
      outputStream.closeEntry();
    }
  }
}
