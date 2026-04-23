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

  public static final String PATCH_ROOT = "BOOT-PATCH/";
  public static final String PAYLOAD_ROOT = PATCH_ROOT + "payload/content/";
  public static final String PREAMBLE_PAYLOAD = PATCH_ROOT + "payload/archive-preamble.bin";
  public static final String PATCH_PROPERTIES = PATCH_ROOT + "patch.properties";
  public static final String OPERATIONS_INDEX = PATCH_ROOT + "operations.tsv";
  public static final String TARGET_ENTRIES_INDEX = PATCH_ROOT + "target-entries.tsv";

  /** Writes all manifest files under {@value #PATCH_ROOT}. */
  public void write(ZipOutputStream zipOutputStream, PatchManifest patchManifest)
      throws IOException {
    ZipEntries.writeDirectory(zipOutputStream, PATCH_ROOT);
    ZipEntries.writeDirectory(zipOutputStream, PATCH_ROOT + "payload/");
    ZipEntries.writeDirectory(zipOutputStream, PAYLOAD_ROOT);
    ZipEntries.writeUtf8(zipOutputStream, PATCH_PROPERTIES, encodeProperties(patchManifest));
    ZipEntries.writeUtf8(
        zipOutputStream, OPERATIONS_INDEX, encodeOperations(patchManifest.getOperations()));
    ZipEntries.writeUtf8(
        zipOutputStream,
        TARGET_ENTRIES_INDEX,
        encodeTargetEntries(patchManifest.getTargetEntries()));
  }

  /** Reads the serialized manifest back from an executable patcher jar. */
  public PatchManifest read(ZipFile zipFile) throws IOException {
    PatchManifest patchManifest = new PatchManifest();
    Map<String, String> properties = readProperties(readUtf8(zipFile, PATCH_PROPERTIES));
    patchManifest.setFormatVersion(properties.get("format.version"));
    patchManifest.setToolVersion(properties.get("tool.version"));
    patchManifest.setCreatedAt(properties.get("created.at"));
    patchManifest.setBaselineFileName(properties.get("baseline.file.name"));
    patchManifest.setBaselineSha256(properties.get("baseline.sha256"));
    patchManifest.setTargetFileName(properties.get("target.file.name"));
    patchManifest.setTargetSha256(properties.get("target.sha256"));
    patchManifest.setTargetArchivePreambleSha256(properties.get("target.archive.preamble.sha256"));
    patchManifest.setTargetArchivePreambleSize(
        Integer.parseInt(properties.get("target.archive.preamble.size")));
    patchManifest.setTargetEntryCrcSumHex(properties.get("target.entry.crc.sum"));
    patchManifest.setTargetEntryCount(Integer.parseInt(properties.get("target.entry.count")));
    for (LogicalArea logicalArea : LogicalArea.values()) {
      patchManifest
          .getLogicalUnitCrcSums()
          .put(logicalArea.getId(), properties.get("unit." + logicalArea.getId() + ".crc.sum"));
      patchManifest
          .getLogicalUnitFingerprints()
          .put(logicalArea.getId(), properties.get("unit." + logicalArea.getId() + ".fingerprint"));
    }
    patchManifest.getOperations().addAll(decodeOperations(readUtf8(zipFile, OPERATIONS_INDEX)));
    patchManifest
        .getTargetEntries()
        .addAll(decodeTargetEntries(readUtf8(zipFile, TARGET_ENTRIES_INDEX)));
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
    properties.put("baseline.sha256", patchManifest.getBaselineSha256());
    properties.put("target.file.name", patchManifest.getTargetFileName());
    properties.put("target.sha256", patchManifest.getTargetSha256());
    properties.put(
        "target.archive.preamble.sha256", patchManifest.getTargetArchivePreambleSha256());
    properties.put(
        "target.archive.preamble.size",
        Integer.toString(patchManifest.getTargetArchivePreambleSize()));
    properties.put("target.entry.crc.sum", patchManifest.getTargetEntryCrcSumHex());
    properties.put("target.entry.count", Integer.toString(patchManifest.getTargetEntryCount()));
    for (LogicalArea logicalArea : LogicalArea.values()) {
      properties.put(
          "unit." + logicalArea.getId() + ".crc.sum",
          patchManifest.getLogicalUnitCrcSums().get(logicalArea.getId()));
      properties.put(
          "unit." + logicalArea.getId() + ".fingerprint",
          patchManifest.getLogicalUnitFingerprints().get(logicalArea.getId()));
    }
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

  private String encodeTargetEntries(List<JarEntrySnapshot> targetEntries) {
    StringBuilder builder = new StringBuilder();
    for (JarEntrySnapshot targetEntry : targetEntries) {
      builder
          .append(targetEntry.getPath())
          .append('\t')
          .append(targetEntry.getCrc32())
          .append('\t')
          .append(targetEntry.getSize())
          .append('\t')
          .append(targetEntry.isDirectory())
          .append('\t')
          .append(targetEntry.getMethod())
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

  private List<JarEntrySnapshot> decodeTargetEntries(String content) {
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
