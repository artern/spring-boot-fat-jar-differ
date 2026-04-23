package io.github.artern.fatjar.differ.core;

/** Archive regions that are treated as replace-as-a-whole trees instead of file-by-file deltas. */
public enum LogicalArea {
  META_INF("meta-inf", "META-INF/"),
  BOOT_INF_CLASSES("boot-inf-classes", "BOOT-INF/classes/"),
  WEB_INF_CLASSES("web-inf-classes", "WEB-INF/classes/");

  private final String id;
  private final String prefix;

  LogicalArea(String id, String prefix) {
    this.id = id;
    this.prefix = prefix;
  }

  public String getId() {
    return id;
  }

  public String getPrefix() {
    return prefix;
  }

  public boolean matches(String path) {
    return path.startsWith(prefix);
  }
}
