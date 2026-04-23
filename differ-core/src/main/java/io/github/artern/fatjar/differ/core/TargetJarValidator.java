package io.github.artern.fatjar.differ.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;

/**
 * Validates that a patch is applied to the intended baseline and that the reconstructed archive
 * matches the expected target exactly.
 */
public final class TargetJarValidator {

  private final SpringBootFatJarScanner scanner = new SpringBootFatJarScanner();

  /**
   * Verifies that the currently installed archive is byte-for-byte the same as the baseline used
   * when the patch was created.
   */
  public void validateBaseline(Path currentJar, PatchManifest patchManifest) throws IOException {
    String currentSha256 = HashingSupport.sha256Hex(currentJar);
    if (!currentSha256.equalsIgnoreCase(patchManifest.getBaselineSha256())) {
      throw new IllegalStateException(
          "Current jar does not match the baseline used to create this patch. expected="
              + patchManifest.getBaselineSha256()
              + ", actual="
              + currentSha256);
    }
  }

  /** Verifies preamble, entries, and logical area fingerprints after patch application. */
  public void validateTarget(Path outputJar, PatchManifest patchManifest) throws IOException {
    JarSnapshot snapshot = scanner.scan(outputJar);
    if (snapshot.getArchivePreamble().getSize() != patchManifest.getTargetArchivePreambleSize()) {
      throw new IllegalStateException(
          "Target archive preamble size mismatch. expected="
              + patchManifest.getTargetArchivePreambleSize()
              + ", actual="
              + snapshot.getArchivePreamble().getSize());
    }
    if (!snapshot
        .getArchivePreamble()
        .getSha256()
        .equalsIgnoreCase(patchManifest.getTargetArchivePreambleSha256())) {
      throw new IllegalStateException(
          "Target archive preamble hash mismatch. expected="
              + patchManifest.getTargetArchivePreambleSha256()
              + ", actual="
              + snapshot.getArchivePreamble().getSha256());
    }
    if (snapshot.getAllEntries().size() != patchManifest.getTargetEntryCount()) {
      throw new IllegalStateException(
          "Target entry count mismatch. expected="
              + patchManifest.getTargetEntryCount()
              + ", actual="
              + snapshot.getAllEntries().size());
    }
    Iterator<JarEntrySnapshot> expectedIterator = patchManifest.getTargetEntries().iterator();
    Iterator<Map.Entry<String, JarEntrySnapshot>> actualIterator =
        snapshot.getAllEntries().entrySet().iterator();
    while (expectedIterator.hasNext() && actualIterator.hasNext()) {
      JarEntrySnapshot expected = expectedIterator.next();
      JarEntrySnapshot actual = actualIterator.next().getValue();
      if (!expected.sameContent(actual)) {
        throw new IllegalStateException("Target jar entry mismatch at " + expected.getPath());
      }
    }
    long crcSum = 0L;
    for (JarEntrySnapshot actual : snapshot.getAllEntries().values()) {
      crcSum += actual.getCrc32();
    }
    String actualCrcSum = Long.toUnsignedString(crcSum, 16);
    if (!actualCrcSum.equalsIgnoreCase(patchManifest.getTargetEntryCrcSumHex())) {
      throw new IllegalStateException(
          "Target jar CRC sum mismatch. expected="
              + patchManifest.getTargetEntryCrcSumHex()
              + ", actual="
              + actualCrcSum);
    }
    for (LogicalArea logicalArea : LogicalArea.values()) {
      LogicalUnitSnapshot logicalUnitSnapshot = snapshot.getLogicalUnit(logicalArea);
      String expectedFingerprint =
          patchManifest.getLogicalUnitFingerprints().get(logicalArea.getId());
      if (!logicalUnitSnapshot.getFingerprint().equals(expectedFingerprint)) {
        throw new IllegalStateException(
            "Target logical area mismatch for " + logicalArea.getPrefix());
      }
    }
  }
}
