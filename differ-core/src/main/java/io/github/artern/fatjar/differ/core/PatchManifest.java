package io.github.artern.fatjar.differ.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializable description of the patch content, expected target archive, and validation
 * fingerprints.
 */
public final class PatchManifest {

  private String formatVersion = "1";
  private String toolVersion;
  private String createdAt;
  private String baselineFileName;
  private String baselineSha256;
  private String targetFileName;
  private String targetSha256;
  private String targetArchivePreambleSha256;
  private int targetArchivePreambleSize;
  private String targetEntryCrcSumHex;
  private int targetEntryCount;
  private final Map<String, String> logicalUnitCrcSums = new LinkedHashMap<String, String>();
  private final Map<String, String> logicalUnitFingerprints = new LinkedHashMap<String, String>();
  private final List<PatchOperation> operations = new ArrayList<PatchOperation>();
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

  public String getBaselineSha256() {
    return baselineSha256;
  }

  public void setBaselineSha256(String baselineSha256) {
    this.baselineSha256 = baselineSha256;
  }

  public String getTargetFileName() {
    return targetFileName;
  }

  public void setTargetFileName(String targetFileName) {
    this.targetFileName = targetFileName;
  }

  public String getTargetSha256() {
    return targetSha256;
  }

  public void setTargetSha256(String targetSha256) {
    this.targetSha256 = targetSha256;
  }

  public String getTargetArchivePreambleSha256() {
    return targetArchivePreambleSha256;
  }

  public void setTargetArchivePreambleSha256(String targetArchivePreambleSha256) {
    this.targetArchivePreambleSha256 = targetArchivePreambleSha256;
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

  public Map<String, String> getLogicalUnitCrcSums() {
    return logicalUnitCrcSums;
  }

  public Map<String, String> getLogicalUnitFingerprints() {
    return logicalUnitFingerprints;
  }

  public List<PatchOperation> getOperations() {
    return operations;
  }

  public List<JarEntrySnapshot> getTargetEntries() {
    return targetEntries;
  }
}
