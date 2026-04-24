package io.github.artern.fatjar.differ.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Serializable description of the patch content, expected target archive, and validation metadata.
 */
public final class PatchManifest {

  private String formatVersion = "2";
  private String toolVersion;
  private String createdAt;
  private String baselineFileName;
  private String baselineEntryCrcSumHex;
  private int baselineEntryCount;
  private String targetFileName;
  private int targetArchivePreambleSize;
  private String targetEntryCrcSumHex;
  private int targetEntryCount;
  private final List<PatchOperation> operations = new ArrayList<PatchOperation>();
  private final List<JarEntrySnapshot> baselineEntries = new ArrayList<JarEntrySnapshot>();
  private final List<JarEntrySnapshot> targetEntries = new ArrayList<JarEntrySnapshot>();

  public String getFormatVersion() {
    return formatVersion;
  }

  public void setFormatVersion(String formatVersion) {
    this.formatVersion = formatVersion;
  }

  public String getToolVersion() {
    return toolVersion;
  }

  public void setToolVersion(String toolVersion) {
    this.toolVersion = toolVersion;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(String createdAt) {
    this.createdAt = createdAt;
  }

  public String getBaselineFileName() {
    return baselineFileName;
  }

  public void setBaselineFileName(String baselineFileName) {
    this.baselineFileName = baselineFileName;
  }

  public String getBaselineEntryCrcSumHex() {
    return baselineEntryCrcSumHex;
  }

  public void setBaselineEntryCrcSumHex(String baselineEntryCrcSumHex) {
    this.baselineEntryCrcSumHex = baselineEntryCrcSumHex;
  }

  public int getBaselineEntryCount() {
    return baselineEntryCount;
  }

  public void setBaselineEntryCount(int baselineEntryCount) {
    this.baselineEntryCount = baselineEntryCount;
  }

  public String getTargetFileName() {
    return targetFileName;
  }

  public void setTargetFileName(String targetFileName) {
    this.targetFileName = targetFileName;
  }

  public int getTargetArchivePreambleSize() {
    return targetArchivePreambleSize;
  }

  public void setTargetArchivePreambleSize(int targetArchivePreambleSize) {
    this.targetArchivePreambleSize = targetArchivePreambleSize;
  }

  public String getTargetEntryCrcSumHex() {
    return targetEntryCrcSumHex;
  }

  public void setTargetEntryCrcSumHex(String targetEntryCrcSumHex) {
    this.targetEntryCrcSumHex = targetEntryCrcSumHex;
  }

  public int getTargetEntryCount() {
    return targetEntryCount;
  }

  public void setTargetEntryCount(int targetEntryCount) {
    this.targetEntryCount = targetEntryCount;
  }

  public List<PatchOperation> getOperations() {
    return operations;
  }

  public List<JarEntrySnapshot> getBaselineEntries() {
    return baselineEntries;
  }

  public List<JarEntrySnapshot> getTargetEntries() {
    return targetEntries;
  }
}
