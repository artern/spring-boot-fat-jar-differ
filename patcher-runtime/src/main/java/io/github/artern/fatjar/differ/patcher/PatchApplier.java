package io.github.artern.fatjar.differ.patcher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import io.github.artern.fatjar.differ.core.JarEntrySnapshot;
import io.github.artern.fatjar.differ.core.PatchManifest;
import io.github.artern.fatjar.differ.core.PatchMetadataIO;
import io.github.artern.fatjar.differ.core.TargetJarValidator;
import io.github.artern.fatjar.differ.core.ZipEntries;

/**
 * Rebuilds the target archive by applying the serialized patch bundle to the currently installed
 * baseline archive.
 */
public final class PatchApplier {

  @FunctionalInterface
  public interface ProgressLogger {
    void log(String message);
  }

  private static final ProgressLogger NO_OP_LOGGER =
      new ProgressLogger() {
        @Override
        public void log(String message) {}
      };

  private final PatchMetadataIO patchMetadataIO = new PatchMetadataIO();
  private final TargetJarValidator targetJarValidator = new TargetJarValidator();
  private final ProgressLogger progressLogger;

  public PatchApplier() {
    this(NO_OP_LOGGER);
  }

  public PatchApplier(ProgressLogger progressLogger) {
    this.progressLogger = progressLogger == null ? NO_OP_LOGGER : progressLogger;
  }

  /**
   * Applies the executable patch bundle and writes the reconstructed archive to {@code outputJar}.
   */
  public PatchManifest apply(Path currentJar, Path patchBundleJar, Path outputJar)
      throws IOException {
    try (ZipFile patchZip = new ZipFile(patchBundleJar.toFile())) {
      PatchManifest patchManifest = patchMetadataIO.read(patchZip);
      log(
          String.format(
              "Loaded patch manifest: baseline=%s, target=%s, operations=%d",
              patchManifest.getBaselineFileName(),
              patchManifest.getTargetFileName(),
              patchManifest.getOperations().size()));
      log("Validating current archive against baseline entry metadata...");
      targetJarValidator.validateBaseline(currentJar, patchManifest);
      log("Baseline validation passed.");
      Files.deleteIfExists(outputJar);
      byte[] preamble = readArchivePreamble(patchZip, patchManifest);
      Path zipPayload =
          Files.createTempFile(
              outputJar.getParent(), outputJar.getFileName().toString() + ".", ".zip");
      try {
        try (ZipFile currentZip = new ZipFile(currentJar.toFile());
            OutputStream payloadStream = Files.newOutputStream(zipPayload);
            ZipOutputStream outputZip = new ZipOutputStream(payloadStream)) {
          WriteResult result =
              writeEntriesInTargetOrder(currentZip, patchZip, patchManifest, outputZip);
          int unchangedEntries = result.getCopiedFromCurrent();
          log("Copied unchanged entries: " + unchangedEntries);
          int appliedEntries = result.getCopiedFromPayload();
          log("Applied payload entries: " + appliedEntries);
        }
        log("Restoring archive preamble: " + preamble.length + " byte(s)");
        ExecutableArchiveSupport.writeArchive(zipPayload, outputJar, preamble);
        ExecutableArchiveSupport.copyFilePermissions(currentJar, outputJar);
      } finally {
        Files.deleteIfExists(zipPayload);
      }
      log("Validating reconstructed archive against target metadata...");
      targetJarValidator.validateTarget(outputJar, patchManifest, preamble);
      log("Target validation passed.");
      return patchManifest;
    }
  }

  private byte[] readArchivePreamble(ZipFile patchZip, PatchManifest patchManifest)
      throws IOException {
    if (patchManifest.getTargetArchivePreambleSize() == 0) {
      return new byte[0];
    }
    ZipEntry payloadEntry = patchZip.getEntry(PatchMetadataIO.PREAMBLE_PAYLOAD);
    if (payloadEntry == null) {
      throw new IOException("Missing archive preamble payload in patch bundle");
    }
    byte[] buffer = new byte[8192];
    java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
    try (InputStream inputStream = patchZip.getInputStream(payloadEntry)) {
      for (; ; ) {
        int read = inputStream.read(buffer);
        if (read < 0) {
          break;
        }
        outputStream.write(buffer, 0, read);
      }
    }
    return outputStream.toByteArray();
  }

