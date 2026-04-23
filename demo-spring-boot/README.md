# demo-spring-boot

This is a real Spring Boot sample project wired to the locally published `io.github.artern.spring-boot-fat-jar-differ` plugin through `mavenLocal()`.

## Build the fat jar

```bash
cd demo-spring-boot
../gradlew bootJar
```

## Generate a patcher jar

Place the previous release jar at:

```text
.gradle/spring-boot-fat-jar-differ/demo-spring-boot/baseline/last-release.jar
```

Then run:

```bash
../gradlew bootDiff
```

The generated patcher jar is written to:

```text
build/fat-jar-differ/patcher-demo-spring-boot.jar
```