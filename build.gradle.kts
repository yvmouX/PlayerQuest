plugins {
    java
    `maven-publish`
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
        // PlaceholderAPI 的官方仓库；用内容过滤限定只在此仓库找它，避免影响其它依赖的解析
        maven {
            url = uri("https://repo.extendedclip.com/content/repositories/placeholderapi/")
            content { includeGroup("me.clip") }
        }
    }

    dependencies {
        // 此坐标仅在没有 includeBuild 时生效（如独立构建/IDE 直接解析依赖）；
        // 版本号跟随当前 YLib 子模块所在分支的 gradle.properties。
        // 注意：settings.gradle.kts 中的 includeBuild("YLib") 会让 Gradle 用本地 YLib 源码
        // 替换此依赖，复合构建的依赖替换按 group:name 匹配、版本号不参与匹配，
        // 因此本地开发始终编译 YLib 源码，改这里不会切换实际使用的 YLib。
        implementation("com.github.yvmouX:YLib:1.0.0-beta10")

        compileOnly("org.spigotmc:spigot-api:1.21.8-R0.1-SNAPSHOT")

        compileOnly("org.jetbrains:annotations:24.0.1")

        // 文本渲染所需的 Adventure（MiniMessage + 两个序列化器）由 YLib 的 core 模块以 api
        // 依赖提供，这里不再重复声明：两处各写一份版本号迟早会漂移，而渲染实现已经统一在
        // YLib 的 TextRenderer 里，插件侧只消费它的结果。

        // 软依赖：编译期需要，运行期缺失时对应功能自动降级
        compileOnly("me.clip:placeholderapi:2.11.6")


        // 定义/玩家数据与网页编辑器的 JSON 编解码统一走 JsonCodec 的这一个 ObjectMapper
        implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
        // Javalin 6 通过 slf4j 输出日志；不提供实现时启动会打印 "No SLF4J providers" 警告
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

        // 保留方法参数名：YLib 的 @Arg 在未显式命名时按参数名匹配，依赖此选项。
        // （本项目仍一律显式写 @Arg("name")，此处作为双保险。）
        options.compilerArgs.add("-parameters")
    }
}


// 本机测试服由 start-folia.ps1 直接启动 run/ 下的服务端 jar。
// 原先用 run-paper 插件的 runServer 任务，但它会在每次运行时维护/替换服务端 jar，
// 与「自行维护 run/ 目录」的工作方式冲突，因此移除该插件。
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
    // Adventure 自带一份（Spigot 无此 API），重定位后与 Paper 自带的 net.kyori.adventure 互不干扰
    relocate("net.kyori", "com.playerPlugin.playerTaskX.libs.kyori")
    
    // 合并 META-INF/services 文件：
    // YLib 自 1.0.0-beta5 起改用 ServiceLoader 定位服务实现（Logger/配置/命令/调度器），
    // shadow 合并时会同步重写服务文件的路径与内容以匹配重定位后的类名，
    // 缺了它，重定位后的服务文件可能无法被 ServiceLoader 识别。
    // （YLib 自身另有"重定位安全回退"，因此这里是冗余保险而非硬需求。）
    mergeServiceFiles()
    
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


// 说明：plugin.yml 位于 core 模块，它的变量替换配在 core/build.gradle.kts 的
// :core:processResources 上。这里（root）曾经也写过一段同样的 filesMatching，
// 但 root 没有 src 目录，:processResources 恒为 NO-SOURCE，那段配置从未生效——
// 已删除，避免「看起来在替换、其实没有」的假象。