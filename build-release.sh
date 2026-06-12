#!/bin/bash
# Build PLAIN release APK
# Usage: ./build-release.sh

set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"

echo "🔨 Building PLAIN Release APK..."
export JAVA_HOME=~/jdk17
export ANDROID_HOME=~/android

./gradlew assembleRelease

APK="$DIR/app/build/outputs/apk/release/app-release.apk"
if [ -f "$APK" ]; then
    SIZE=$(du -h "$APK" | cut -f1)
    echo "✅ Release APK: $APK ($SIZE)"
else
    echo "❌ APK not found"
    exit 1
fi
