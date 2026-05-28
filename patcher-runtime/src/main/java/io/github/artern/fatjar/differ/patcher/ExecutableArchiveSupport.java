package io.github.artern.fatjar.differ.patcher;

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;

/** Writes executable archives with a launch-script preamble while keeping ZIP offsets valid. */
final class ExecutableArchiveSupport {

  private static final int END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50;
  private static final int ZIP64_END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06064b50;
  private static final int ZIP64_END_OF_CENTRAL_DIRECTORY_LOCATOR_SIGNATURE = 0x07064b50;
  private static final int CENTRAL_DIRECTORY_FILE_HEADER_SIGNATURE = 0x02014b50;
  private static final int ZIP64_EXTENDED_INFORMATION_EXTRA_FIELD = 0x0001;
  private static final int MAX_END_OF_CENTRAL_DIRECTORY_SEARCH = 65_557;

  private ExecutableArchiveSupport() {}

  static void writeArchive(Path zipPayload, Path outputArchive, byte[] preamble)
      throws IOException {
    Files.deleteIfExists(outputArchive);
    if (preamble.length == 0) {
      Files.move(zipPayload, outputArchive, StandardCopyOption.REPLACE_EXISTING);
      return;
    }
    try (OutputStream outputStream = Files.newOutputStream(outputArchive)) {
      outputStream.write(preamble);
      Files.copy(zipPayload, outputStream);
    }
    shiftZipOffsets(outputArchive, preamble.length);
  }

  static void copyFilePermissions(Path source, Path target) throws IOException {
    PosixFileAttributeView sourceView =
        Files.getFileAttributeView(source, PosixFileAttributeView.class);
    PosixFileAttributeView targetView =
        Files.getFileAttributeView(target, PosixFileAttributeView.class);
    if (sourceView != null && targetView != null) {
      targetView.setPermissions(sourceView.readAttributes().permissions());
      return;
    }
    java.io.File sourceFile = source.toFile();
    java.io.File targetFile = target.toFile();
    targetFile.setReadable(sourceFile.canRead(), false);
    targetFile.setWritable(sourceFile.canWrite(), false);
    targetFile.setExecutable(sourceFile.canExecute(), false);
  }

  private static void shiftZipOffsets(Path archive, long shift) throws IOException {
    try (RandomAccessFile randomAccessFile = new RandomAccessFile(archive.toFile(), "rw")) {
      long endOfCentralDirectoryOffset = findEndOfCentralDirectory(randomAccessFile);
      if (endOfCentralDirectoryOffset < 0) {
        throw new IOException("Unable to locate end of central directory in executable archive");
      }
      long centralDirectoryOffset =
          readUnsignedIntLE(randomAccessFile, endOfCentralDirectoryOffset + 16);
      long zip64RecordOffset = -1L;
      long zip64LocatorOffset = endOfCentralDirectoryOffset - 20;
      if (zip64LocatorOffset >= 0
          && readIntLE(randomAccessFile, zip64LocatorOffset)
              == ZIP64_END_OF_CENTRAL_DIRECTORY_LOCATOR_SIGNATURE) {
        zip64RecordOffset = readLongLE(randomAccessFile, zip64LocatorOffset + 8);
        long shiftedZip64RecordOffset = zip64RecordOffset + shift;
        if (readIntLE(randomAccessFile, shiftedZip64RecordOffset)
            != ZIP64_END_OF_CENTRAL_DIRECTORY_SIGNATURE) {
          throw new IOException("Invalid ZIP64 end of central directory record after preamble");
        }
        if (centralDirectoryOffset == 0xffffffffL) {
          centralDirectoryOffset = readLongLE(randomAccessFile, shiftedZip64RecordOffset + 48);
        }
        writeLongLE(randomAccessFile, zip64LocatorOffset + 8, shiftedZip64RecordOffset);
        writeLongLE(
            randomAccessFile,
            shiftedZip64RecordOffset + 48,
            readLongLE(randomAccessFile, shiftedZip64RecordOffset + 48) + shift);
      }
      shiftCentralDirectoryEntryOffsets(randomAccessFile, centralDirectoryOffset + shift, shift);
      if (centralDirectoryOffset != 0xffffffffL) {
        writeIntLE(
            randomAccessFile,
            endOfCentralDirectoryOffset + 16,
            checkedInt(centralDirectoryOffset + shift));
      }
    }
  }

