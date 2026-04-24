package io.github.artern.fatjar.differ.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JarDiffEngineTest {

  @TempDir Path tempDir;

  @Test
  void createsPatchOperationsWithRequiredGranularity() throws Exception {
    Path baselineJar = tempDir.resolve("baseline.jar");
    Path targetJar = tempDir.resolve("target.jar");
    writeJar(baselineJar, baselineEntries());
    writeJar(targetJar, targetEntries());

    JarDiffPlan plan = new JarDiffEngine().createPlan(baselineJar, targetJar, "test-version");
    List<PatchOperation> operations = plan.getOperations();

    assertEquals(5, operations.size());
    assertTrue(hasOperation(operations, PatchOperation.Type.REPLACE_TREE, "META-INF/"));
    assertTrue(hasOperation(operations, PatchOperation.Type.REPLACE_TREE, "BOOT-INF/classes/"));
    assertTrue(
        hasOperation(operations, PatchOperation.Type.REPLACE_ENTRY, "application.properties"));
    assertTrue(hasOperation(operations, PatchOperation.Type.DELETE_ENTRY, "removed.txt"));
    assertTrue(hasOperation(operations, PatchOperation.Type.ADD_ENTRY, "added.txt"));
    assertEquals(targetEntries().size(), plan.getPatchManifest().getTargetEntryCount());
  }

  @Test
  void createsPatchOperationsForExecutableWarAndTracksLaunchScript() throws Exception {
    Path baselineWar = tempDir.resolve("baseline.war");
    Path targetWar = tempDir.resolve("target.war");
    writeArchive(baselineWar, warEntries("old"), "#!/bin/sh\necho old\n");
    writeArchive(targetWar, warEntries("new"), "#!/bin/sh\necho new\n");

    JarDiffPlan plan = new JarDiffEngine().createPlan(baselineWar, targetWar, "test-version");
    List<PatchOperation> operations = plan.getOperations();

    assertTrue(hasOperation(operations, PatchOperation.Type.REPLACE_TREE, "WEB-INF/classes/"));
    assertEquals(
        "#!/bin/sh\necho new\n".getBytes(StandardCharsets.UTF_8).length,
        plan.getPatchManifest().getTargetArchivePreambleSize());
    assertArrayEquals(
        "#!/bin/sh\necho new\n".getBytes(StandardCharsets.UTF_8),
        plan.getTargetSnapshot().getArchivePreamble().getBytes());
  }

  @Test
  void createsReplaceEntryWhenCompressionMethodChanges() throws Exception {
    Path baselineJar = tempDir.resolve("baseline-method.jar");
    Path targetJar = tempDir.resolve("target-method.jar");

    writeArchive(baselineJar, baselineEntries(), null, Collections.<String>emptySet());
    writeArchive(
        targetJar, targetEntries(), null, Collections.singleton("BOOT-INF/lib/dependency.jar"));

    JarDiffPlan plan = new JarDiffEngine().createPlan(baselineJar, targetJar, "test-version");
    java.util.List<String> reportLines = new JarDiffReportFormatter().format(plan);

    assertTrue(
        hasOperation(
            plan.getOperations(),
            PatchOperation.Type.REPLACE_ENTRY,
            "BOOT-INF/lib/dependency.jar"));
    assertTrue(
        containsFragment(reportLines, "BOOT-INF/lib/dependency.jar")
            && containsFragment(reportLines, "DEFLATED -> STORED"));
  }

  private boolean containsFragment(List<String> reportLines, String fragment) {
    for (String reportLine : reportLines) {
      if (reportLine.contains(fragment)) {
        return true;
      }
    }
    return false;
  }

  private boolean hasOperation(
      List<PatchOperation> operations, PatchOperation.Type type, String path) {
    for (PatchOperation operation : operations) {
      if (operation.getType() == type && path.equals(operation.getTargetPath())) {
        return true;
      }
    }
    return false;
  }

  private Map<String, String> baselineEntries() {
    Map<String, String> entries = new LinkedHashMap<String, String>();
    entries.put("META-INF/", null);
    entries.put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nMain-Class: sample.Old\n");
    entries.put("BOOT-INF/", null);
    entries.put("BOOT-INF/classes/", null);
    entries.put("BOOT-INF/classes/sample/App.class", "old-app");
    entries.put("BOOT-INF/lib/dependency.jar", "dep");
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
    entries.put("BOOT-INF/lib/dependency.jar", "dep");
    entries.put("application.properties", "mode=new\n");
    entries.put("added.txt", "add-me\n");
    return entries;
  }

  private void writeJar(Path jarFile, Map<String, String> entries) throws IOException {
    writeArchive(jarFile, entries, null);
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
            java.nio.file.Files.newOutputStream(
                jarFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
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
}
