package io.github.artern.fatjar.differ.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Serializes the patch manifest into the executable patcher jar and restores it at patch time. */
public final class PatchMetadataIO {

  private static final String SUPPORTED_FORMAT_VERSION = "2";

  public static final String PATCH_ROOT = "BOOT-PATCH/";
  public static final String PAYLOAD_ROOT = PATCH_ROOT + "payload/content/";
  public static final String PREAMBLE_PAYLOAD = PATCH_ROOT + "payload/archive-preamble.bin";
  public static final String PATCH_PROPERTIES = PATCH_ROOT + "patch.properties";
  public static final String OPERATIONS_INDEX = PATCH_ROOT + "operations.tsv";
  public static final String BASELINE_ENTRIES_INDEX = PATCH_ROOT + "baseline-entries.tsv";
  public static final String TARGET_ENTRIES_INDEX = PATCH_ROOT + "target-entries.tsv";

  /**
   * Writes all manifest files under {@value #PATCH_ROOT}.
   *
   * @param zipOutputStream output archive stream
   * @param patchManifest patch manifest to serialize
   * @throws IOException when writing metadata entries fails
   */
  public void write(ZipOutputStream zipOutputStream, PatchManifest patchManifest)
      throws IOException {
    ZipEntries.writeDirectory(zipOutputStream, PATCH_ROOT);
    ZipEntries.writeDirectory(zipOutputStream, PATCH_ROOT + "payload/");
    ZipEntries.writeDirectory(zipOutputStream, PAYLOAD_ROOT);
    ZipEntries.writeUtf8(zipOutputStream, PATCH_PROPERTIES, encodeProperties(patchManifest));
    ZipEntries.writeUtf8(
        zipOutputStream, OPERATIONS_INDEX, encodeOperations(patchManifest.getOperations()));
    ZipEntries.writeUtf8(
        zipOutputStream, BASELINE_ENTRIES_INDEX, encodeEntries(patchManifest.getBaselineEntries()));
    ZipEntries.writeUtf8(
        zipOutputStream, TARGET_ENTRIES_INDEX, encodeEntries(patchManifest.getTargetEntries()));
  }

  /**
   * Reads the serialized manifest back from an executable patcher jar.
   *
   * @param zipFile patcher archive file
   * @return deserialized patch manifest
   * @throws IOException when required metadata entries are missing or malformed
   */
  public PatchManifest read(ZipFile zipFile) throws IOException {
    PatchManifest patchManifest = new PatchManifest();
    Map<String, String> properties = readProperties(readUtf8(zipFile, PATCH_PROPERTIES));
    String formatVersion = properties.get("format.version");
    if (!SUPPORTED_FORMAT_VERSION.equals(formatVersion)) {
      throw new IOException(
          "Unsupported patch format version: "
              + formatVersion
              + ". Regenerate the patcher with the current toolchain.");
    }
    patchManifest.setFormatVersion(formatVersion);
    patchManifest.setToolVersion(properties.get("tool.version"));
    patchManifest.setCreatedAt(properties.get("created.at"));
    patchManifest.setBaselineFileName(properties.get("baseline.file.name"));
    patchManifest.setBaselineEntryCrcSumHex(properties.get("baseline.entry.crc.sum"));
    String baselineEntryCount = properties.get("baseline.entry.count");
    if (baselineEntryCount == null || baselineEntryCount.isEmpty()) {
      throw new IOException("Missing baseline entry count in patch metadata");
    }
    patchManifest.setBaselineEntryCount(Integer.parseInt(baselineEntryCount));
    patchManifest.setTargetFileName(properties.get("target.file.name"));
    patchManifest.setTargetArchivePreambleSize(
        Integer.parseInt(properties.get("target.archive.preamble.size")));
    patchManifest.setTargetEntryCrcSumHex(properties.get("target.entry.crc.sum"));
    patchManifest.setTargetEntryCount(Integer.parseInt(properties.get("target.entry.count")));
    patchManifest.getOperations().addAll(decodeOperations(readUtf8(zipFile, OPERATIONS_INDEX)));
    patchManifest
        .getBaselineEntries()
        .addAll(decodeEntries(readUtf8(zipFile, BASELINE_ENTRIES_INDEX)));
    patchManifest.getTargetEntries().addAll(decodeEntries(readUtf8(zipFile, TARGET_ENTRIES_INDEX)));
    return patchManifest;
  }

