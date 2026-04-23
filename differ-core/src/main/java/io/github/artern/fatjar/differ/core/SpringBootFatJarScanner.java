package io.github.artern.fatjar.differ.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Scans an executable Spring Boot archive into a structural snapshot that the differ and validator
 * can reason about.
 */
public final class SpringBootFatJarScanner {

  /**
   * Reads archive entries, captures any launch script preamble, and groups the logical areas that
   * are replaced as whole trees.
   */
  public JarSnapshot scan(Path jarPath) throws IOException {
    ArchivePreamble archivePreamble = ArchivePreambleSupport.read(jarPath);
    Map<String, JarEntrySnapshot> allEntries = new TreeMap<String, JarEntrySnapshot>();
    Map<String, JarEntrySnapshot> regularEntries = new TreeMap<String, JarEntrySnapshot>();
    Map<LogicalArea, Map<String, JarEntrySnapshot>> groupedEntries =
        new EnumMap<LogicalArea, Map<String, JarEntrySnapshot>>(LogicalArea.class);
    for (LogicalArea logicalArea : LogicalArea.values()) {
      groupedEntries.put(logicalArea, new LinkedHashMap<String, JarEntrySnapshot>());
    }
    boolean supportedSpringBootArchive = false;

    try (ZipFile jarFile = new ZipFile(jarPath.toFile())) {
      Enumeration<? extends ZipEntry> entries = jarFile.entries();
      while (entries.hasMoreElements()) {
        ZipEntry zipEntry = entries.nextElement();
        JarEntrySnapshot snapshot = JarEntrySnapshot.from(zipEntry);
        allEntries.put(snapshot.getPath(), snapshot);
        LogicalArea matched = null;
        // The first matching logical area wins; everything else remains a
        // regular entry and can be patched individually.
        for (LogicalArea logicalArea : LogicalArea.values()) {
          if (logicalArea.matches(snapshot.getPath())) {
            groupedEntries.get(logicalArea).put(snapshot.getPath(), snapshot);
            matched = logicalArea;
            supportedSpringBootArchive = true;
            break;
          }
        }
        if (matched == null) {
          regularEntries.put(snapshot.getPath(), snapshot);
        }
      }
    }

    Map<LogicalArea, LogicalUnitSnapshot> logicalUnits =
        new EnumMap<LogicalArea, LogicalUnitSnapshot>(LogicalArea.class);
    for (LogicalArea logicalArea : LogicalArea.values()) {
      logicalUnits.put(
          logicalArea, new LogicalUnitSnapshot(logicalArea, groupedEntries.get(logicalArea)));
    }
    return new JarSnapshot(
        jarPath,
        archivePreamble,
        allEntries,
        regularEntries,
        logicalUnits,
        supportedSpringBootArchive);
  }
}
