package io.github.artern.fatjar.differ.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Bundles the two archive snapshots together with the manifest derived from their differences. */
public final class JarDiffPlan {

  private final JarSnapshot baselineSnapshot;
  private final JarSnapshot targetSnapshot;
  private final PatchManifest patchManifest;

  public JarDiffPlan(
      JarSnapshot baselineSnapshot, JarSnapshot targetSnapshot, PatchManifest patchManifest) {
    this.baselineSnapshot = baselineSnapshot;
    this.targetSnapshot = targetSnapshot;
    this.patchManifest = patchManifest;
  }

  public JarSnapshot getBaselineSnapshot() {
    return baselineSnapshot;
  }

  public JarSnapshot getTargetSnapshot() {
    return targetSnapshot;
  }

  public PatchManifest getPatchManifest() {
    return patchManifest;
  }

  /**
   * Exposes a defensive copy so callers can inspect operations without mutating the manifest state.
   */
  public List<PatchOperation> getOperations() {
    return Collections.unmodifiableList(
        new ArrayList<PatchOperation>(patchManifest.getOperations()));
  }
}