  private String encodeProperties(PatchManifest patchManifest) {
    // Properties keep the on-disk format easy to inspect with standard zip
    // tooling and stable across Java 8 runtimes.
    Map<String, String> properties = new LinkedHashMap<String, String>();
    properties.put("format.version", patchManifest.getFormatVersion());
    properties.put("tool.version", patchManifest.getToolVersion());
    properties.put("created.at", patchManifest.getCreatedAt());
    properties.put("baseline.file.name", patchManifest.getBaselineFileName());
    properties.put("baseline.entry.crc.sum", patchManifest.getBaselineEntryCrcSumHex());
    properties.put("baseline.entry.count", Integer.toString(patchManifest.getBaselineEntryCount()));
    properties.put("target.file.name", patchManifest.getTargetFileName());
    properties.put(
        "target.archive.preamble.size",
        Integer.toString(patchManifest.getTargetArchivePreambleSize()));
    properties.put("target.entry.crc.sum", patchManifest.getTargetEntryCrcSumHex());
    properties.put("target.entry.count", Integer.toString(patchManifest.getTargetEntryCount()));
    StringBuilder builder = new StringBuilder();
    for (Map.Entry<String, String> entry : properties.entrySet()) {
      builder
          .append(entry.getKey())
          .append('=')
          .append(entry.getValue() == null ? "" : entry.getValue())
          .append('\n');
    }
    return builder.toString();
  }

  private String encodeOperations(List<PatchOperation> operations) {
    StringBuilder builder = new StringBuilder();
    for (PatchOperation operation : operations) {
      builder
          .append(operation.getType().name())
          .append('\t')
          .append(operation.getTargetPath())
          .append('\t')
          .append(operation.isDirectory())
          .append('\n');
    }
    return builder.toString();
  }

  private String encodeEntries(List<JarEntrySnapshot> entries) {
    StringBuilder builder = new StringBuilder();
    for (JarEntrySnapshot entry : entries) {
      builder
          .append(entry.getPath())
          .append('\t')
          .append(entry.getCrc32())
          .append('\t')
          .append(entry.getSize())
          .append('\t')
          .append(entry.isDirectory())
          .append('\t')
          .append(entry.getMethod())
          .append('\n');
    }
    return builder.toString();
  }

  private Map<String, String> readProperties(String content) {
    Map<String, String> properties = new LinkedHashMap<String, String>();
    String[] lines = content.split("\\r?\\n");
    for (String line : lines) {
      if (line.trim().isEmpty()) {
        continue;
      }
      int separator = line.indexOf('=');
      if (separator < 0) {
        continue;
      }
      properties.put(line.substring(0, separator), line.substring(separator + 1));
    }
    return properties;
  }

  private List<PatchOperation> decodeOperations(String content) {
    List<PatchOperation> operations = new ArrayList<PatchOperation>();
    String[] lines = content.split("\\r?\\n");
    for (String line : lines) {
      if (line.trim().isEmpty()) {
        continue;
      }
      String[] parts = line.split("\\t", 3);
      PatchOperation operation = new PatchOperation();
      operation.setType(PatchOperation.Type.valueOf(parts[0]));
      operation.setTargetPath(parts[1]);
      operation.setDirectory(Boolean.parseBoolean(parts[2]));
      operations.add(operation);
    }
    return operations;
  }

  private List<JarEntrySnapshot> decodeEntries(String content) {
    List<JarEntrySnapshot> entries = new ArrayList<JarEntrySnapshot>();
    String[] lines = content.split("\\r?\\n");
    for (String line : lines) {
      if (line.trim().isEmpty()) {
        continue;
      }
      String[] parts = line.split("\\t", 5);
      entries.add(
          new JarEntrySnapshot(
              parts[0],
              Long.parseLong(parts[1]),
              Long.parseLong(parts[2]),
              Boolean.parseBoolean(parts[3]),
              parts.length >= 5 ? Integer.parseInt(parts[4]) : -1));
    }
    return entries;
  }

  private String readUtf8(ZipFile zipFile, String entryName) throws IOException {
    ZipEntry zipEntry = zipFile.getEntry(entryName);
    if (zipEntry == null) {
      throw new IOException("Missing patch metadata entry: " + entryName);
    }
    try (InputStream inputStream = zipFile.getInputStream(zipEntry);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[4096];
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
