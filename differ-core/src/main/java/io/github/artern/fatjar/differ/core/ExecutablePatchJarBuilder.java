package io.github.artern.fatjar.differ.core;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Assembles the executable patcher jar from the runtime template and the diff payload derived from
 * the target archive.
 */
public final class ExecutablePatchJarBuilder {

  private final JarDiffEngine jarDiffEngine = new JarDiffEngine();
  private final PatchMetadataIO patchMetadataIO = new PatchMetadataIO();

  /**
   * Creates an executable patch bundle that can upgrade {@code baselineJar} to {@code targetJar} in
   * place.
   */
  public JarDiffPlan build(
      Path baselineJar,
      Path targetJar,
      InputStream templateJarInputStream,
      Path outputPatchJar,
      String toolVersion)
      throws IOException {
    JarDiffPlan plan = jarDiffEngine.createPlan(baselineJar, targetJar, toolVersion);
    Files.createDirectories(outputPatchJar.toAbsolutePath().getParent());
    try (InputStream sourceStream = templateJarInputStream;
        ZipInputStream templateZip = new ZipInputStream(sourceStream);
        ZipOutputStream outputZip = new ZipOutputStream(Files.newOutputStream(outputPatchJar))) {
      Set<String> writtenEntries = new LinkedHashSet<String>();
      // The template provides the runnable patcher runtime; the custom
      // metadata and payload are layered on top of it.
      copyTemplate(templateZip, outputZip, writtenEntries);
      patchMetadataIO.write(outputZip, plan.getPatchManifest());
      copyArchivePreamble(plan.getTargetSnapshot().getArchivePreamble(), outputZip, writtenEntries);
      copyPayload(targetJar, plan.getPatchManifest(), outputZip, writtenEntries);
    }
    return plan;
  }

  private void copyArchivePreamble(
      ArchivePreamble archivePreamble, ZipOutputStream outputZip, Set<String> writtenEntries)
      throws IOException {
    if (!writtenEntries.add(PatchMetadataIO.PREAMBLE_PAYLOAD)) {
      return;
    }
    ZipEntries.writeStream(
        outputZip,
        new ZipEntry(PatchMetadataIO.PREAMBLE_PAYLOAD),
        new ByteArrayInputStream(archivePreamble.getBytes()));
  }

  private void copyTemplate(
      ZipInputStream templateZip, ZipOutputStream outputZip, Set<String> writtenEntries)
      throws IOException {
    for (; ; ) {
      ZipEntry zipEntry = templateZip.getNextEntry();
      if (zipEntry == null) {
        break;
      }
      if (!zipEntry.getName().startsWith(PatchMetadataIO.PATCH_ROOT)
          && writtenEntries.add(zipEntry.getName())) {
        ZipEntries.copyFromZipInputStream(templateZip, zipEntry, outputZip);
      }
      templateZip.closeEntry();
    }
  }

  private void copyPayload(
      Path targetJar,
      PatchManifest patchManifest,
      ZipOutputStream outputZip,
      Set<String> writtenEntries)
      throws IOException {
    try (ZipFile targetZip = new ZipFile(targetJar.toFile())) {
      for (PatchOperation operation : patchManifest.getOperations()) {
        if (!operation.requiresPayload()) {
          continue;
        }
        if (operation.getType() == PatchOperation.Type.REPLACE_TREE) {
          copyTreePayload(targetZip, operation.getTargetPath(), outputZip, writtenEntries);
        } else {
          copyEntryPayload(targetZip, operation, outputZip, writtenEntries);
        }
      }
    }
  }

  private void copyTreePayload(
      ZipFile targetZip, String prefix, ZipOutputStream outputZip, Set<String> writtenEntries)
      throws IOException {
    // Tree replacement entries are materialized one by one so the runtime can
    // rebuild the exact target structure without special directory metadata.
    for (String name : ZipEntries.listNames(targetZip)) {
      if (name.startsWith(prefix)) {
        String patchEntryName = PatchMetadataIO.PAYLOAD_ROOT + name;
        if (writtenEntries.add(patchEntryName)) {
          ZipEntry zipEntry = targetZip.getEntry(name);
          ZipEntries.copyFromZipFile(targetZip, zipEntry, outputZip, patchEntryName);
        }
      }
    }
  }

  private void copyEntryPayload(
      ZipFile targetZip,
      PatchOperation operation,
      ZipOutputStream outputZip,
      Set<String> writtenEntries)
      throws IOException {
    String patchEntryName = PatchMetadataIO.PAYLOAD_ROOT + operation.getTargetPath();
    if (!writtenEntries.add(patchEntryName)) {
      return;
    }
    ZipEntry zipEntry = targetZip.getEntry(operation.getTargetPath());
    if (zipEntry == null) {
      if (operation.isDirectory()) {
        ZipEntries.writeDirectory(outputZip, patchEntryName);
        return;
      }
      throw new IOException("Missing payload entry in target jar: " + operation.getTargetPath());
    }
    ZipEntries.copyFromZipFile(targetZip, zipEntry, outputZip, patchEntryName);
  }
}
