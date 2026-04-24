package io.github.artern.fatjar.differ.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Validates that a patch is applied to the intended baseline and that the reconstructed archive
 * matches the expected target exactly.
 */
public final class TargetJarValidator {

  private final SpringBootFatJarScanner scanner = new SpringBootFatJarScanner();

  /**
   * Verifies that the currently installed archive expands to the same entry set as the baseline
   * used when the patch was created.
   */
  public void validateBaseline(Path currentJar, PatchManifest patchManifest) throws IOException {
    JarSnapshot snapshot = scanner.scan(currentJar);
    validateEntries(
        snapshot.getAllEntries(),
        patchManifest.getBaselineEntries(),
        patchManifest.getBaselineEntryCount(),
        patchManifest.getBaselineEntryCrcSumHex(),
        false,
        "Current jar");
  }

  /** Verifies preamble bytes and entry metadata after patch application. */
  public void validateTarget(Path outputJar, PatchManifest patchManifest, byte[] expectedPreamble)
      throws IOException {
    JarSnapshot snapshot = scanner.scan(outputJar);
    byte[] actualPreamble = snapshot.getArchivePreamble().getBytes();
    if (actualPreamble.length != patchManifest.getTargetArchivePreambleSize()) {
      throw new IllegalStateException(
          "Target archive preamble size mismatch. expected="
              + patchManifest.getTargetArchivePreambleSize()
              + ", actual="
              + actualPreamble.length);
    }
    if (!Arrays.equals(actualPreamble, expectedPreamble)) {
      throw new IllegalStateException("Target archive preamble content mismatch.");
    }
    validateEntries(
        snapshot.getAllEntries(),
        patchManifest.getTargetEntries(),
        patchManifest.getTargetEntryCount(),
        patchManifest.getTargetEntryCrcSumHex(),
        true,
        "Target jar");
  }

  private void validateEntries(
      Map<String, JarEntrySnapshot> actualEntries,
      List<JarEntrySnapshot> expectedEntries,
      int expectedCount,
      String expectedCrcSumHex,
      boolean includeMethod,
      String label) {
    if (actualEntries.size() != expectedCount) {
      throw new IllegalStateException(
          label
              + " entry count mismatch. expected="
              + expectedCount
              + ", actual="
              + actualEntries.size());
    }
    Iterator<JarEntrySnapshot> expectedIterator = expectedEntries.iterator();
    Iterator<Map.Entry<String, JarEntrySnapshot>> actualIterator =
        actualEntries.entrySet().iterator();
    while (expectedIterator.hasNext() && actualIterator.hasNext()) {
      JarEntrySnapshot expected = expectedIterator.next();
      JarEntrySnapshot actual = actualIterator.next().getValue();
      boolean matches =
          includeMethod ? expected.sameContent(actual) : expected.samePayloadContent(actual);
      if (!matches) {
        throw new IllegalStateException(label + " entry mismatch at " + expected.getPath());
      }
    }
    if (expectedCrcSumHex == null || expectedCrcSumHex.isEmpty()) {
      return;
    }
    long crcSum = 0L;
    for (JarEntrySnapshot actual : actualEntries.values()) {
      crcSum += actual.getCrc32();
    }
    String actualCrcSum = Long.toUnsignedString(crcSum, 16);
    if (!actualCrcSum.equalsIgnoreCase(expectedCrcSumHex)) {
      throw new IllegalStateException(
          label + " CRC sum mismatch. expected=" + expectedCrcSumHex + ", actual=" + actualCrcSum);
    }
  }
}