  private static void shiftCentralDirectoryEntryOffsets(
      RandomAccessFile randomAccessFile, long centralDirectoryOffset, long shift)
      throws IOException {
    long cursor = centralDirectoryOffset;
    while (cursor < randomAccessFile.length()) {
      int signature = readIntLE(randomAccessFile, cursor);
      if (signature == CENTRAL_DIRECTORY_FILE_HEADER_SIGNATURE) {
        int nameLength = readUnsignedShortLE(randomAccessFile, cursor + 28);
        int extraLength = readUnsignedShortLE(randomAccessFile, cursor + 30);
        int commentLength = readUnsignedShortLE(randomAccessFile, cursor + 32);
        long localHeaderOffset = readUnsignedIntLE(randomAccessFile, cursor + 42);
        if (localHeaderOffset == 0xffffffffL) {
          shiftZip64LocalHeaderOffset(randomAccessFile, cursor, nameLength, extraLength, shift);
        } else {
          writeIntLE(randomAccessFile, cursor + 42, checkedInt(localHeaderOffset + shift));
        }
        cursor += 46L + nameLength + extraLength + commentLength;
        continue;
      }
      if (signature == ZIP64_END_OF_CENTRAL_DIRECTORY_SIGNATURE
          || signature == ZIP64_END_OF_CENTRAL_DIRECTORY_LOCATOR_SIGNATURE
          || signature == END_OF_CENTRAL_DIRECTORY_SIGNATURE) {
        return;
      }
      throw new IOException(
          "Unexpected ZIP structure while rewriting executable archive offsets at " + cursor);
    }
    throw new IOException("Unexpected end of archive while rewriting central directory");
  }

  private static void shiftZip64LocalHeaderOffset(
      RandomAccessFile randomAccessFile,
      long centralDirectoryEntryOffset,
      int nameLength,
      int extraLength,
      long shift)
      throws IOException {
    long extraOffset = centralDirectoryEntryOffset + 46L + nameLength;
    long extraEnd = extraOffset + extraLength;
    int valueOffset = 0;
    if (readUnsignedIntLE(randomAccessFile, centralDirectoryEntryOffset + 24) == 0xffffffffL) {
      valueOffset += 8;
    }
    if (readUnsignedIntLE(randomAccessFile, centralDirectoryEntryOffset + 20) == 0xffffffffL) {
      valueOffset += 8;
    }
    long cursor = extraOffset;
    while (cursor + 4 <= extraEnd) {
      int headerId = readUnsignedShortLE(randomAccessFile, cursor);
      int dataSize = readUnsignedShortLE(randomAccessFile, cursor + 2);
      long dataOffset = cursor + 4;
      if (headerId == ZIP64_EXTENDED_INFORMATION_EXTRA_FIELD) {
        long offsetField = dataOffset + valueOffset;
        if (offsetField + 8 > dataOffset + dataSize) {
          throw new IOException("Malformed ZIP64 extra field for central directory entry");
        }
        writeLongLE(
            randomAccessFile, offsetField, readLongLE(randomAccessFile, offsetField) + shift);
        return;
      }
      cursor = dataOffset + dataSize;
    }
    throw new IOException("Missing ZIP64 local header offset in central directory entry");
  }

