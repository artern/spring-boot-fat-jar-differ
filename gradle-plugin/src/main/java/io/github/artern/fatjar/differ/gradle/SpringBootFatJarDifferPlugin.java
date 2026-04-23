package io.github.artern.fatjar.differ.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;

/** Registers the differ extension and task for Gradle-based Spring Boot projects. */
public final class SpringBootFatJarDifferPlugin implements Plugin<Project> {

  @Override
  public void apply(Project project) {
    SpringBootFatJarDifferExtension extension =
        project
            .getExtensions()
            .create("springBootFatJarDiffer", SpringBootFatJarDifferExtension.class);
    extension
        .getBaselineDirectory()
        .convention(
            project
                .getLayout()
                .getProjectDirectory()
                .dir(".gradle/spring-boot-fat-jar-differ/" + project.getName() + "/baseline"));
    extension.getFailIfMissingBaseline().convention(Boolean.TRUE);

    TaskProvider<SpringBootFatJarDifferTask> taskProvider =
        project
            .getTasks()
            .register(
                "bootDiff",
                SpringBootFatJarDifferTask.class,
                task -> {
                  task.setGroup("build");
                  task.setDescription(
                      "Generates an executable patcher jar for a Spring Boot executable archive");
                  task.setExtension(extension);
                });

    project.afterEvaluate(
        ignored ->
            taskProvider.configure(
                task -> {
                  // Follow Spring Boot's conventional archive-producing tasks first, then
                  // fall back to the plain jar task when used outside Spring Boot.
                  if (project.getTasks().findByName("bootJar") != null) {
                    task.dependsOn("bootJar");
                  } else if (project.getTasks().findByName("bootWar") != null) {
                    task.dependsOn("bootWar");
                  } else if (project.getTasks().findByName("jar") != null) {
                    task.dependsOn("jar");
                  }
                }));
  }
}
