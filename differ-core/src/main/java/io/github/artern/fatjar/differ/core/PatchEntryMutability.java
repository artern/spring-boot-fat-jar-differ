package io.github.artern.fatjar.differ.core;

import java.util.List;

/** Shared rules for deciding whether a current archive entry will be rewritten by a patch. */
public final class PatchEntryMutability {

  private PatchEntryMutability() {}

  /**
   * Returns whether the patch will replace, delete, or otherwise take ownership of the given entry
   * path when rebuilding the target archive.
   */
  public static boolean isMutable(String entryPath, List<PatchOperation> operations) {
    for (PatchOperation operation : operations) {
      if ((operation.getType() == PatchOperation.Type.REPLACE_TREE
              || operation.getType() == PatchOperation.Type.DELETE_TREE)
          && entryPath.startsWith(operation.getTargetPath())) {
        return true;
      }
      if (entryPath.equals(operation.getTargetPath())) {
        return operation.getType() == PatchOperation.Type.ADD_ENTRY
            || operation.getType() == PatchOperation.Type.REPLACE_ENTRY
            || operation.getType() == PatchOperation.Type.DELETE_ENTRY;
      }
    }
    return false;
  }
}
