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
                  // Prefer WAR-producing tasks first so executable WAR overlays can
                  // patch the same artifact that is built and deployed.
                  if (isEnabledTask(project, "bootWar")) {
                    task.dependsOn("bootWar");
                  } else if (isEnabledTask(project, "bootJar")) {
                    task.dependsOn("bootJar");
                  } else if (isEnabledTask(project, "war")) {
                    task.dependsOn("war");
                  } else if (isEnabledTask(project, "jar")) {
                    task.dependsOn("jar");
                  }
                }));
  }

  private boolean isEnabledTask(Project project, String taskName) {
    return project.getTasks().findByName(taskName) != null
        && project.getTasks().getByName(taskName).getEnabled();
  }
}
