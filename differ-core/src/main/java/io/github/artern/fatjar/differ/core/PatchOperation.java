package io.github.artern.fatjar.differ.core;

/** Describes one archive mutation that the runtime patcher must perform. */
public final class PatchOperation {

  /** Supported mutation shapes for the patch runtime. */
  public enum Type {
    REPLACE_TREE,
    DELETE_TREE,
    ADD_ENTRY,
    REPLACE_ENTRY,
    DELETE_ENTRY
  }

  private Type type;
  private String targetPath;
  private boolean directory;

  public PatchOperation() {}

  public PatchOperation(Type type, String targetPath, boolean directory) {
    this.type = type;
    this.targetPath = targetPath;
    this.directory = directory;
  }

  public Type getType() {
    return type;
  }

  public void setType(Type type) {
    this.type = type;
  }

  public String getTargetPath() {
    return targetPath;
  }

  public void setTargetPath(String targetPath) {
    this.targetPath = targetPath;
  }

  public boolean isDirectory() {
    return directory;
  }

  public void setDirectory(boolean directory) {
    this.directory = directory;
  }

  /** Returns whether the patch bundle must carry replacement bytes for this operation. */
  public boolean requiresPayload() {
    return type == Type.REPLACE_TREE || type == Type.ADD_ENTRY || type == Type.REPLACE_ENTRY;
  }
}
