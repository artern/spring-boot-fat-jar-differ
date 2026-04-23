package io.github.artern.fatjar.differ.gradle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.UnexpectedBuildFailure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringBootFatJarDifferPluginTest {

  @TempDir Path tempDir;

  @Test
  void failsWhenBaselineJarIsMissing() throws Exception {
    Path projectDir = prepareProject(false);

    UnexpectedBuildFailure failure =
        assertThrows(UnexpectedBuildFailure.class, () -> runner(projectDir).build());

    assertTrue(failure.getMessage().contains("Baseline jar not found"));
  }

  @Test
  void generatesPatcherAndUpdatesBaseline() throws Exception {
    Path projectDir = prepareProject(true);

    BuildResult result = runner(projectDir).build();
    assertEquals(SUCCESS, result.task(":bootDiff").getOutcome());

    Path patcher = projectDir.resolve("build/fat-jar-differ/patcher-latest.jar");
    Path baseline =
        projectDir.resolve(
            ".gradle/spring-boot-fat-jar-differ/sample-app/baseline/last-release.jar");
    Path latest = projectDir.resolve("fixtures/latest.jar");

    assertTrue(Files.exists(patcher));
    assertArrayEquals(Files.readAllBytes(latest), Files.readAllBytes(baseline));
  }

  private Path prepareProject(boolean withBaseline) throws Exception {
    Path projectDir = tempDir.resolve(withBaseline ? "with-baseline" : "without-baseline");
    Files.createDirectories(projectDir);
    Files.write(
        projectDir.resolve("settings.gradle"),
        "rootProject.name = 'sample-app'\n".getBytes(StandardCharsets.UTF_8));
    Files.write(
        projectDir.resolve("build.gradle"),
        ("plugins {\n"
                + "    id 'io.github.artern.spring-boot-fat-jar-differ'\n"
                + "}\n"
                + "version = '1.0.0'\n"
                + "springBootFatJarDiffer {\n"
                + "    latestJar = layout.projectDirectory.file('fixtures/latest.jar')\n"
                + "}\n")
            .getBytes(StandardCharsets.UTF_8));

    Path fixturesDir = projectDir.resolve("fixtures");
    Files.createDirectories(fixturesDir);
    writeJar(fixturesDir.resolve("latest.jar"), targetEntries());
    if (withBaseline) {
      Path baselineDir =
          projectDir.resolve(".gradle/spring-boot-fat-jar-differ/sample-app/baseline");
      Files.createDirectories(baselineDir);
      writeJar(baselineDir.resolve("last-release.jar"), baselineEntries());
    }
    return projectDir;
  }

  private GradleRunner runner(Path projectDir) {
    return GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("bootDiff");
  }

  private Map<String, String> baselineEntries() {
    Map<String, String> entries = new LinkedHashMap<String, String>();
    entries.put("META-INF/", null);
    entries.put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nMain-Class: sample.Old\n");
    entries.put("BOOT-INF/", null);
    entries.put("BOOT-INF/classes/", null);
    entries.put("BOOT-INF/classes/sample/App.class", "old-app");
    entries.put("BOOT-INF/lib/dependency.jar", "dep");
    entries.put("application.properties", "mode=old\n");
    return entries;
  }

  private Map<String, String> targetEntries() {
    Map<String, String> entries = new LinkedHashMap<String, String>();
    entries.put("META-INF/", null);
    entries.put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nMain-Class: sample.New\n");
    entries.put("BOOT-INF/", null);
    entries.put("BOOT-INF/classes/", null);
    entries.put("BOOT-INF/classes/sample/App.class", "new-app");
    entries.put("BOOT-INF/lib/dependency.jar", "dep");
    entries.put("application.properties", "mode=new\n");
    return entries;
  }

  private void writeJar(Path jarFile, Map<String, String> entries) throws IOException {
    try (ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(jarFile))) {
      for (Map.Entry<String, String> entry : entries.entrySet()) {
        outputStream.putNextEntry(new ZipEntry(entry.getKey()));
        if (entry.getValue() != null) {
          outputStream.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
        }
        outputStream.closeEntry();
      }
    }
  }
}
