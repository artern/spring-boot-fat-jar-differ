package io.github.artern.fatjar.differ.core;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Compares two Spring Boot executable archives and produces the patch manifest that drives both
 * payload assembly and target validation.
 */
public final class JarDiffEngine {

  private final SpringBootFatJarScanner scanner = new SpringBootFatJarScanner();

  /**
   * Builds a diff plan from the previous release archive to the new target archive.
   *
   * @param baselineJar baseline archive path
   * @param targetJar target archive path
   * @param toolVersion differ tool version to persist in metadata
   * @return fully computed diff plan
   * @throws IOException when either archive cannot be scanned
   */
  public JarDiffPlan createPlan(Path baselineJar, Path targetJar, String toolVersion)
      throws IOException {
    JarSnapshot baselineSnapshot = scanner.scan(baselineJar);
    JarSnapshot targetSnapshot = scanner.scan(targetJar);
    if (!targetSnapshot.isSupportedSpringBootArchive()) {
      throw new IllegalArgumentException(
          "Target archive is not a supported Spring Boot executable archive: " + targetJar);
    }
    if (!baselineSnapshot.isSupportedSpringBootArchive()) {
      throw new IllegalArgumentException(
          "Baseline archive is not a supported Spring Boot executable archive: " + baselineJar);
    }

    PatchManifest manifest = new PatchManifest();
    manifest.setToolVersion(toolVersion);
    manifest.setCreatedAt(Instant.now().toString());
    manifest.setBaselineFileName(baselineJar.getFileName().toString());
    manifest.setTargetFileName(targetJar.getFileName().toString());
    manifest.setTargetArchivePreambleSize(targetSnapshot.getArchivePreamble().getSize());

    List<PatchOperation> operations = manifest.getOperations();
    // Logical areas are replaced as whole trees to keep patch application simple
    // and avoid mixing classpath-critical content from different releases.
    for (LogicalArea logicalArea : LogicalArea.values()) {
      LogicalUnitSnapshot baselineUnit = baselineSnapshot.getLogicalUnit(logicalArea);
      LogicalUnitSnapshot targetUnit = targetSnapshot.getLogicalUnit(logicalArea);
      if (!baselineUnit.structurallyEquals(targetUnit)) {
        PatchOperation.Type type =
            targetUnit.isEmpty()
                ? PatchOperation.Type.DELETE_TREE
                : PatchOperation.Type.REPLACE_TREE;
        operations.add(new PatchOperation(type, logicalArea.getPrefix(), true));
      }
    }

    SortedSet<String> allRegularPaths = new TreeSet<String>();
    allRegularPaths.addAll(baselineSnapshot.getRegularEntries().keySet());
    allRegularPaths.addAll(targetSnapshot.getRegularEntries().keySet());
    // Everything outside the logical areas keeps fine-grained file semantics.
    for (String path : allRegularPaths) {
      JarEntrySnapshot baselineEntry = baselineSnapshot.getRegularEntries().get(path);
      JarEntrySnapshot targetEntry = targetSnapshot.getRegularEntries().get(path);
      if (baselineEntry == null && targetEntry != null) {
        operations.add(
            new PatchOperation(PatchOperation.Type.ADD_ENTRY, path, targetEntry.isDirectory()));
      } else if (baselineEntry != null && targetEntry == null) {
        operations.add(
            new PatchOperation(
                PatchOperation.Type.DELETE_ENTRY, path, baselineEntry.isDirectory()));
      } else if (baselineEntry != null
          && targetEntry != null
          && !baselineEntry.sameContent(targetEntry)) {
        operations.add(
            new PatchOperation(PatchOperation.Type.REPLACE_ENTRY, path, targetEntry.isDirectory()));
      }
    }

    manifest.getBaselineEntries().addAll(baselineSnapshot.getAllEntries().values());
    manifest.setBaselineEntryCount(manifest.getBaselineEntries().size());
    manifest.setBaselineEntryCrcSumHex(crcSumHex(baselineSnapshot.getAllEntries().values()));

    manifest.getTargetEntries().addAll(targetSnapshot.getAllEntries().values());
    manifest.setTargetEntryCount(manifest.getTargetEntries().size());
    manifest.setTargetEntryCrcSumHex(crcSumHex(targetSnapshot.getAllEntries().values()));

    return new JarDiffPlan(baselineSnapshot, targetSnapshot, manifest);
  }

  private String crcSumHex(Iterable<JarEntrySnapshot> entries) {
    long crcSum = 0L;
    for (JarEntrySnapshot entry : entries) {
      crcSum += entry.getCrc32();
    }
    return Long.toUnsignedString(crcSum, 16);
  }

  /**
   * Returns a compact human-readable summary for CLI and build logs.
   *
   * @param plan diff plan to summarize
   * @return compact summary string
   */
  public String summarize(JarDiffPlan plan) {
    int replaceTrees = 0;
    int adds = 0;
    int replaces = 0;
    int deletes = 0;
    List<PatchOperation> operations = new ArrayList<PatchOperation>(plan.getOperations());
    for (PatchOperation operation : operations) {
      switch (operation.getType()) {
        case REPLACE_TREE:
        case DELETE_TREE:
          replaceTrees++;
          break;
        case ADD_ENTRY:
          adds++;
          break;
        case REPLACE_ENTRY:
          replaces++;
          break;
        case DELETE_ENTRY:
          deletes++;
          break;
        default:
          break;
      }
    }
    return String.format(
        "logicalAreas=%d, add=%d, replace=%d, delete=%d", replaceTrees, adds, replaces, deletes);
  }
}
