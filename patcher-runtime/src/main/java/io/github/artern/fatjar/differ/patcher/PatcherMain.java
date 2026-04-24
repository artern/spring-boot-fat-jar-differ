package io.github.artern.fatjar.differ.patcher;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import io.github.artern.fatjar.differ.core.PatchManifest;

/** Executable runtime entrypoint embedded into the generated patcher jar. */
public final class PatcherMain {

  private static final DateTimeFormatter BACKUP_SUFFIX =
      DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

  private PatcherMain() {}

  /**
   * Applies the embedded patch to the current archive after creating a backup in the supplied
   * directory.
   */
  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      System.err.println(
          "Usage: java -jar spring-boot-fat-jar-patcher.jar <current-fat-jar> <backup-dir>");
      System.exit(1);
    }

    Path currentJar = Paths.get(args[0]).toAbsolutePath().normalize();
    Path backupDirectory = Paths.get(args[1]).toAbsolutePath().normalize();
    if (!Files.exists(currentJar)) {
      throw new IllegalArgumentException("Current jar does not exist: " + currentJar);
    }
    Files.createDirectories(backupDirectory);

    Path selfJar = resolveSelfJar();
    System.out.println("Patch bundle: " + selfJar);
    System.out.println("Current archive: " + currentJar);
    System.out.println("Backup directory: " + backupDirectory);
    Path backupFile =
        backupDirectory.resolve(
            currentJar.getFileName().toString()
                + "."
                + BACKUP_SUFFIX.format(LocalDateTime.now())
                + ".bak");
    Files.copy(currentJar, backupFile, StandardCopyOption.REPLACE_EXISTING);

    // Patch into a temporary sibling file first so a failed patch never leaves
    // the installed archive half-written.
    Path tempFile =
        Files.createTempFile(
            currentJar.getParent(), currentJar.getFileName().toString() + ".", ".patched");
    PatchApplier patchApplier = new PatchApplier(System.out::println);
    PatchManifest patchManifest = null;
    try {
      patchManifest = patchApplier.apply(currentJar, selfJar, tempFile);
      Files.delete(currentJar);
      moveIntoPlace(tempFile, currentJar);
    } catch (Exception exception) {
      Files.deleteIfExists(tempFile);
      throw exception;
    }

    System.out.println("Backup created at: " + backupFile);
    System.out.println("Patched jar replaced in-place: " + currentJar);
  }

  private static Path resolveSelfJar() throws URISyntaxException {
    Path self =
        Paths.get(PatcherMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    if (!Files.isRegularFile(self)) {
      throw new IllegalStateException("Patcher must run from an executable jar file: " + self);
    }
    return self;
  }

  /** Prefer an atomic replacement when the filesystem supports it. */
  private static void moveIntoPlace(Path source, Path target) throws IOException {
    try {
      Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException exception) {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
