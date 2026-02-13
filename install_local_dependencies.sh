#!/bin/bash

curl_if_missing() {
    local src="$1"
    local dest="$2"
    local dir
    dir=$(dirname "$dest")
    local file
    file=$(basename "$dest")
    local temp=".gradle/.m2/$file.tmp"
    if [ ! -f "$dest" ] || [ ! -s "$dest" ]; then
        echo "Downloading $file"
        mkdir -p "$dir"
        curl -L "$src" -o "$temp"
        if [ -s "$temp" ]; then
            mv "$temp" "$dest"
        else
            echo "Download failed or file is empty: $file"
            rm -f "$temp"
        fi
    fi
}

# Download IntelliJ Platform Gradle Plugin POM and JAR files
# org.jetbrains.intellij.platform.gradle.plugin-2.11.0.pom
curl_if_missing "https://plugins.gradle.org/m2/org/jetbrains/intellij/platform/org.jetbrains.intellij.platform.gradle.plugin/2.11.0/org.jetbrains.intellij.platform.gradle.plugin-2.11.0.pom" ".gradle/.m2/org/jetbrains/intellij/platform/org.jetbrains.intellij.platform.gradle.plugin/2.11.0/org.jetbrains.intellij.platform.gradle.plugin-2.11.0.pom"
# intellij-platform-gradle-plugin-2.11.0.pom and .jar
curl_if_missing "https://plugins.gradle.org/m2/org/jetbrains/intellij/platform/intellij-platform-gradle-plugin/2.11.0/intellij-platform-gradle-plugin-2.11.0.pom" ".gradle/.m2/org/jetbrains/intellij/platform/intellij-platform-gradle-plugin/2.11.0/intellij-platform-gradle-plugin-2.11.0.pom"
curl_if_missing "https://plugins.gradle.org/m2/org/jetbrains/intellij/platform/intellij-platform-gradle-plugin/2.11.0/intellij-platform-gradle-plugin-2.11.0.jar" ".gradle/.m2/org/jetbrains/intellij/platform/intellij-platform-gradle-plugin/2.11.0/intellij-platform-gradle-plugin-2.11.0.jar"

# Download Changelog Gradle Plugin POM and JAR files
# org.jetbrains.changelog.gradle.plugin-2.5.0.pom
curl_if_missing "https://plugins.gradle.org/m2/org/jetbrains/changelog/org.jetbrains.changelog.gradle.plugin/2.5.0/org.jetbrains.changelog.gradle.plugin-2.5.0.pom" ".gradle/.m2/org/jetbrains/changelog/org.jetbrains.changelog.gradle.plugin/2.5.0/org.jetbrains.changelog.gradle.plugin-2.5.0.pom"
# gradle-changelog-plugin.pom and .jar
curl_if_missing "https://plugins.gradle.org/m2/org/jetbrains/intellij/plugins/gradle-changelog-plugin/2.5.0/gradle-changelog-plugin-2.5.0.pom" ".gradle/.m2/org/jetbrains/intellij/plugins/gradle-changelog-plugin/2.5.0/gradle-changelog-plugin-2.5.0.pom"
curl_if_missing "https://plugins.gradle.org/m2/org/jetbrains/intellij/plugins/gradle-changelog-plugin/2.5.0/gradle-changelog-plugin-2.5.0.jar" ".gradle/.m2/org/jetbrains/intellij/plugins/gradle-changelog-plugin/2.5.0/gradle-changelog-plugin-2.5.0.jar"

# Download Gradle Toolchains Resolver
# org.gradle.toolchains.foojay-resolver-convention.gradle.plugin-1.0.0.pom
curl_if_missing "https://plugins.gradle.org/m2/org/gradle/toolchains/foojay-resolver-convention/org.gradle.toolchains.foojay-resolver-convention.gradle.plugin/1.0.0/org.gradle.toolchains.foojay-resolver-convention.gradle.plugin-1.0.0.pom" ".gradle/.m2/org/gradle/toolchains/foojay-resolver-convention/org.gradle.toolchains.foojay-resolver-convention.gradle.plugin/1.0.0/org.gradle.toolchains.foojay-resolver-convention.gradle.plugin-1.0.0.pom"
# foojay-resolver-1.0.0.pom and .jar
curl_if_missing "https://plugins.gradle.org/m2/org/gradle/toolchains/foojay-resolver/1.0.0/foojay-resolver-1.0.0.pom" ".gradle/.m2/org/gradle/toolchains/foojay-resolver/1.0.0/foojay-resolver-1.0.0.pom"
curl_if_missing "https://plugins.gradle.org/m2/org/gradle/toolchains/foojay-resolver/1.0.0/foojay-resolver-1.0.0.jar" ".gradle/.m2/org/gradle/toolchains/foojay-resolver/1.0.0/foojay-resolver-1.0.0.jar"

# Download Rider Platform POM and ZIP files
curl_if_missing "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/rider/riderRD/2025.3.2/riderRD-2025.3.2.pom" ".gradle/.m2/com/jetbrains/intellij/rider/riderRD/2025.3.2/riderRD-2025.3.2.pom"
curl_if_missing "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/rider/riderRD/2025.3.2/riderRD-2025.3.2.zip" ".gradle/.m2/com/jetbrains/intellij/rider/riderRD/2025.3.2/riderRD-2025.3.2.zip"

