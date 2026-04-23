package io.github.artern.fatjar.differ.gradle;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;

/** User-configurable inputs and outputs for the Gradle differ task. */
public abstract class SpringBootFatJarDifferExtension {

  /**
   * Directory that stores the cached baseline archive when no explicit baseline file is configured.
   */
  public abstract DirectoryProperty getBaselineDirectory();

  /** Explicit baseline archive used as the starting point for diff generation. */
  public abstract RegularFileProperty getBaselineJar();

  /** Explicit latest archive to diff against. Defaults to bootJar, bootWar, or jar output. */
  public abstract RegularFileProperty getLatestJar();

  /** Output location of the generated executable patcher jar. */
  public abstract RegularFileProperty getOutputPatcher();

  /** Whether the task should fail instead of skipping when the baseline archive is missing. */
  public abstract Property<Boolean> getFailIfMissingBaseline();
}
