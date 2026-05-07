package io.github.artern.fatjar.differ.gradle;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;

/** User-configurable inputs and outputs for the Gradle differ task. */
public abstract class SpringBootFatJarDifferExtension {

  /**
   * Directory that stores the cached baseline archive when no explicit baseline file is configured.
   *
   * @return baseline cache directory property
   */
  public abstract DirectoryProperty getBaselineDirectory();

  /**
   * Explicit baseline archive used as the starting point for diff generation.
   *
   * @return baseline archive property
   */
  public abstract RegularFileProperty getBaselineJar();

  /**
   * Explicit latest archive to diff against. Defaults to bootWar, bootJar, war, or jar output.
   *
   * @return latest archive property
   */
  public abstract RegularFileProperty getLatestJar();

  /**
   * Output location of the generated executable patcher jar.
   *
   * @return output patcher property
   */
  public abstract RegularFileProperty getOutputPatcher();

  /**
   * Whether the task should fail instead of skipping when the baseline archive is missing.
   *
   * @return fail-on-missing-baseline flag property
   */
  public abstract Property<Boolean> getFailIfMissingBaseline();
}
