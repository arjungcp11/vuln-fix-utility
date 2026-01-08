#!/bin/bash

#############################################
# CONFIGURATION
#############################################

UTILITY_JAR="vuln-fix-utility-1.3.0.jar"
FIXED_PROJECTS_DIR="/d/fixed-projects"   # Git Bash path for D:/fixed-projects
LOG_DIR="$FIXED_PROJECTS_DIR/build-logs"

#############################################
# SAFETY CHECK
#############################################

if [ ! -f "$UTILITY_JAR" ]; then
  echo "❌ JAR not found: $UTILITY_JAR"
  exit 1
fi

mkdir -p "$LOG_DIR"

echo "=============================================="
echo " Starting Vulnerability Fix Utility"
echo "=============================================="

#############################################
# STEP 1: RUN SPRING BOOT UTILITY
#############################################

java -jar "$UTILITY_JAR"

if [ $? -ne 0 ]; then
  echo "❌ Utility execution failed. Exiting."
  exit 1
fi

echo "✅ Utility completed successfully"
echo ""

#############################################
# STEP 2: LIST ALL PROJECTS (MAVEN / GRADLE)
#############################################

echo "=============================================="
echo " Detected Projects"
echo "=============================================="

projects=()

for dir in "$FIXED_PROJECTS_DIR"/*; do
  if [ -d "$dir" ]; then
    if [ -f "$dir/pom.xml" ]; then
      echo "MAVEN  : $(basename "$dir")"
      projects+=("$dir")
    elif [ -f "$dir/build.gradle" ] || [ -f "$dir/build.gradle.kts" ]; then
      echo "GRADLE : $(basename "$dir")"
      projects+=("$dir")
    fi
  fi
done

echo ""

#############################################
# STEP 3: BUILD PROJECTS
#############################################

echo "=============================================="
echo " Building Projects"
echo "=============================================="

for project in "${projects[@]}"; do

  projectName=$(basename "$project")
  issueFile="$FIXED_PROJECTS_DIR/${projectName}-issues.txt"

  echo "----------------------------------------------"
  echo " Building: $projectName"
  echo "----------------------------------------------"

  cd "$project" || continue

  ###########################################
  # MAVEN PROJECT
  ###########################################
  if [ -f "pom.xml" ]; then
    mvn clean install > "$LOG_DIR/$projectName.log" 2>&1

    if [ $? -ne 0 ]; then
      echo "❌ Maven build failed for $projectName"
      echo "Maven build failed for $projectName" > "$issueFile"
      echo "See log: $LOG_DIR/$projectName.log" >> "$issueFile"
    else
      echo "✅ Maven build successful for $projectName"
    fi
  fi

  ###########################################
  # GRADLE PROJECT
  ###########################################
  if [ -f "build.gradle" ] || [ -f "build.gradle.kts" ]; then

    if [ -f "./gradlew" ]; then
      ./gradlew build > "$LOG_DIR/$projectName.log" 2>&1
    else
      gradle build > "$LOG_DIR/$projectName.log" 2>&1
    fi

    if [ $? -ne 0 ]; then
      echo "❌ Gradle build failed for $projectName"
      echo "Gradle build failed for $projectName" > "$issueFile"
      echo "See log:
