# 项目需求与实现计划

这份文档不是过程记录，而是项目接手说明。目标读者是假设第一次进入这个仓库的任何人，或任何 AI 工具。看完后，应该能够快速理解这个项目的真正目标、不能破坏的约束、该从哪里动手，以及改完后如何验证。

## 1. 原始诉求（2026-04-22）

这个项目要解决的不是“做一个通用二进制 diff 工具”，而是：

把两个 Spring Boot 可执行归档之间的版本升级，表达成一个可分发、可执行、可校验的 patcher jar。

更具体地说：

1. 输入是旧版本归档和新版本归档。
2. 输出不是普通补丁文件，而是一个可以直接运行的 `patcher-<app-name>.jar`。
3. patcher 在目标机器上运行时，只需要：
   - 当前已安装归档。
   - patcher 本身。
   - 一个可写备份目录。
4. patcher 必须先校验 baseline，再重建目标归档，再做目标校验，最后原地替换。

一句话总结：

这个仓库的核心价值，是把 Spring Boot 可执行归档升级，封装成“结构感知 + 可独立执行 + 可校验”的补丁流程。

## 2. 这是什么，不是什么

### 它是什么

- 一个面向 Spring Boot 可执行归档的结构化差分工具。
- 一个可独立运行的补丁器生成器。
- 一个可嵌入 Gradle 构建流程的插件。
- 一个可被 Maven 项目间接复用的 CLI 工件。

### 它不是什么

- 不是通用 jar diff 平台。
- 不是纯字节级 patch 系统。
- 不是只服务单一项目的脚本堆。
- 不是只能处理 `bootJar` 的工具，`bootWar` 和 `launchScript()` 也是明确支持范围。

## 3. 当前必须满足的硬约束

这些约束不是建议，而是接手时默认不能破坏的前提：

1. Java 目标版本仍然是 1.8。
2. 构建工具仍然是 Gradle 8.14.3。
3. 生成的 patcher jar 必须可独立运行。
4. 必须同时支持两条使用路径：
   - 独立 CLI。
   - Gradle 插件。
5. Maven 项目不能直接使用 Gradle 插件，必须通过已发布的 CLI 接入。
6. 必须支持三类可执行归档形态：
   - `bootJar`
   - `bootWar`
   - 带 `launchScript()` 前导区的归档
7. 本地消费方式以 `mavenLocal()` 为准，不再依赖 `includeBuild`。
8. 格式化工具使用 Spotless，并且当前配置需要在 Java 8 环境下可运行。

## 4. 项目架构，一页看懂

### `differ-core`

这是最重要的模块。它决定“差分是什么”和“校验是什么”。

职责：

- 扫描可执行归档。
- 抽取前导区（launch script preamble）。
- 按逻辑区域和普通条目构建快照。
- 生成 patch manifest。
- 负责 baseline/target 校验。

优先阅读文件：

- `SpringBootFatJarScanner`
- `JarDiffEngine`
- `ExecutablePatchJarBuilder`
- `PatchMetadataIO`
- `TargetJarValidator`

### `differ-cli`

职责很单纯：

- 解析命令行参数。
- 调用 core 构建 patcher。
- 输出摘要信息和结构化 diff 明细。

### `patcher-runtime`

这是 patcher 真正执行升级的运行时。

职责：

- 从 patcher 自身读取 manifest 和 payload。
- 先恢复 archive preamble，再写入 zip 内容。
- 先校验 baseline，再校验 target。

### `gradle-plugin`

职责：

- 暴露 `springBootFatJarDiffer` 扩展。
- 自动寻找 `bootJar`、`bootWar` 或 `jar` 产物。
- 生成 patcher。
- 更新 baseline 缓存。

### `demo-spring-boot`

这是一个真实示例，不是摆设。它的价值是验证发布后的消费路径，而不是展示语法。

## 5. 设计红线，不要轻易改

以下规则是当前实现成立的基础。如果要改，必须连带更新测试和文档。

### 5.1 差分不是全文件平铺比较

以下区域是按整棵树替换的：

- `META-INF/`
- `BOOT-INF/classes/`
- `WEB-INF/classes/`

其他条目才是逐文件新增、替换、删除。

这是有意的结构化设计，不是偷懒。

### 5.2 launch script preamble 是一等公民

