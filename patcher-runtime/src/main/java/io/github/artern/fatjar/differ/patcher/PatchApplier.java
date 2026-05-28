package io.github.artern.fatjar.differ.patcher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import io.github.artern.fatjar.differ.core.PatchEntryMutability;
import io.github.artern.fatjar.differ.core.PatchManifest;
import io.github.artern.fatjar.differ.core.PatchMetadataIO;
import io.github.artern.fatjar.differ.core.PatchOperation;
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
          Set<String> writtenEntries = new LinkedHashSet<String>();
          int unchangedEntries =
              copyUnchangedEntries(currentZip, patchManifest, outputZip, writtenEntries);
          log("Copied unchanged entries: " + unchangedEntries);
          int appliedEntries =
              copyPayloadEntries(patchZip, patchManifest, outputZip, writtenEntries);
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

  private int copyUnchangedEntries(
      ZipFile currentZip,
      PatchManifest patchManifest,
      ZipOutputStream outputZip,
      Set<String> writtenEntries)
      throws IOException {
    int copied = 0;
    Enumeration<? extends ZipEntry> entries = currentZip.entries();
    while (entries.hasMoreElements()) {
      ZipEntry zipEntry = entries.nextElement();
      if (PatchEntryMutability.isMutable(zipEntry.getName(), patchManifest.getOperations())) {
        continue;
      }
      if (writtenEntries.add(zipEntry.getName())) {
        copyEntry(currentZip, zipEntry, outputZip, zipEntry.getName());
        copied++;
      }
    }
    return copied;
  }

  private int copyPayloadEntries(
      ZipFile patchZip,
      PatchManifest patchManifest,
      ZipOutputStream outputZip,
      Set<String> writtenEntries)
      throws IOException {
    int applied = 0;
    for (PatchOperation operation : patchManifest.getOperations()) {
      if (!operation.requiresPayload()) {
        log("[" + operation.getType().name() + "] " + operation.getTargetPath());
        continue;
      }
      // Tree replacements replay every nested entry from the payload; file
      // operations only need their direct payload entry.
      if (operation.getType() == PatchOperation.Type.REPLACE_TREE) {
        log("[REPLACE_TREE] " + operation.getTargetPath());
        applied += copyPayloadTree(patchZip, operation.getTargetPath(), outputZip, writtenEntries);
      } else {
        applied += copyPayloadEntry(patchZip, operation, outputZip, writtenEntries);
      }
    }
    return applied;
  }

  private int copyPayloadTree(
      ZipFile patchZip, String targetPrefix, ZipOutputStream outputZip, Set<String> writtenEntries)
      throws IOException {
    String payloadPrefix = PatchMetadataIO.PAYLOAD_ROOT + targetPrefix;
    int applied = 0;
    Set<String> names = new TreeSet<String>();
    Enumeration<? extends ZipEntry> entries = patchZip.entries();
    while (entries.hasMoreElements()) {
      names.add(entries.nextElement().getName());
    }
    for (String name : names) {
      if (!name.startsWith(payloadPrefix)) {
        continue;
      }
      String targetName = name.substring(PatchMetadataIO.PAYLOAD_ROOT.length());
      if (writtenEntries.add(targetName)) {
        ZipEntry payloadEntry = patchZip.getEntry(name);
        copyEntry(patchZip, payloadEntry, outputZip, targetName);
        log(
            "  [WRITE] "
                + targetName
                + " (method="
                + compressionMethod(payloadEntry.getMethod())
                + ")");
        applied++;
      }
    }
    return applied;
  }

  private int copyPayloadEntry(
      ZipFile patchZip,
      PatchOperation operation,
      ZipOutputStream outputZip,
      Set<String> writtenEntries)
      throws IOException {
    String payloadName = PatchMetadataIO.PAYLOAD_ROOT + operation.getTargetPath();
    if (!writtenEntries.add(operation.getTargetPath())) {
      return 0;
    }
    ZipEntry payloadEntry = patchZip.getEntry(payloadName);
    if (payloadEntry == null) {
      if (operation.isDirectory()) {
        java.util.zip.ZipEntry zipEntry =
            new java.util.zip.ZipEntry(normalizeDirectory(operation.getTargetPath()));
        outputZip.putNextEntry(zipEntry);
        outputZip.closeEntry();
        log("[" + operation.getType().name() + "] " + operation.getTargetPath() + " (directory)");
        return 1;
      }
      throw new IOException("Missing payload entry in patch bundle: " + payloadName);
    }
    copyEntry(patchZip, payloadEntry, outputZip, operation.getTargetPath());
    log(
        "["
            + operation.getType().name()
            + "] "
            + operation.getTargetPath()
            + " (method="
            + compressionMethod(payloadEntry.getMethod())
            + ")");
    return 1;
  }

  private String normalizeDirectory(String targetPath) {
    return targetPath.endsWith("/") ? targetPath : targetPath + "/";
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
}
