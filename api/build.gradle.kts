dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.2")
    compileOnly("org.slf4j:slf4j-api:2.0.9")
    implementation("org.slf4j:slf4j-simple:2.0.16") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }

    compileOnly("com.googlecode.json-simple:json-simple:1.1.1") // TODO will remove
}