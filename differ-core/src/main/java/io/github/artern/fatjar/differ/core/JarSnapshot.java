package io.github.artern.fatjar.differ.core;

import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/** Immutable structural view of a scanned executable archive. */
public final class JarSnapshot {

  private final Path source;
  private final ArchivePreamble archivePreamble;
  private final SortedMap<String, JarEntrySnapshot> allEntries;
  private final SortedMap<String, JarEntrySnapshot> regularEntries;
  private final Map<LogicalArea, LogicalUnitSnapshot> logicalUnits;
  private final boolean supportedSpringBootArchive;

  public JarSnapshot(
      Path source,
      ArchivePreamble archivePreamble,
      Map<String, JarEntrySnapshot> allEntries,
      Map<String, JarEntrySnapshot> regularEntries,
      Map<LogicalArea, LogicalUnitSnapshot> logicalUnits,
      boolean supportedSpringBootArchive) {
    this.source = source;
    this.archivePreamble = archivePreamble;
    this.allEntries =
        Collections.unmodifiableSortedMap(new TreeMap<String, JarEntrySnapshot>(allEntries));
    this.regularEntries =
        Collections.unmodifiableSortedMap(new TreeMap<String, JarEntrySnapshot>(regularEntries));
    this.logicalUnits =
        Collections.unmodifiableMap(new EnumMap<LogicalArea, LogicalUnitSnapshot>(logicalUnits));
    this.supportedSpringBootArchive = supportedSpringBootArchive;
  }

  public Path getSource() {
    return source;
  }

  public ArchivePreamble getArchivePreamble() {
    return archivePreamble;
  }

  public SortedMap<String, JarEntrySnapshot> getAllEntries() {
    return allEntries;
  }

  public SortedMap<String, JarEntrySnapshot> getRegularEntries() {
    return regularEntries;
  }

  public Map<LogicalArea, LogicalUnitSnapshot> getLogicalUnits() {
    return logicalUnits;
  }

  /**
   * Returns an empty snapshot for missing logical areas so callers can compare archives without
   * null checks.
   *
   * @param logicalArea logical area key
   * @return existing or empty logical unit snapshot
   */
  public LogicalUnitSnapshot getLogicalUnit(LogicalArea logicalArea) {
    LogicalUnitSnapshot snapshot = logicalUnits.get(logicalArea);
    if (snapshot == null) {
      return new LogicalUnitSnapshot(logicalArea, Collections.<String, JarEntrySnapshot>emptyMap());
    }
    return snapshot;
  }

  public boolean isSupportedSpringBootArchive() {
    return supportedSpringBootArchive;
  }
}