  private static long findEndOfCentralDirectory(RandomAccessFile randomAccessFile)
      throws IOException {
    long archiveSize = randomAccessFile.length();
    int searchWindow = (int) Math.min(archiveSize, MAX_END_OF_CENTRAL_DIRECTORY_SEARCH);
    byte[] tail = new byte[searchWindow];
    long tailOffset = archiveSize - searchWindow;
    randomAccessFile.seek(tailOffset);
    randomAccessFile.readFully(tail);
    for (int index = tail.length - 4; index >= 0; index--) {
      if ((tail[index] & 0xff) == 0x50
          && (tail[index + 1] & 0xff) == 0x4b
          && (tail[index + 2] & 0xff) == 0x05
          && (tail[index + 3] & 0xff) == 0x06) {
        return tailOffset + index;
      }
    }
    return -1L;
  }

  private static int readIntLE(RandomAccessFile randomAccessFile, long offset) throws IOException {
    byte[] buffer = new byte[4];
    randomAccessFile.seek(offset);
    randomAccessFile.readFully(buffer);
    return ((buffer[0] & 0xff))
        | ((buffer[1] & 0xff) << 8)
        | ((buffer[2] & 0xff) << 16)
        | ((buffer[3] & 0xff) << 24);
  }

  private static int readUnsignedShortLE(RandomAccessFile randomAccessFile, long offset)
      throws IOException {
    byte[] buffer = new byte[2];
    randomAccessFile.seek(offset);
    randomAccessFile.readFully(buffer);
    return (buffer[0] & 0xff) | ((buffer[1] & 0xff) << 8);
  }

  private static long readUnsignedIntLE(RandomAccessFile randomAccessFile, long offset)
      throws IOException {
    return Integer.toUnsignedLong(readIntLE(randomAccessFile, offset));
  }

  private static long readLongLE(RandomAccessFile randomAccessFile, long offset)
      throws IOException {
    byte[] buffer = new byte[8];
    randomAccessFile.seek(offset);
    randomAccessFile.readFully(buffer);
    return ((long) buffer[0] & 0xffL)
        | (((long) buffer[1] & 0xffL) << 8)
        | (((long) buffer[2] & 0xffL) << 16)
        | (((long) buffer[3] & 0xffL) << 24)
        | (((long) buffer[4] & 0xffL) << 32)
        | (((long) buffer[5] & 0xffL) << 40)
        | (((long) buffer[6] & 0xffL) << 48)
        | (((long) buffer[7] & 0xffL) << 56);
  }

  private static void writeIntLE(RandomAccessFile randomAccessFile, long offset, int value)
      throws IOException {
    byte[] buffer = new byte[4];
    buffer[0] = (byte) (value & 0xff);
    buffer[1] = (byte) ((value >>> 8) & 0xff);
    buffer[2] = (byte) ((value >>> 16) & 0xff);
    buffer[3] = (byte) ((value >>> 24) & 0xff);
    randomAccessFile.seek(offset);
    randomAccessFile.write(buffer);
  }

  private static void writeLongLE(RandomAccessFile randomAccessFile, long offset, long value)
      throws IOException {
    byte[] buffer = new byte[8];
    buffer[0] = (byte) (value & 0xff);
    buffer[1] = (byte) ((value >>> 8) & 0xff);
    buffer[2] = (byte) ((value >>> 16) & 0xff);
    buffer[3] = (byte) ((value >>> 24) & 0xff);
    buffer[4] = (byte) ((value >>> 32) & 0xff);
    buffer[5] = (byte) ((value >>> 40) & 0xff);
    buffer[6] = (byte) ((value >>> 48) & 0xff);
    buffer[7] = (byte) ((value >>> 56) & 0xff);
    randomAccessFile.seek(offset);
    randomAccessFile.write(buffer);
  }

  private static int checkedInt(long value) throws IOException {
    if (value < 0 || value > 0xffffffffL) {
      throw new IOException("ZIP offset exceeds 32-bit range: " + value);
    }
    return (int) value;
  }
}
