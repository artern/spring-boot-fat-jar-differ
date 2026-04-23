package io.github.artern.fatjar.differ.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Extracts the raw preamble that precedes the zip payload in an executable archive. */
final class ArchivePreambleSupport {

  private static final int PK = 0x50;
  private static final int K = 0x4b;
  private static final int LOCAL_FILE_HEADER_1 = 0x03;
  private static final int LOCAL_FILE_HEADER_2 = 0x04;

  private ArchivePreambleSupport() {}

  /** Reads bytes until the first local file header marker ({@code PK\003\004}). */
  static ArchivePreamble read(Path archive) throws IOException {
    ByteArrayOutputStream preamble = new ByteArrayOutputStream();
    try (InputStream inputStream = Files.newInputStream(archive)) {
      int state = 0;
      for (; ; ) {
        int next = inputStream.read();
        if (next < 0) {
          break;
        }
        // A tiny state machine lets us detect the first local-file header
        // without buffering the entire archive in memory.
        switch (state) {
          case 0:
            if (next == PK) {
              state = 1;
            } else {
              preamble.write(next);
            }
            break;
          case 1:
            if (next == K) {
              state = 2;
            } else {
              preamble.write(PK);
              if (next == PK) {
                state = 1;
              } else {
                preamble.write(next);
                state = 0;
              }
            }
            break;
          case 2:
            if (next == LOCAL_FILE_HEADER_1) {
              state = 3;
            } else {
              preamble.write(PK);
              preamble.write(K);
              if (next == PK) {
                state = 1;
              } else {
                preamble.write(next);
                state = 0;
              }
            }
            break;
          case 3:
            if (next == LOCAL_FILE_HEADER_2) {
              return new ArchivePreamble(preamble.toByteArray());
            }
            preamble.write(PK);
            preamble.write(K);
            preamble.write(LOCAL_FILE_HEADER_1);
            if (next == PK) {
              state = 1;
            } else {
              preamble.write(next);
              state = 0;
            }
            break;
          default:
            throw new IllegalStateException("Unexpected preamble scanner state: " + state);
        }
      }
    }
    return new ArchivePreamble(preamble.toByteArray());
  }
}
