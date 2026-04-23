package io.github.artern.fatjar.differ.core;

import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Snapshot of one logical replace-as-a-whole area such as {@code META-INF/} or {@code
 * BOOT-INF/classes/}.
 */
public final class LogicalUnitSnapshot {

  private final LogicalArea logicalArea;
  private final SortedMap<String, JarEntrySnapshot> entries;
  private final String crcSumHex;
  private final String fingerprint;

  public LogicalUnitSnapshot(LogicalArea logicalArea, Map<String, JarEntrySnapshot> sourceEntries) {
    this.logicalArea = logicalArea;
    this.entries =
        Collections.unmodifiableSortedMap(new TreeMap<String, JarEntrySnapshot>(sourceEntries));
    this.crcSumHex = computeCrcSum(entries);
    this.fingerprint = computeFingerprint(entries);
  }

  public LogicalArea getLogicalArea() {
    return logicalArea;
  }

  public SortedMap<String, JarEntrySnapshot> getEntries() {
    return entries;
  }

  public boolean isEmpty() {
    return entries.isEmpty();
  }

  public String getCrcSumHex() {
    return crcSumHex;
  }

  public String getFingerprint() {
    return fingerprint;
  }

  private static String computeCrcSum(SortedMap<String, JarEntrySnapshot> entries) {
    long sum = 0L;
    for (JarEntrySnapshot entry : entries.values()) {
      sum += entry.getCrc32();
    }
    return Long.toUnsignedString(sum, 16);
  }

  private static String computeFingerprint(SortedMap<String, JarEntrySnapshot> entries) {
    // Fingerprints use stable sorted metadata so two scans of the same archive
    // produce the same logical-area identity regardless of traversal order.
    StringBuilder builder = new StringBuilder();
    for (JarEntrySnapshot entry : entries.values()) {
      builder
          .append(entry.getPath())
          .append('|')
          .append(entry.getCrc32())
          .append('|')
          .append(entry.getSize())
          .append('|')
          .append(entry.isDirectory())
          .append('|')
          .append(entry.getMethod())
          .append('\n');
    }
    return HashingSupport.sha256Hex(builder.toString());
  }
}