  private WriteResult writeEntriesInTargetOrder(
      ZipFile currentZip, ZipFile patchZip, PatchManifest patchManifest, ZipOutputStream outputZip)
      throws IOException {
    Set<String> writtenEntries = new LinkedHashSet<String>();
    Map<String, ZipEntry> payloadEntries = payloadEntriesByTargetPath(patchZip);
    int copiedFromCurrent = 0;
    int copiedFromPayload = 0;
    for (JarEntrySnapshot targetEntry : patchManifest.getTargetEntries()) {
      String targetPath = targetEntry.getPath();
      if (!writtenEntries.add(targetPath)) {
        continue;
      }
      ZipEntry payloadEntry = payloadEntries.get(targetPath);
      if (payloadEntry != null) {
        copyEntry(patchZip, payloadEntry, outputZip, targetPath);
        copiedFromPayload++;
        continue;
      }
      ZipEntry currentEntry = currentZip.getEntry(targetPath);
      if (currentEntry == null) {
        throw new IOException("Missing entry while rebuilding target archive: " + targetPath);
      }
      copyEntry(currentZip, currentEntry, outputZip, targetPath);
      copiedFromCurrent++;
    }
    return new WriteResult(copiedFromCurrent, copiedFromPayload);
  }

  private Map<String, ZipEntry> payloadEntriesByTargetPath(ZipFile patchZip) {
    Map<String, ZipEntry> payloadEntries = new LinkedHashMap<String, ZipEntry>();
    Enumeration<? extends ZipEntry> entries = patchZip.entries();
    while (entries.hasMoreElements()) {
      ZipEntry entry = entries.nextElement();
      String name = entry.getName();
      if (!name.startsWith(PatchMetadataIO.PAYLOAD_ROOT)
          || PatchMetadataIO.PREAMBLE_PAYLOAD.equals(name)) {
        continue;
      }
      String targetName = name.substring(PatchMetadataIO.PAYLOAD_ROOT.length());
      payloadEntries.put(targetName, entry);
    }
    return payloadEntries;
  }

  private void copyEntry(
      ZipFile sourceZip, ZipEntry sourceEntry, ZipOutputStream outputZip, String targetName)
      throws IOException {
    java.util.zip.ZipEntry zipEntry = ZipEntries.copyMetadata(sourceEntry, targetName);
    outputZip.putNextEntry(zipEntry);
    if (!sourceEntry.isDirectory()) {
      byte[] buffer = new byte[8192];
      java.io.InputStream inputStream = sourceZip.getInputStream(sourceEntry);
      try {
        for (; ; ) {
          int read = inputStream.read(buffer);
          if (read < 0) {
            break;
          }
          outputZip.write(buffer, 0, read);
        }
      } finally {
        inputStream.close();
      }
    }
    outputZip.closeEntry();
  }

  private void log(String message) {
    progressLogger.log(message);
  }

  private String compressionMethod(int method) {
    if (method == ZipEntry.STORED) {
      return "STORED";
    }
    if (method == ZipEntry.DEFLATED) {
      return "DEFLATED";
    }
    if (method < 0) {
      return "UNKNOWN";
    }
    return Integer.toString(method);
  }

  private static final class WriteResult {
    private final int copiedFromCurrent;
    private final int copiedFromPayload;

    private WriteResult(int copiedFromCurrent, int copiedFromPayload) {
      this.copiedFromCurrent = copiedFromCurrent;
      this.copiedFromPayload = copiedFromPayload;
    }

    private int getCopiedFromCurrent() {
      return copiedFromCurrent;
    }

    private int getCopiedFromPayload() {
      return copiedFromPayload;
    }
  }
}
