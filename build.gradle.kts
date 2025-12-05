plugins {
    java
    `maven-publish`
    id("xyz.jpenilla.run-paper") version "2.3.1"
    id("com.gradleup.shadow") version "9.0.0-rc3"
}


allprojects {
    group = "com.playerPlugin"
    version = "1.0.0"

    repositories {
        mavenCentral()
        maven { url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") }
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://repo.tcoded.com/releases") }
        maven { url = uri("https://repo.rosewooddev.io/repository/public/") }
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")

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

    dependencies {
        implementation(files(rootProject.file("lib/YLib-1.0.0-beta5.jar")))
        compileOnly("org.spigotmc:spigot-api:1.21.8-R0.1-SNAPSHOT")
    }
}

project(":core") {
    dependencies {
        // 依赖 api 模块
        implementation(project(":api"))

        compileOnly("com.github.MilkBowl:VaultAPI:1.7")
        compileOnly("org.black_ixx:playerpoints:3.3.4-SNAPSHOT")

        compileOnly("org.xerial:sqlite-jdbc:3.42.0.0")
        compileOnly("mysql:mysql-connector-java:8.0.33")
        compileOnly("com.googlecode.json-simple:json-simple:1.1.1") // TODO will remove

        implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2") // TODO will remove
        implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.2") // TODO will remove

        implementation("com.alibaba.fastjson2:fastjson2:2.0.60")

        implementation("io.javalin:javalin:6.7.0")
        implementation("io.javalin:javalin-bundle:5.6.1")


        compileOnly("org.slf4j:slf4j-api:2.0.9")
        implementation("org.slf4j:slf4j-simple:2.0.16") {
            exclude(group = "org.slf4j", module = "slf4j-api")
        }
    }
}

project(":api") {
}



tasks {
    runServer {
        minecraftVersion("1.21")
        jvmArgs("-Dfile.encoding=UTF-8", "-Dsun.stdout.encoding=UTF-8", "-Dsun.stderr.encoding=UTF-8")
    }
}

tasks.shadowJar {
    // 依赖 core 模块的 jar 任务
    dependsOn(project(":core").tasks.named("jar"))
    
    // 将 core 模块的输出包含进来
    from(project(":core").sourceSets.main.get().output)
    
    // 包含 core 模块的运行时依赖
    configurations = listOf(project(":core").configurations.runtimeClasspath.get())
    
    relocate("cn.yvmou.ylib", "com.playerPlugin.playerTaskX.lib.ylib")
    
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