package io.github.artern.fatjar.differ.patcher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
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
import io.github.artern.fatjar.differ.core.JarEntrySnapshot;
import io.github.artern.fatjar.differ.core.JarSnapshot;
import io.github.artern.fatjar.differ.core.PatchMetadataIO;
import io.github.artern.fatjar.differ.core.SpringBootFatJarScanner;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    assertSnapshotEntriesMatch(expected, actual);
    assertArrayEquals(
        expected.getArchivePreamble().getBytes(), actual.getArchivePreamble().getBytes());
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

    baselineWar.toFile().setExecutable(true, false);

    new PatchApplier().apply(baselineWar, patchBundle, outputWar);

    SpringBootFatJarScanner scanner = new SpringBootFatJarScanner();
    JarSnapshot actual = scanner.scan(outputWar);
    JarSnapshot expected = scanner.scan(targetWar);
    assertSnapshotEntriesMatch(expected, actual);
    assertArrayEquals(
        expected.getArchivePreamble().getBytes(), actual.getArchivePreamble().getBytes());
    assertCentralDirectoryOffsetAligned(targetWar);
    assertCentralDirectoryOffsetAligned(outputWar);
    assertEquals(baselineWar.toFile().canExecute(), outputWar.toFile().canExecute());
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
    assertSnapshotEntriesMatch(expected, actual);
    assertEquals(
        expected.getAllEntries().get("BOOT-INF/classes/com/").getMethod(),
        actual.getAllEntries().get("BOOT-INF/classes/com/").getMethod());
  }

  @Test
  void appliesPatchBundleAgainstStructurallyEquivalentCurrentJar() throws Exception {
    Path baselineJar = tempDir.resolve("baseline-structural.jar");
    Path currentJar = tempDir.resolve("current-structural.jar");
    Path targetJar = tempDir.resolve("target-structural.jar");
    Path templateJar = tempDir.resolve("template.jar");
    Path patchBundle = tempDir.resolve("spring-boot-fat-jar-patcher.jar");
    Path outputJar = tempDir.resolve("output-structural.jar");

    writeJar(baselineJar, baselineEntries());
    writeArchiveWithMetadata(
        currentJar,
        baselineEntries(),
        null,
        Collections.singleton("BOOT-INF/lib/dependency.jar"),
        1_700_000_000_000L,
        true);
    writeJar(targetJar, targetEntries());
    writeTemplateJar(templateJar);

    ExecutablePatchJarBuilder builder = new ExecutablePatchJarBuilder();
    try (InputStream templateStream = Files.newInputStream(templateJar)) {
      builder.build(baselineJar, targetJar, templateStream, patchBundle, "test-version");
    }

    new PatchApplier().apply(currentJar, patchBundle, outputJar);

    SpringBootFatJarScanner scanner = new SpringBootFatJarScanner();
    JarSnapshot actual = scanner.scan(outputJar);
    JarSnapshot expected = scanner.scan(targetJar);
    assertSnapshotEntriesMatch(expected, actual);
  }

  @Test
  void appliesPatchBundleWhenCurrentJarDiffersOnlyOnMutablePaths() throws Exception {
    Path baselineJar = tempDir.resolve("baseline-mutable.jar");
    Path currentJar = tempDir.resolve("current-mutable.jar");
    Path targetJar = tempDir.resolve("target-mutable.jar");
    Path templateJar = tempDir.resolve("template.jar");
    Path patchBundle = tempDir.resolve("spring-boot-fat-jar-patcher.jar");
    Path outputJar = tempDir.resolve("output-mutable.jar");

    writeJar(baselineJar, baselineEntries());
    writeJar(currentJar, currentMutableEntries());
    writeJar(targetJar, targetEntries());
    writeTemplateJar(templateJar);

    ExecutablePatchJarBuilder builder = new ExecutablePatchJarBuilder();
    try (InputStream templateStream = Files.newInputStream(templateJar)) {
      builder.build(baselineJar, targetJar, templateStream, patchBundle, "test-version");
    }

    new PatchApplier().apply(currentJar, patchBundle, outputJar);

    SpringBootFatJarScanner scanner = new SpringBootFatJarScanner();
    JarSnapshot actual = scanner.scan(outputJar);
    JarSnapshot expected = scanner.scan(targetJar);
    assertSnapshotEntriesMatch(expected, actual);
  }

  @Test
  void rejectsCurrentJarWithExtraImmutableEntry() throws Exception {
    Path baselineJar = tempDir.resolve("baseline-extra.jar");
    Path currentJar = tempDir.resolve("current-extra.jar");
    Path targetJar = tempDir.resolve("target-extra.jar");
    Path templateJar = tempDir.resolve("template.jar");
    Path patchBundle = tempDir.resolve("spring-boot-fat-jar-patcher.jar");
    Path outputJar = tempDir.resolve("output-extra.jar");

    writeJar(baselineJar, baselineEntries());
    writeJar(currentJar, currentWithExtraImmutableEntries());
    writeJar(targetJar, targetEntries());
    writeTemplateJar(templateJar);

    ExecutablePatchJarBuilder builder = new ExecutablePatchJarBuilder();
    try (InputStream templateStream = Files.newInputStream(templateJar)) {
      builder.build(baselineJar, targetJar, templateStream, patchBundle, "test-version");
    }

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> new PatchApplier().apply(currentJar, patchBundle, outputJar));
    assertTrue(exception.getMessage().contains("Current jar entry count mismatch"));
  }

  @Test
  void rejectsLegacyPatchBundleWithoutBaselineEntriesMetadata() throws Exception {
    Path baselineJar = tempDir.resolve("baseline-legacy.jar");
    Path targetJar = tempDir.resolve("target-legacy.jar");
    Path templateJar = tempDir.resolve("template.jar");
    Path patchBundle = tempDir.resolve("spring-boot-fat-jar-patcher.jar");
    Path legacyPatchBundle = tempDir.resolve("spring-boot-fat-jar-patcher-legacy.jar");
    Path outputJar = tempDir.resolve("output-legacy.jar");

    writeJar(baselineJar, baselineEntries());
    writeJar(targetJar, targetEntries());
    writeTemplateJar(templateJar);

    ExecutablePatchJarBuilder builder = new ExecutablePatchJarBuilder();
    try (InputStream templateStream = Files.newInputStream(templateJar)) {
      builder.build(baselineJar, targetJar, templateStream, patchBundle, "test-version");
    }

    downgradeToLegacyPatchBundle(patchBundle, legacyPatchBundle);

    IOException exception =
        assertThrows(
            IOException.class,
            () -> new PatchApplier().apply(baselineJar, legacyPatchBundle, outputJar));
    assertTrue(
        exception
            .getMessage()
            .contains(
                "Unsupported patch format version: 1. Regenerate the patcher with the current toolchain."));
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

  private Map<String, String> currentMutableEntries() {
    Map<String, String> entries = new LinkedHashMap<String, String>();
    entries.put("META-INF/", null);
    entries.put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nMain-Class: sample.Current\n");
    entries.put("META-INF/extra.txt", "current-meta\n");
    entries.put("BOOT-INF/", null);
    entries.put("BOOT-INF/classes/", null);
    entries.put("BOOT-INF/classes/sample/App.class", "current-app");
    entries.put("BOOT-INF/classes/legacy/Old.class", "legacy-class");
    entries.put("BOOT-INF/lib/dependency.jar", "dep-current");
    entries.put("application.properties", "mode=current\n");
    entries.put("added.txt", "stale-add\n");
    return entries;
  }

  private Map<String, String> currentWithExtraImmutableEntries() {
    Map<String, String> entries = new LinkedHashMap<String, String>(baselineEntries());
    entries.put("local-only.txt", "keep-me\n");
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
    writeArchiveWithMetadata(jarFile, entries, preamble, storedEntries, -1L, false);
  }

  private void writeArchiveWithMetadata(
      Path jarFile,
      Map<String, String> entries,
      String preamble,
      Set<String> storedEntries,
      long entryTime,
      boolean reverseOrder)
      throws IOException {
    Path zipTarget =
        preamble == null
            ? jarFile
            : Files.createTempFile(tempDir, jarFile.getFileName().toString() + ".", ".zip");
    List<Map.Entry<String, String>> orderedEntries =
        new ArrayList<Map.Entry<String, String>>(entries.entrySet());
    if (reverseOrder) {
      Collections.reverse(orderedEntries);
    }
    try {
      try (ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(zipTarget))) {
        for (Map.Entry<String, String> entry : orderedEntries) {
          byte[] bytes =
              entry.getValue() == null
                  ? new byte[0]
                  : entry.getValue().getBytes(StandardCharsets.UTF_8);
          ZipEntry zipEntry = new ZipEntry(entry.getKey());
          if (entryTime >= 0) {
            zipEntry.setTime(entryTime++);
          }
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
      if (preamble != null) {
        ExecutableArchiveSupport.writeArchive(
            zipTarget, jarFile, preamble.getBytes(StandardCharsets.UTF_8));
      }
    } finally {
      if (preamble != null) {
        Files.deleteIfExists(zipTarget);
      }
    }
  }

  private void assertCentralDirectoryOffsetAligned(Path archive) throws IOException {
    byte[] bytes = Files.readAllBytes(archive);
    int endOfCentralDirectoryOffset = lastIndexOf(bytes, new byte[] {'P', 'K', 0x05, 0x06});
    assertTrue(endOfCentralDirectoryOffset >= 0, "Missing end of central directory: " + archive);
    long centralDirectorySize = readUnsignedInt(bytes, endOfCentralDirectoryOffset + 12);
    long centralDirectoryOffset = readUnsignedInt(bytes, endOfCentralDirectoryOffset + 16);
    long actualCentralDirectoryOffset = endOfCentralDirectoryOffset - centralDirectorySize;
    assertEquals(actualCentralDirectoryOffset, centralDirectoryOffset);
  }

  private int lastIndexOf(byte[] source, byte[] pattern) {
    for (int index = source.length - pattern.length; index >= 0; index--) {
      boolean matches = true;
      for (int offset = 0; offset < pattern.length; offset++) {
        if (source[index + offset] != pattern[offset]) {
          matches = false;
          break;
        }
      }
      if (matches) {
        return index;
      }
    }
    return -1;
  }

  private long readUnsignedInt(byte[] source, int offset) {
    return ((long) source[offset] & 0xffL)
        | (((long) source[offset + 1] & 0xffL) << 8)
        | (((long) source[offset + 2] & 0xffL) << 16)
        | (((long) source[offset + 3] & 0xffL) << 24);
  }

  private void assertStored(Path jarFile, String entryName) throws IOException {
    try (ZipFile zipFile = new ZipFile(jarFile.toFile())) {
      ZipEntry zipEntry = zipFile.getEntry(entryName);
      assertNotNull(zipEntry, entryName + " should exist");
      assertEquals(ZipEntry.STORED, zipEntry.getMethod(), entryName + " should stay STORED");
    }
  }

  private void assertSnapshotEntriesMatch(JarSnapshot expected, JarSnapshot actual) {
    assertEquals(expected.getAllEntries().size(), actual.getAllEntries().size());
    assertEquals(expected.getAllEntries().keySet(), actual.getAllEntries().keySet());
    for (Map.Entry<String, JarEntrySnapshot> entry : expected.getAllEntries().entrySet()) {
      JarEntrySnapshot actualEntry = actual.getAllEntries().get(entry.getKey());
      assertEquals(entry.getValue().getCrc32(), actualEntry.getCrc32());
      assertEquals(entry.getValue().getSize(), actualEntry.getSize());
      assertEquals(entry.getValue().getMethod(), actualEntry.getMethod());
      assertEquals(entry.getValue().isDirectory(), actualEntry.isDirectory());
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

  private void downgradeToLegacyPatchBundle(Path sourceJar, Path legacyJar) throws IOException {
    try (ZipFile sourceZip = new ZipFile(sourceJar.toFile());
        ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(legacyJar))) {
      Enumeration<? extends ZipEntry> entries = sourceZip.entries();
      while (entries.hasMoreElements()) {
        ZipEntry sourceEntry = entries.nextElement();
        if (PatchMetadataIO.BASELINE_ENTRIES_INDEX.equals(sourceEntry.getName())) {
          continue;
        }
        if (PatchMetadataIO.PATCH_PROPERTIES.equals(sourceEntry.getName())) {
          String content = readUtf8(sourceZip, sourceEntry);
          content = content.replace("format.version=2\n", "format.version=1\n");
          content = content.replaceAll("(?m)^baseline\\.entry\\.(crc\\.sum|count)=.*\\n", "");
          writeUtf8(outputStream, sourceEntry.getName(), content);
          continue;
        }
        copyZipEntry(sourceZip, sourceEntry, outputStream);
      }
    }
  }

  private void writeUtf8(ZipOutputStream outputStream, String entryName, String content)
      throws IOException {
    ZipEntry zipEntry = new ZipEntry(entryName);
    outputStream.putNextEntry(zipEntry);
    outputStream.write(content.getBytes(StandardCharsets.UTF_8));
    outputStream.closeEntry();
  }

  private void copyZipEntry(ZipFile sourceZip, ZipEntry sourceEntry, ZipOutputStream outputStream)
      throws IOException {
    ZipEntry zipEntry = new ZipEntry(sourceEntry.getName());
    outputStream.putNextEntry(zipEntry);
    if (!sourceEntry.isDirectory()) {
      try (InputStream inputStream = sourceZip.getInputStream(sourceEntry)) {
        byte[] buffer = new byte[4096];
        for (; ; ) {
          int read = inputStream.read(buffer);
          if (read < 0) {
            break;
          }
          outputStream.write(buffer, 0, read);
        }
      }
    }
    outputStream.closeEntry();
  }

  private String readUtf8(ZipFile zipFile, ZipEntry zipEntry) throws IOException {
    try (InputStream inputStream = zipFile.getInputStream(zipEntry)) {
      byte[] buffer = new byte[4096];
      java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
      for (; ; ) {
        int read = inputStream.read(buffer);
        if (read < 0) {
          break;
        }
        outputStream.write(buffer, 0, read);
      }
      return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
    }
  }
}
