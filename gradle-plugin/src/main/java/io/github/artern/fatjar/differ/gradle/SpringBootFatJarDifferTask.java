package io.github.artern.fatjar.differ.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

import io.github.artern.fatjar.differ.core.ExecutablePatchJarBuilder;

/**
 * Gradle task that turns the current build output plus a cached baseline archive into an executable
 * patcher jar.
 */
public abstract class SpringBootFatJarDifferTask extends DefaultTask {

  private SpringBootFatJarDifferExtension extension;

  @Internal
  public SpringBootFatJarDifferExtension getExtension() {
    return extension;
  }

  public void setExtension(SpringBootFatJarDifferExtension extension) {
    this.extension = extension;
  }

  /** Generates the patcher jar and refreshes the cached baseline after a successful build. */
  @TaskAction
  public void generate() throws IOException {
    Path latestJar = resolveLatestJar();
    Path baselineJar = resolveBaselineJar();
    Path outputPatcher = resolveOutputPatcher();

    if (!Files.exists(baselineJar)) {
      if (Boolean.TRUE.equals(extension.getFailIfMissingBaseline().getOrElse(Boolean.TRUE))) {
        throw new GradleException(
            "Baseline jar not found. Put the previous release jar at "
                + baselineJar
                + " or configure springBootFatJarDiffer.baselineJar");
      }
      getLogger().warn("Baseline jar does not exist, skipping patch generation: {}", baselineJar);
      return;
    }

    // The embedded runtime template keeps the plugin distribution small while
    // still emitting a self-contained patcher jar.
    Files.createDirectories(outputPatcher.getParent());
    ExecutablePatchJarBuilder builder = new ExecutablePatchJarBuilder();
    try (InputStream templateStream =
        SpringBootFatJarDifferTask.class.getResourceAsStream(
            "/templates/spring-boot-fat-jar-patcher-template.jar")) {
      if (templateStream == null) {
        throw new GradleException("Missing embedded patcher template resource");
      }
      builder.build(
          baselineJar,
          latestJar,
          templateStream,
          outputPatcher,
          getProject().getVersion().toString());
    }

    Files.createDirectories(baselineJar.getParent());
    Files.copy(latestJar, baselineJar, StandardCopyOption.REPLACE_EXISTING);
    getLogger().lifecycle("Generated patcher jar: {}", outputPatcher);
    getLogger().lifecycle("Updated baseline jar: {}", baselineJar);
  }

  private Path resolveLatestJar() {
    if (extension.getLatestJar().isPresent()) {
      return extension.getLatestJar().get().getAsFile().toPath().toAbsolutePath().normalize();
    }
    AbstractArchiveTask archiveTask = findArchiveTask();
    if (archiveTask != null) {
      return archiveTask.getArchiveFile().get().getAsFile().toPath().toAbsolutePath().normalize();
    }
    throw new GradleException(
        "Unable to resolve latest fat jar. Configure springBootFatJarDiffer.latestJar");
  }

  private Path resolveBaselineJar() {
    if (extension.getBaselineJar().isPresent()) {
      return extension.getBaselineJar().get().getAsFile().toPath().toAbsolutePath().normalize();
    }
    // Default the cache file extension to the same type as the produced
    // archive so executable WAR projects do not need extra configuration.
    AbstractArchiveTask archiveTask = findArchiveTask();
    String archiveExtension = "jar";
    if (archiveTask != null && archiveTask.getArchiveExtension().isPresent()) {
      archiveExtension = archiveTask.getArchiveExtension().get();
    }
    return extension
        .getBaselineDirectory()
        .file("last-release." + archiveExtension)
        .get()
        .getAsFile()
        .toPath()
        .toAbsolutePath()
        .normalize();
  }

  private Path resolveOutputPatcher() {
    if (extension.getOutputPatcher().isPresent()) {
      return extension.getOutputPatcher().get().getAsFile().toPath().toAbsolutePath().normalize();
    }
    String patcherFileName =
        "patcher-" + stripExtension(resolveLatestJar().getFileName().toString()) + ".jar";
    return getProject()
        .getLayout()
        .getBuildDirectory()
        .file("fat-jar-differ/" + patcherFileName)
        .get()
        .getAsFile()
        .toPath()
        .toAbsolutePath()
        .normalize();
  }

  private AbstractArchiveTask findArchiveTask() {
    Task bootJarTask = getProject().getTasks().findByName("bootJar");
    if (bootJarTask instanceof AbstractArchiveTask) {
      return (AbstractArchiveTask) bootJarTask;
    }
    Task bootWarTask = getProject().getTasks().findByName("bootWar");
    if (bootWarTask instanceof AbstractArchiveTask) {
      return (AbstractArchiveTask) bootWarTask;
    }
    Task jarTask = getProject().getTasks().findByName("jar");
    if (jarTask instanceof AbstractArchiveTask) {
      return (AbstractArchiveTask) jarTask;
    }
    return null;
  }

  private String stripExtension(String fileName) {
    int separator = fileName.lastIndexOf('.');
    if (separator <= 0) {
      return fileName;
    }
    return fileName.substring(0, separator);
  }
}
