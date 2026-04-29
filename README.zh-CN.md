# spring-boot-fat-jar-differ

中文说明。英文版请见 [README.md](README.md)。

`spring-boot-fat-jar-differ` 是一个面向 Java 8 产物的工具链项目，使用 Gradle 8.14.3 构建。它用于比较两个 Spring Boot 可执行归档，并生成一个可独立运行的补丁器 jar，用来把当前安装中的归档就地升级到目标版本。

项目当前发布在 `io.github.artern` 命名空间下，提供三类产物：

1. `spring-boot-fat-jar-differ.jar`：独立 CLI，对比 baseline 和 latest 归档并生成补丁器。
2. `patcher-<target-name>.jar`：由 CLI 或 Gradle 插件生成的可执行补丁器。
3. `io.github.artern.spring-boot-fat-jar-differ`：用于 Spring Boot 构建流程的 Gradle 插件。

## 支持的归档类型

- 由 `bootJar` 生成的 Spring Boot 可执行 JAR。
- 由 `bootWar` 生成的 Spring Boot 可执行 WAR。
- 包含 Spring Boot `launchScript()` 启动脚本前导区的归档。前导区会作为整体二进制块保存和升级。

## 补丁模型

- `META-INF/` 作为整棵树替换。
- `BOOT-INF/classes/` 作为整棵树替换。
- `WEB-INF/classes/` 作为整棵树替换。
- 其余条目按文件级别做新增、替换、删除。
- 生成的补丁包会在 `BOOT-PATCH/` 下保存 baseline 与 target 条目清单，以及启动脚本前导区信息。

这个实现优先保证重建安全性和校验确定性，而不是追求最小 payload。

## 项目结构

- `differ-core`：归档扫描、差分计划、补丁元数据、目标校验。
- `differ-cli`：CLI 入口，负责动态组装可执行补丁器。
- `patcher-runtime`：补丁器运行时模板。
- `gradle-plugin`：可发布的 Gradle 插件。
- `demo-spring-boot`：通过 `mavenLocal()` 消费插件的真实示例工程。

## 构建与格式化

构建面向 Java 8 的二进制产物：

```bash
./gradlew assembleBinaries
```

把下游 Gradle 和 Maven 消费方所需的工件发布到本地仓库：

```bash
./gradlew publishToolingToMavenLocal
```

发布坐标：

- `io.github.artern:differ-core:0.1.0`
- `io.github.artern:differ-cli:0.1.0`
- `io.github.artern:gradle-plugin:0.1.0`

使用 Spotless 进行格式化：

```bash
./gradlew spotlessApply
```

Spotless 与项目本身一样按 Java 8 约束来使用。这里显式锁定 `google-java-format` 1.7，以保持格式化工具链对 Java 8 运行环境的兼容性。

## CLI 使用方式

生成可执行补丁器：

```bash
java -jar differ-cli/build/libs/spring-boot-fat-jar-differ.jar ./baseline.jar ./latest.jar
```

如果不显式指定输出路径，CLI 默认会在当前目录生成 `patcher-<latest-archive-base>.jar`。

自定义输出路径：

```bash
java -jar differ-cli/build/libs/spring-boot-fat-jar-differ.jar ./baseline.jar ./latest.jar --output ./dist/custom-patcher.jar
```

执行补丁器：

```bash
java -jar ./patcher-my-app.jar ./current.jar ./backup
```

补丁执行流程：

1. 在指定备份目录生成带时间戳的备份文件。
2. 把目标归档重建到临时文件。
3. 校验条目元数据、逻辑区域以及启动脚本前导区。
4. 以原地替换的方式覆盖当前归档。

## Gradle 插件使用方式

先在 `pluginManagement` 中声明插件版本和仓库，让 Gradle 能从 `mavenLocal()` 解析本地发布的 plugin marker 与实现工件：

```groovy
pluginManagement {
    plugins {
        id 'io.github.artern.spring-boot-fat-jar-differ' version '0.1.0'
    }
    repositories {
        mavenLocal()
        maven { url 'https://maven.aliyun.com/repository/gradle-plugin/' }
        maven { url 'https://maven.aliyun.com/repository/public/' }
        maven { url 'https://maven.aliyun.com/repository/spring-plugin/' }
        maven { url 'https://maven.aliyun.com/repository/spring/' }
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id 'io.github.artern.spring-boot-fat-jar-differ'
}
```

可选配置：

```groovy
springBootFatJarDiffer {
    latestJar = layout.buildDirectory.file('libs/my-app.jar')
    baselineJar = layout.projectDirectory.file('.gradle/spring-boot-fat-jar-differ/my-app/baseline/last-release.jar')
}
```

如果没有自定义输出路径需求，可以不配置 `outputPatcher`，默认会生成标准命名的 `patcher-<latest-archive-base>.jar`。

执行任务：

```bash
./gradlew bootDiff
```

默认值：

- baseline 缓存位置：`.gradle/spring-boot-fat-jar-differ/<project-name>/baseline/last-release.<archive-extension>`
- patcher 输出位置：`build/fat-jar-differ/patcher-<latest-archive-base>.jar`

如果 baseline 归档不存在，任务会明确失败并提示应放置的位置。

对于仍使用老式 `buildscript` 方式的可执行 WAR 工程，可以在 `buildscript` classpath 中加入 `io.github.artern:gradle-plugin:0.1.0`，然后再 `apply plugin: 'io.github.artern.spring-boot-fat-jar-differ'`。

## Maven 集成方式

Maven 工程不能直接套用 Gradle 插件，应改为消费已发布的 CLI 工件。

通过 `spring-boot-fat-jar-differ` profile 实现：

1. baseline 缺失时明确失败。
2. 从 `mavenLocal()` 解析 `io.github.artern:differ-cli:0.1.0`。
3. 在 `verify` 阶段生成可执行补丁器。
4. 成功后刷新本地 baseline 缓存。

示例：

```bash
mvn verify -Pspring-boot-fat-jar-differ
```

## Demo

仓库内包含一个真实 Spring Boot 示例工程 `demo-spring-boot/`：

```bash
./gradlew -p demo-spring-boot bootJar
./gradlew -p demo-spring-boot bootDiff
```
