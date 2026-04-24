package io.github.artern.fatjar.differ.core;

/**
 * Captures any bytes that appear before the first zip local-file header, such as a Spring Boot
 * launch script.
 */
public final class ArchivePreamble {

  private final byte[] bytes;

  public ArchivePreamble(byte[] bytes) {
    this.bytes = bytes == null ? new byte[0] : bytes.clone();
  }

  public byte[] getBytes() {
    return bytes.clone();
  }

  public int getSize() {
    return bytes.length;
  }

  public boolean isEmpty() {
    return bytes.length == 0;
  }
}
