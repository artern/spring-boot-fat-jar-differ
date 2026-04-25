package io.github.artern.fatjar.differ.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates that a patch is applied to the intended baseline and that the reconstructed archive
 * matches the expected target exactly.
 */
public final class TargetJarValidator {

  private final SpringBootFatJarScanner scanner = new SpringBootFatJarScanner();

  /**
   * Verifies that the currently installed archive still matches the immutable part of the baseline
   * and does not contain extra immutable entries that the patch would leave behind.
   */
  public void validateBaseline(Path currentJar, PatchManifest patchManifest) throws IOException {
    JarSnapshot snapshot = scanner.scan(currentJar);
    List<PatchOperation> operations = patchManifest.getOperations();
    validateEntries(
        immutableActualEntries(snapshot.getAllEntries(), operations),
        immutableExpectedEntries(patchManifest.getBaselineEntries(), operations),
        immutableEntryCount(patchManifest.getBaselineEntries(), operations),
        immutableEntryCrcSumHex(patchManifest.getBaselineEntries(), operations),
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

  private Map<String, JarEntrySnapshot> immutableActualEntries(
      Map<String, JarEntrySnapshot> actualEntries, List<PatchOperation> operations) {
    Map<String, JarEntrySnapshot> immutableEntries = new LinkedHashMap<String, JarEntrySnapshot>();
    for (Map.Entry<String, JarEntrySnapshot> entry : actualEntries.entrySet()) {
      if (!PatchEntryMutability.isMutable(entry.getKey(), operations)) {
        immutableEntries.put(entry.getKey(), entry.getValue());
      }
    }
    return immutableEntries;
  }

  private List<JarEntrySnapshot> immutableExpectedEntries(
      List<JarEntrySnapshot> expectedEntries, List<PatchOperation> operations) {
    List<JarEntrySnapshot> immutableEntries = new java.util.ArrayList<JarEntrySnapshot>();
    for (JarEntrySnapshot entry : expectedEntries) {
      if (!PatchEntryMutability.isMutable(entry.getPath(), operations)) {
        immutableEntries.add(entry);
      }
    }
    return immutableEntries;
  }

  private int immutableEntryCount(
      List<JarEntrySnapshot> expectedEntries, List<PatchOperation> operations) {
    return immutableExpectedEntries(expectedEntries, operations).size();
  }

  private String immutableEntryCrcSumHex(
      List<JarEntrySnapshot> expectedEntries, List<PatchOperation> operations) {
    long crcSum = 0L;
    for (JarEntrySnapshot entry : expectedEntries) {
      if (!PatchEntryMutability.isMutable(entry.getPath(), operations)) {
        crcSum += entry.getCrc32();
      }
    }
    return Long.toUnsignedString(crcSum, 16);
  }
}
