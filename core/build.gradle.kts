dependencies {
    // 依赖 api 模块
    implementation(project(":api"))

    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
    compileOnly("org.black_ixx:playerpoints:3.3.4-SNAPSHOT")

    compileOnly("org.xerial:sqlite-jdbc:3.42.0.0")
    compileOnly("mysql:mysql-connector-java:8.0.33")
    compileOnly("com.googlecode.json-simple:json-simple:1.1.1") // TODO will remove

    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.2")

    implementation("com.alibaba.fastjson2:fastjson2:2.0.60")

    implementation("io.javalin:javalin:6.7.0")
    val openapi = "6.7.0"
    annotationProcessor("io.javalin.community.openapi:openapi-annotation-processor:$openapi")
    // for /openapi route with JSON scheme
    implementation("io.javalin.community.openapi:javalin-openapi-plugin:${openapi}")
    // for Swagger UI
    implementation("io.javalin.community.openapi:javalin-swagger-plugin:${openapi}")
    // for ReDoc UI
    implementation("io.javalin.community.openapi:javalin-redoc-plugin:${openapi}")

    compileOnly("org.slf4j:slf4j-api:2.0.9")
    implementation("org.slf4j:slf4j-simple:2.0.16") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
}