package io.github.artern.fatjar.differ.core;

import java.util.zip.ZipEntry;

/** Lightweight metadata snapshot for a single archive entry. */
public final class JarEntrySnapshot {

  private final String path;
  private final long crc32;
  private final long size;
  private final boolean directory;
  private final int method;

  public JarEntrySnapshot(String path, long crc32, long size, boolean directory) {
    this(path, crc32, size, directory, -1);
  }

  public JarEntrySnapshot(String path, long crc32, long size, boolean directory, int method) {
    this.path = path;
    this.crc32 = crc32;
    this.size = size;
    this.directory = directory;
    this.method = method;
  }

  public static JarEntrySnapshot from(ZipEntry zipEntry) {
    long crc32 = zipEntry.getCrc() < 0 ? 0L : zipEntry.getCrc();
    long size = zipEntry.getSize() < 0 ? 0L : zipEntry.getSize();
    return new JarEntrySnapshot(
        zipEntry.getName(), crc32, size, zipEntry.isDirectory(), zipEntry.getMethod());
  }

  public String getPath() {
    return path;
  }

  public long getCrc32() {
    return crc32;
  }

  public long getSize() {
    return size;
  }

  public boolean isDirectory() {
    return directory;
  }

  public int getMethod() {
    return method;
  }

  /**
   * Compares the entry shape and content metadata used by the differ.
   *
   * @param other candidate entry to compare
   * @return true if path, payload metadata, and method all match
   */
  public boolean sameContent(JarEntrySnapshot other) {
    return samePayloadContent(other) && method == other.method;
  }

  /**
   * Compares extracted entry content identity while ignoring archive-level metadata such as the zip
   * compression method.
   *
   * @param other candidate entry to compare
   * @return true if path, type, crc, and size match
   */
  public boolean samePayloadContent(JarEntrySnapshot other) {
    return other != null
        && directory == other.directory
        && crc32 == other.crc32
        && size == other.size
        && path.equals(other.path);
  }
}