可执行 Spring Boot 归档可能在 zip 载荷前面带有脚本前导区。这个前导区必须：

- 被扫描出来。
- 被写入 patch payload。
- 在补丁应用时优先恢复。
- 参与目标校验。

如果未来有人把它退化成“忽略前导区”，那就是功能回退。

### 5.3 patcher 必须先校验 baseline

patcher 不能直接对任意当前归档执行升级。必须先确认当前归档就是生成补丁时的 baseline。否则应直接失败，而不是尝试修补。

### 5.4 target 校验必须覆盖结构

当前校验会看：

- entry count
- entry CRC 汇总
- target entry manifest
- logical area fingerprint
- archive preamble hash 和长度

不要把它简化成单一校验项。

### 5.5 baseline 缓存跟随归档扩展名

Gradle 插件默认 baseline 文件名不是写死成 `.jar`，而是跟随当前归档扩展名。这是为了支持 executable WAR。

## 6. 本地开发时最常用的命令

### 构建

```bash
./gradlew assembleBinaries
```

### 发布本地工件

```bash
./gradlew publishToolingToMavenLocal
```

### 格式化

```bash
./gradlew spotlessApply
./gradlew spotlessCheck
```

### 核心验证

```bash
./gradlew :differ-core:test
./gradlew :patcher-runtime:test
./gradlew :gradle-plugin:test
```

### Demo 验证

```bash
./gradlew -p demo-spring-boot bootJar
./gradlew -p demo-spring-boot bootDiff
```

## 7. 你要改什么，就从哪里开始

这是给 AI 或首次接手者的快速路由。

### 如果你要改差分规则

先看：

- `LogicalArea`
- `SpringBootFatJarScanner`
- `JarDiffEngine`
- `TargetJarValidator`

### 如果你要改 patcher 包内容

先看：

- `ExecutablePatchJarBuilder`
- `PatchMetadataIO`
- `PatchApplier`

### 如果你要改 CLI 行为

先看：

- `DifferMain`
- `PatcherMain`

### 如果你要改 Gradle 接入体验

先看：

- `SpringBootFatJarDifferPlugin`
- `SpringBootFatJarDifferTask`
- `SpringBootFatJarDifferExtension`

### 如果你要改发布/消费链路

先看：

- 根 `build.gradle`
- `gradle-plugin/build.gradle`
- `differ-core/build.gradle`
- `differ-cli/build.gradle`

## 8. 当前真实状态

截至现在，这个项目已经具备以下能力：

1. 能处理 Spring Boot executable JAR。
2. 能处理 Spring Boot executable WAR。
3. 能处理 `launchScript()` 前导区。
4. 能生成可执行 patcher jar。
5. Gradle 消费方通过 `mavenLocal()` 使用插件。
6. Maven 消费方通过已发布的 CLI 工件接入。
7. 核心代码、CLI、patcher runtime、Gradle 插件已经补充了较充分的注释。
8. 仓库已经接入 Spotless。

## 9. 接手时最容易犯的错

1. 把它误当成普通 jar diff 工具，进而试图改成纯字节级 patch。
2. 忽略 launch script preamble，只处理 zip 内部条目。
3. 修改 Gradle 插件时只考虑 `bootJar`，忘记 `bootWar`。
4. 继续使用 `includeBuild`，而不是 `mavenLocal()` 发布消费链路。
5. 改了差分规则但不更新测试和 README。
6. 只看生成逻辑，不看校验逻辑，最后造成 patcher 可生成但不可安全执行。

## 10. 接手时推荐的 vibecoding 工作方式

推荐顺序：

1. 先确认你改动落在哪个模块。
2. 先读该模块的入口类和对应测试。
3. 先做最小改动，不要同时改差分规则、运行时和发布链。
4. 第一处实质编辑后，立即跑对应的窄验证。
5. 如果改动影响用户使用方式，同时更新 README。
6. 如果改动影响架构边界或约束，同时更新这份文档。

## 11. 未来最值得继续做的方向

这些是值得继续扩展的方向，但都不属于“接手后必须先改”的内容：

1. patch manifest 的格式演进与升级说明。
2. 更多 executable WAR 样例与更复杂 overlay 场景。
3. 远程仓库发布流程、签名和 release profile。
4. 更完整的端到端文档与测试说明。
