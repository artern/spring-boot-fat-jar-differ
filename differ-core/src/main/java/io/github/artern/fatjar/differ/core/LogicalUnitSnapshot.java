package io.github.artern.fatjar.differ.core;

import java.util.Collections;
import java.util.Iterator;
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

  public LogicalUnitSnapshot(LogicalArea logicalArea, Map<String, JarEntrySnapshot> sourceEntries) {
    this.logicalArea = logicalArea;
    this.entries =
        Collections.unmodifiableSortedMap(new TreeMap<String, JarEntrySnapshot>(sourceEntries));
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

  /**
   * Compares two logical units entry-by-entry and stops at the first difference because any single
   * mismatch already means the whole logical tree must be replaced.
   *
   * @param other logical unit to compare
   * @return true when both units are structurally equivalent
   */
  public boolean structurallyEquals(LogicalUnitSnapshot other) {
    if (other == null
        || logicalArea != other.logicalArea
        || entries.size() != other.entries.size()) {
      return false;
    }
    Iterator<Map.Entry<String, JarEntrySnapshot>> leftIterator = entries.entrySet().iterator();
    Iterator<Map.Entry<String, JarEntrySnapshot>> rightIterator =
        other.entries.entrySet().iterator();
    while (leftIterator.hasNext() && rightIterator.hasNext()) {
      if (!leftIterator.next().getValue().sameContent(rightIterator.next().getValue())) {
        return false;
      }
    }
    return !leftIterator.hasNext() && !rightIterator.hasNext();
  }
}
