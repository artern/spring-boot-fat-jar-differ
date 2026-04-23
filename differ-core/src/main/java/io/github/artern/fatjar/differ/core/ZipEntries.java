package io.github.artern.fatjar.differ.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Small zip utility methods shared by bundle assembly and metadata writing. */
public final class ZipEntries {

  private ZipEntries() {}

  /**
   * Copies the source entry name-independent metadata onto a new output entry so the rebuilt
   * archive preserves Spring Boot packaging constraints such as STORED nested jars.
   */
  public static ZipEntry copyMetadata(ZipEntry sourceEntry, String targetName) {
    ZipEntry outputEntry =
        new ZipEntry(sourceEntry.isDirectory() ? normalizeDirectory(targetName) : targetName);
    if (sourceEntry.getTime() >= 0) {
      outputEntry.setTime(sourceEntry.getTime());
    }
    if (sourceEntry.getLastModifiedTime() != null) {
      outputEntry.setLastModifiedTime(sourceEntry.getLastModifiedTime());
    }
    if (sourceEntry.getLastAccessTime() != null) {
      outputEntry.setLastAccessTime(sourceEntry.getLastAccessTime());
    }
    if (sourceEntry.getCreationTime() != null) {
      outputEntry.setCreationTime(sourceEntry.getCreationTime());
    }
    if (sourceEntry.getMethod() >= 0) {
      outputEntry.setMethod(sourceEntry.getMethod());
      if (sourceEntry.getSize() >= 0) {
        outputEntry.setSize(sourceEntry.getSize());
      }
      if (sourceEntry.getCrc() >= 0) {
        outputEntry.setCrc(sourceEntry.getCrc());
      }
      if (sourceEntry.getMethod() == ZipEntry.STORED && sourceEntry.getCompressedSize() >= 0) {
        outputEntry.setCompressedSize(sourceEntry.getCompressedSize());
      }
    }
    if (sourceEntry.getExtra() != null) {
      outputEntry.setExtra(sourceEntry.getExtra());
    }
    if (sourceEntry.getComment() != null) {
      outputEntry.setComment(sourceEntry.getComment());
    }
    return outputEntry;
  }

  static void copyFromZipFile(
      ZipFile zipFile, ZipEntry zipEntry, ZipOutputStream zipOutputStream, String targetName)
      throws IOException {
    if (zipEntry.isDirectory()) {
      writeDirectory(zipOutputStream, targetName);
      return;
    }
    try (InputStream inputStream = zipFile.getInputStream(zipEntry)) {
      writeStream(zipOutputStream, copyMetadata(zipEntry, targetName), inputStream);
    }
  }

  static void copyFromZipInputStream(
      ZipInputStream zipInputStream, ZipEntry zipEntry, ZipOutputStream zipOutputStream)
      throws IOException {
    if (zipEntry.isDirectory()) {
      writeDirectory(zipOutputStream, zipEntry.getName());
      return;
    }
    writeStream(zipOutputStream, copyMetadata(zipEntry, zipEntry.getName()), zipInputStream);
  }

  static void writeUtf8(ZipOutputStream zipOutputStream, String entryName, String content)
      throws IOException {
    writeStream(
        zipOutputStream,
        new ZipEntry(entryName),
        new java.io.ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
  }

  static void writeDirectory(ZipOutputStream zipOutputStream, String entryName) throws IOException {
    String normalized = entryName.endsWith("/") ? entryName : entryName + "/";
    ZipEntry zipEntry = new ZipEntry(normalized);
    zipOutputStream.putNextEntry(zipEntry);
    zipOutputStream.closeEntry();
  }

  static void writeStream(
      ZipOutputStream zipOutputStream, ZipEntry zipEntry, InputStream inputStream)
      throws IOException {
    zipOutputStream.putNextEntry(zipEntry);
    byte[] buffer = new byte[8192];
    for (; ; ) {
      int read = inputStream.read(buffer);
      if (read < 0) {
        break;
      }
      zipOutputStream.write(buffer, 0, read);
    }
    zipOutputStream.closeEntry();
  }

  static Set<String> listNames(ZipFile zipFile) {
    Set<String> names = new LinkedHashSet<String>();
    Enumeration<? extends ZipEntry> entries = zipFile.entries();
    while (entries.hasMoreElements()) {
      names.add(entries.nextElement().getName());
    }
    return names;
  }

  private static String normalizeDirectory(String entryName) {
    return entryName.endsWith("/") ? entryName : entryName + "/";
  }
}
