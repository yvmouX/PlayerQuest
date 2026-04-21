plugins {
    java
    `maven-publish`
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("com.gradleup.shadow") version "9.3.0"
}


allprojects {
    group = "com.playerPlugin"
    version = "1.0.0"

    apply(plugin = "java")
    apply(plugin = "maven-publish")

    repositories {
        mavenCentral()
        maven { url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") }
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://repo.tcoded.com/releases") }
        maven { url = uri("https://repo.rosewooddev.io/repository/public/") }
    }

    dependencies {
        implementation("com.github.yvmouX:YLib:1.0.0-alpha.9")

        compileOnly("org.spigotmc:spigot-api:1.21.8-R0.1-SNAPSHOT")

        compileOnly("org.jetbrains:annotations:24.0.1")


        implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
        implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.2")
        compileOnly("org.slf4j:slf4j-api:2.0.9")
        implementation("org.slf4j:slf4j-simple:2.0.16") {
            exclude(group = "org.slf4j", module = "slf4j-api")
        }
    }

    val targetJavaVersion = 21
    java {
        val javaVersion = JavaVersion.toVersion(targetJavaVersion)
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion

        // 若当前 JDK 版本低于目标版本，使用 Toolchain 指定 JDK 版本
        if (JavaVersion.current() < javaVersion) {
            toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
        }
    }

    // Java 编译任务配置
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"

        // 针对 Java 10+ 启用 --release 参数
        if (targetJavaVersion >= 10 || JavaVersion.current().isJava10Compatible) {
            options.release.set(targetJavaVersion)
        }
    }
}


tasks {
    runServer {
        minecraftVersion("1.21.8")
        jvmArgs("-Dfile.encoding=UTF-8", "-Dsun.stdout.encoding=UTF-8", "-Dsun.stderr.encoding=UTF-8")
        // 确保运行前先构建前端
        dependsOn(project(":core").tasks.named("processResources"))
    }

    // 添加Folia支持
    runPaper.folia.registerTask()
}

tasks.shadowJar {
    // 依赖 core 模块的 jar 任务
    dependsOn(project(":core").tasks.named("jar"))
    
    // 将 core 模块的输出包含进来
    from(project(":core").sourceSets.main.get().output)
    
    // 包含 core 模块的运行时依赖
    configurations = listOf(project(":core").configurations.runtimeClasspath.get())

    // 重定位
    relocate("cn.yvmou.ylib", "com.playerPlugin.playerTaskX.lib.ylib")
    relocate("com.fasterxml.jackson", "com.playerPlugin.playerTaskX.libs.jackson")
    
    // 优雅地处理重复文件
    mergeServiceFiles() // 自动合并 META-INF/services 文件
    
    // 排除签名文件和重复的元数据文件
    exclude(
        "META-INF/*.SF",
        "META-INF/*.DSA",
        "META-INF/*.RSA",
        "META-INF/LICENSE",
        "META-INF/LICENSE.txt",
        "META-INF/NOTICE",
        "META-INF/NOTICE.txt"
    )
}


// 资源处理任务配置（替换 plugin.yml 中的版本变量）
tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filteringCharset = "UTF-8"

    // 对 plugin.yml 进行变量替换（例如 ${version} 替换为项目版本）
    filesMatching("plugin.yml") {
        expand(props)
    }
}