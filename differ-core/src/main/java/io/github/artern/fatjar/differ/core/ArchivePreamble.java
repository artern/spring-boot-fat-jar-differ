package io.github.artern.fatjar.differ.core;

/**
 * Captures any bytes that appear before the first zip local-file header, such as a Spring Boot
 * launch script.
 */
public final class ArchivePreamble {

  private final byte[] bytes;
  private final String sha256;

  public ArchivePreamble(byte[] bytes) {
    this.bytes = bytes == null ? new byte[0] : bytes.clone();
    this.sha256 = HashingSupport.sha256Hex(this.bytes);
  }

  public byte[] getBytes() {
    return bytes.clone();
  }

  public String getSha256() {
    return sha256;
  }

  public int getSize() {
    return bytes.length;
  }

  public boolean isEmpty() {
    return bytes.length == 0;
  }
}
