package io.github.artern.fatjar.differ.cli;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.github.artern.fatjar.differ.core.ExecutablePatchJarBuilder;
import io.github.artern.fatjar.differ.core.JarDiffEngine;
import io.github.artern.fatjar.differ.core.JarDiffPlan;
import io.github.artern.fatjar.differ.core.JarDiffReportFormatter;

/**
 * Command-line entrypoint that compares two executable Spring Boot archives and emits an executable
 * patcher jar.
 */
public final class DifferMain {

  private DifferMain() {}

  /** Usage: {@code spring-boot-fat-jar-differ.jar <baseline> <latest> [output]}. */
  public static void main(String[] args) throws Exception {
    if (args.length < 2 || args.length > 4) {
      printUsageAndExit();
    }

    Path baselineJar = Paths.get(args[0]).toAbsolutePath().normalize();
    Path latestJar = Paths.get(args[1]).toAbsolutePath().normalize();

    requireFile(baselineJar, "Baseline jar");
    requireFile(latestJar, "Latest jar");
    Path outputJar = resolveOutput(args, latestJar);

    ExecutablePatchJarBuilder builder = new ExecutablePatchJarBuilder();
    JarDiffPlan plan;
    String toolVersion = resolveToolVersion();
    System.out.println("Baseline archive: " + baselineJar);
    System.out.println("Latest archive: " + latestJar);
    System.out.println("Output patcher: " + outputJar);
    System.out.println("Differ version: " + toolVersion);
    try (InputStream templateStream =
        DifferMain.class.getResourceAsStream(
            "/templates/spring-boot-fat-jar-patcher-template.jar")) {
      if (templateStream == null) {
        throw new IllegalStateException("Missing embedded patcher template resource");
      }
      plan = builder.build(baselineJar, latestJar, templateStream, outputJar, toolVersion);
    }

    JarDiffEngine diffEngine = new JarDiffEngine();
    JarDiffReportFormatter reportFormatter = new JarDiffReportFormatter();
    System.out.println("Patcher jar created: " + outputJar);
    System.out.println("Diff summary: " + diffEngine.summarize(plan));
    System.out.println("Operations:");
    for (String line : reportFormatter.format(plan)) {
      System.out.println(line);
    }
  }

  private static void requireFile(Path file, String description) {
    if (!Files.isRegularFile(file)) {
      throw new IllegalArgumentException(description + " does not exist: " + file);
    }
  }

  private static Path resolveOutput(String[] args, Path latestJar) {
    if (args.length == 2) {
      return Paths.get(defaultPatcherName(latestJar)).toAbsolutePath().normalize();
    }
    // Support both the simple positional output argument and the more explicit
    // --output form so the CLI stays friendly in scripts.
    if (args.length == 3) {
      return Paths.get(args[2]).toAbsolutePath().normalize();
    }
    if ("--output".equals(args[2])) {
      return Paths.get(args[3]).toAbsolutePath().normalize();
    }
    printUsageAndExit();
    throw new IllegalStateException("Unreachable");
  }

  private static String defaultPatcherName(Path latestJar) {
    String fileName = latestJar.getFileName().toString();
    int separator = fileName.lastIndexOf('.');
    String baseName = separator > 0 ? fileName.substring(0, separator) : fileName;
    return "patcher-" + baseName + ".jar";
  }

  private static String resolveToolVersion() {
    Package currentPackage = DifferMain.class.getPackage();
    if (currentPackage != null && currentPackage.getImplementationVersion() != null) {
      return currentPackage.getImplementationVersion();
    }
    return "development";
  }

  private static void printUsageAndExit() {
    System.err.println(
        "Usage: java -jar spring-boot-fat-jar-differ.jar <baseline-fat-jar> <latest-fat-jar> [output-patcher-jar]");
    System.err.println(
        "   or: java -jar spring-boot-fat-jar-differ.jar <baseline-fat-jar> <latest-fat-jar> --output <output-patcher-jar>");
    System.exit(1);
  }
}
