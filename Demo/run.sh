#!/bin/bash
# run.sh - Build and run with full dependencies (no custom Gradle task needed)

echo "Building project..."
./gradlew clean build --quiet || { echo "Build failed"; exit 1; }

echo "Starting application..."

JAR="app/build/libs/app-1.0-SNAPSHOT.jar"

# This gets the runtime classpath by resolving dependencies
CP=$(./gradlew -q dependencies --configuration runtimeClasspath \
     | grep '^runtimeClasspath - Runtime classpath of source set' -A 1000 \
     | grep -o '/.*\.jar' \
     | tr '\n' ':' \
     | sed 's/:$//')

if [ -z "$CP" ]; then
  echo "Warning: Could not automatically detect classpath. Falling back to manual paths."
  # Fallback: common locations for dependencies
  CP="$HOME/.gradle/caches/modules-2/files-2.1/com.fazecast/jSerialComm/**/jSerialComm-*.jar:$HOME/.gradle/caches/modules-2/files-2.1/org.json/json/**/json-*.jar"
fi

# Run the app
java -cp "$JAR:$CP" org.yourcompany.yourproject.Main