# Download java-compiler-ant-tasks Maven metadata and artifacts
curl_if_missing "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/java/java-compiler-ant-tasks/maven-metadata.xml" ".gradle/.m2/com/jetbrains/intellij/java/java-compiler-ant-tasks/maven-metadata.xml"
curl_if_missing "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/java/java-compiler-ant-tasks/253.29346.145/java-compiler-ant-tasks-253.29346.145.pom" ".gradle/.m2/com/jetbrains/intellij/java/java-compiler-ant-tasks/253.29346.145/java-compiler-ant-tasks-253.29346.145.pom"
curl_if_missing "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/java/java-compiler-ant-tasks/253.29346.145/java-compiler-ant-tasks-253.29346.145.jar" ".gradle/.m2/com/jetbrains/intellij/java/java-compiler-ant-tasks/253.29346.145/java-compiler-ant-tasks-253.29346.145.jar"

# Download dependencies for java-compiler-ant-tasks-253.29346.145
curl_if_missing "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/java/java-gui-forms-compiler/253.29346.145/java-gui-forms-compiler-253.29346.145.pom" ".gradle/.m2/com/jetbrains/intellij/java/java-gui-forms-compiler/253.29346.145/java-gui-forms-compiler-253.29346.145.pom"
curl_if_missing "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/java/java-gui-forms-compiler/253.29346.145/java-gui-forms-compiler-253.29346.145.jar" ".gradle/.m2/com/jetbrains/intellij/java/java-gui-forms-compiler/253.29346.145/java-gui-forms-compiler-253.29346.145.jar"
curl_if_missing "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/java/java-gui-forms-rt/253.29346.145/java-gui-forms-rt-253.29346.145.pom" ".gradle/.m2/com/jetbrains/intellij/java/java-gui-forms-rt/253.29346.145/java-gui-forms-rt-253.29346.145.pom"
curl_if_missing "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/java/java-gui-forms-rt/253.29346.145/java-gui-forms-rt-253.29346.145.jar" ".gradle/.m2/com/jetbrains/intellij/java/java-gui-forms-rt/253.29346.145/java-gui-forms-rt-253.29346.145.jar"
curl_if_missing "https://packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/intellij/deps/asm-all/9.6.1/asm-all-9.6.1.pom" ".gradle/.m2/org/jetbrains/intellij/deps/asm-all/9.6.1/asm-all-9.6.1.pom"
curl_if_missing "https://packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/intellij/deps/asm-all/9.6.1/asm-all-9.6.1.jar" ".gradle/.m2/org/jetbrains/intellij/deps/asm-all/9.6.1/asm-all-9.6.1.jar"
curl_if_missing "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/java/java-compiler-instrumentation-util/253.29346.145/java-compiler-instrumentation-util-253.29346.145.pom" ".gradle/.m2/com/jetbrains/intellij/java/java-compiler-instrumentation-util/253.29346.145/java-compiler-instrumentation-util-253.29346.145.pom"
curl_if_missing "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/java/java-compiler-instrumentation-util/253.29346.145/java-compiler-instrumentation-util-253.29346.145.jar" ".gradle/.m2/com/jetbrains/intellij/java/java-compiler-instrumentation-util/253.29346.145/java-compiler-instrumentation-util-253.29346.145.jar"
curl_if_missing "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/java/java-compiler-instrumentation-util-java8/253.29346.145/java-compiler-instrumentation-util-java8-253.29346.145.pom" ".gradle/.m2/com/jetbrains/intellij/java/java-compiler-instrumentation-util-java8/253.29346.145/java-compiler-instrumentation-util-java8-253.29346.145.pom"
curl_if_missing "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/java/java-compiler-instrumentation-util-java8/253.29346.145/java-compiler-instrumentation-util-java8-253.29346.145.jar" ".gradle/.m2/com/jetbrains/intellij/java/java-compiler-instrumentation-util-java8/253.29346.145/java-compiler-instrumentation-util-java8-253.29346.145.jar"
curl_if_missing "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/platform/util-jdom/253.29346.145/util-jdom-253.29346.145.pom" ".gradle/.m2/com/jetbrains/intellij/platform/util-jdom/253.29346.145/util-jdom-253.29346.145.pom"
curl_if_missing "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/platform/util-jdom/253.29346.145/util-jdom-253.29346.145.jar" ".gradle/.m2/com/jetbrains/intellij/platform/util-jdom/253.29346.145/util-jdom-253.29346.145.jar"

# Download com.jetbrains.intellij.platform:test-framework Maven metadata and artifacts
curl_if_missing "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/platform/test-framework/maven-metadata.xml" ".gradle/.m2/com/jetbrains/intellij/platform/test-framework/maven-metadata.xml"
curl_if_missing "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/platform/test-framework/253.29346.145/test-framework-253.29346.145.pom" ".gradle/.m2/com/jetbrains/intellij/platform/test-framework/253.29346.145/test-framework-253.29346.145.pom"
curl_if_missing "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/platform/test-framework/253.29346.145/test-framework-253.29346.145.jar" ".gradle/.m2/com/jetbrains/intellij/platform/test-framework/253.29346.145/test-framework-253.29346.145.jar"
