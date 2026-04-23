package io.github.artern.fatjar.differ.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Shared hashing helpers used by the differ, metadata serializer, and runtime validator. */
final class HashingSupport {

  private HashingSupport() {}

  static String sha256Hex(Path path) throws IOException {
    MessageDigest digest = messageDigest();
    byte[] buffer = new byte[8192];
    try (InputStream inputStream = Files.newInputStream(path)) {
      for (; ; ) {
        int read = inputStream.read(buffer);
        if (read < 0) {
          break;
        }
        digest.update(buffer, 0, read);
      }
    }
    return toHex(digest.digest());
  }

  static String sha256Hex(String value) {
    MessageDigest digest = messageDigest();
    digest.update(value.getBytes(StandardCharsets.UTF_8));
    return toHex(digest.digest());
  }

  static String sha256Hex(byte[] value) {
    MessageDigest digest = messageDigest();
    digest.update(value);
    return toHex(digest.digest());
  }

  static String toHex(byte[] bytes) {
    StringBuilder builder = new StringBuilder(bytes.length * 2);
    for (byte element : bytes) {
      builder.append(String.format("%02x", element));
    }
    return builder.toString();
  }

  private static MessageDigest messageDigest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required", exception);
    }
  }
}
