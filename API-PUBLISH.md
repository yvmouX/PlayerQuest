# API 发布指南

## 📦 发布到本地 Maven 仓库

### 命令

```bash
# Windows (PowerShell/CMD)
gradlew :api:publishToMavenLocal

# Linux/Mac
./gradlew :api:publishToMavenLocal
```

### 发布位置

API 将被发布到本地 Maven 仓库：
- Windows: `C:\Users\<用户名>\.m2\repository\com\playerPlugin\playertaskx-api\1.0.0\`
- Linux/Mac: `~/.m2/repository/com/playerPlugin/playertaskx-api/1.0.0/`

### 生成的文件

- `playertaskx-api-1.0.0.jar` - 主 JAR 文件
- `playertaskx-api-1.0.0-sources.jar` - 源代码 JAR
- `playertaskx-api-1.0.0-javadoc.jar` - Javadoc 文档
- `playertaskx-api-1.0.0.pom` - Maven POM 文件

## 🌐 发布到远程 Maven 仓库

### 1. 配置远程仓库

编辑 `api/build.gradle.kts`，取消注释远程仓库配置：

```kotlin
publishing {
    repositories {
        maven {
            url = uri("https://your-repo-url")
            credentials {
                username = project.findProperty("repoUsername") as String?
                password = project.findProperty("repoPassword") as String?
            }
        }
    }
}
```

### 2. 配置凭据

在项目根目录创建 `gradle.properties`：

```properties
repoUsername=your_username
repoPassword=your_password
```

**重要**: 将 `gradle.properties` 添加到 `.gitignore`，避免泄露凭据！

### 3. 发布

```bash
gradlew :api:publish
```

## 📋 其他插件如何使用

### 从本地 Maven 仓库依赖

其他插件的 `build.gradle.kts`:

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    compileOnly("com.playerPlugin:playertaskx-api:1.0.0")
}
```

### 从远程仓库依赖

```kotlin
repositories {
    maven { url = uri("https://your-repo-url") }
    mavenCentral()
}

dependencies {
    compileOnly("com.playerPlugin:playertaskx-api:1.0.0")
}
```

## 🔨 构建完整插件

构建包含 API 的完整 PlayerTaskX 插件：

```bash
gradlew shadowJar
```

生成的文件位于：`build/libs/playerTaskX-1.0.0-all.jar`

## ✅ 验证发布

检查本地 Maven 仓库是否包含 API：

### Windows (PowerShell)
```powershell
Test-Path "$env:USERPROFILE\.m2\repository\com\playerPlugin\playertaskx-api\1.0.0\playertaskx-api-1.0.0.jar"
```

### Linux/Mac
```bash
ls -la ~/.m2/repository/com/playerPlugin/playertaskx-api/1.0.0/
```

## 📚 版本管理

更新版本号：编辑 `build.gradle.kts`:

```kotlin
allprojects {
    group = "com.playerPlugin"
    version = "1.0.1"  // 修改这里
}
```

## 🎯 最佳实践

1. **语义化版本**: 使用 `主版本.次版本.修订号` (如 1.0.0)
2. **向后兼容**: API 修改时注意保持向后兼容
3. **文档更新**: 每次 API 更改都更新 API-USAGE.md
4. **变更日志**: 维护 CHANGELOG.md 记录每个版本的变化
5. **标签发布**: 每次发布都在 Git 中打标签

```bash
git tag -a v1.0.0 -m "Release version 1.0.0"
git push origin v1.0.0
```
