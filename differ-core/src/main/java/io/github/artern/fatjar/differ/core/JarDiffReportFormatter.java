package io.github.artern.fatjar.differ.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.zip.ZipEntry;

/** Renders a human-readable per-entry diff so operators can audit generated patches. */
public final class JarDiffReportFormatter {

  /** Expands the structural plan into concrete entry-level log lines. */
  public List<String> format(JarDiffPlan plan) {
    List<String> lines = new ArrayList<String>();
    appendPreambleLine(plan, lines);
    for (PatchOperation operation : plan.getOperations()) {
      switch (operation.getType()) {
        case REPLACE_TREE:
        case DELETE_TREE:
          appendTreeOperation(plan, operation, lines);
          break;
        case ADD_ENTRY:
          lines.add(
              formatEntryLine(
                  "ADD", operation.getTargetPath(), null, targetEntry(plan, operation)));
          break;
        case REPLACE_ENTRY:
          lines.add(
              formatEntryLine(
                  "REPLACE",
                  operation.getTargetPath(),
                  baselineEntry(plan, operation),
                  targetEntry(plan, operation)));
          break;
        case DELETE_ENTRY:
          lines.add(
              formatEntryLine(
                  "DELETE", operation.getTargetPath(), baselineEntry(plan, operation), null));
          break;
        default:
          break;
      }
    }
    if (lines.isEmpty()) {
      lines.add("  [NOOP] No archive mutations were required.");
    }
    return lines;
  }

  private void appendPreambleLine(JarDiffPlan plan, List<String> lines) {
    ArchivePreamble baseline = plan.getBaselineSnapshot().getArchivePreamble();
    ArchivePreamble target = plan.getTargetSnapshot().getArchivePreamble();
    if (!Arrays.equals(baseline.getBytes(), target.getBytes())) {
      lines.add(
          String.format(
              "  [REPLACE_PREAMBLE] archive-preamble (size %d -> %d)",
              baseline.getSize(), target.getSize()));
    }
  }

  private void appendTreeOperation(JarDiffPlan plan, PatchOperation operation, List<String> lines) {
    lines.add("  [" + operation.getType().name() + "] " + operation.getTargetPath());
    LogicalArea logicalArea = findLogicalArea(operation.getTargetPath());
    if (logicalArea == null) {
      return;
    }
    LogicalUnitSnapshot baselineUnit = plan.getBaselineSnapshot().getLogicalUnit(logicalArea);
    LogicalUnitSnapshot targetUnit = plan.getTargetSnapshot().getLogicalUnit(logicalArea);
    SortedSet<String> paths = new TreeSet<String>();
    paths.addAll(baselineUnit.getEntries().keySet());
    paths.addAll(targetUnit.getEntries().keySet());
    for (String path : paths) {
      JarEntrySnapshot baselineEntry = baselineUnit.getEntries().get(path);
      JarEntrySnapshot targetEntry = targetUnit.getEntries().get(path);
      if (baselineEntry == null && targetEntry != null) {
        lines.add("    " + formatEntryLine("ADD", path, null, targetEntry).trim());
      } else if (baselineEntry != null && targetEntry == null) {
        lines.add("    " + formatEntryLine("DELETE", path, baselineEntry, null).trim());
      } else if (baselineEntry != null
          && targetEntry != null
          && !baselineEntry.sameContent(targetEntry)) {
        lines.add("    " + formatEntryLine("REPLACE", path, baselineEntry, targetEntry).trim());
      }
    }
  }

  private JarEntrySnapshot baselineEntry(JarDiffPlan plan, PatchOperation operation) {
    return plan.getBaselineSnapshot().getAllEntries().get(operation.getTargetPath());
  }

  private JarEntrySnapshot targetEntry(JarDiffPlan plan, PatchOperation operation) {
    return plan.getTargetSnapshot().getAllEntries().get(operation.getTargetPath());
  }

  private LogicalArea findLogicalArea(String targetPath) {
    for (LogicalArea logicalArea : LogicalArea.values()) {
      if (logicalArea.getPrefix().equals(targetPath)) {
        return logicalArea;
      }
    }
    return null;
  }

  private String formatEntryLine(
      String label, String path, JarEntrySnapshot baselineEntry, JarEntrySnapshot targetEntry) {
    StringBuilder builder = new StringBuilder();
    builder.append("[").append(label).append("] ").append(path);
    if (baselineEntry == null && targetEntry != null) {
      builder.append(" (").append(entryShape(targetEntry)).append(")");
    } else if (baselineEntry != null && targetEntry == null) {
      builder.append(" (").append(entryShape(baselineEntry)).append(")");
    } else if (baselineEntry != null && targetEntry != null) {
      builder
          .append(" (size ")
          .append(baselineEntry.getSize())
          .append(" -> ")
          .append(targetEntry.getSize())
          .append(", method ")
          .append(methodName(baselineEntry.getMethod()))
          .append(" -> ")
          .append(methodName(targetEntry.getMethod()))
          .append(")");
    }
    return "  " + builder.toString();
  }

  private String entryShape(JarEntrySnapshot entry) {
    return "size=" + entry.getSize() + ", method=" + methodName(entry.getMethod());
  }

  private String methodName(int method) {
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
