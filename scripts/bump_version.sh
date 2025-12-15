##!/bin/bash
#set -e
#
## 1. Capture the new version passed by Semantic Release
#NEW_VERSION=$1
#
#if [ -z "$NEW_VERSION" ]; then
#  echo "Error: No version argument supplied."
#  exit 1
#fi
#
#echo "🚀 Bumping Android version to: $NEW_VERSION"
#
## 2. Define the path to your module's build file
#GRADLE_FILE="NewRelicVideoCore/build.gradle"
#
#if [ ! -f "$GRADLE_FILE" ]; then
#    echo "Error: Could not find file $GRADLE_FILE"
#    exit 1
#fi
#
## 3. Update 'versionName' ONLY
## This finds 'versionName "4.0.3"' and changes it to 'versionName "4.0.4"'
#sed -i.bak "s/versionName \".*\"/versionName \"$NEW_VERSION\"/" "$GRADLE_FILE"
#
## 4. Clean up the backup file created by sed
#rm "${GRADLE_FILE}.bak"
#
#echo "✅ Updated $GRADLE_FILE:"
#echo "   - versionName: $NEW_VERSION"
#echo "   - versionCode: (Unchanged)"

#!/bin/bash
set -e

# 1. Capture the new version passed by Semantic Release
NEW_VERSION=$1

if [ -z "$NEW_VERSION" ]; then
  echo "Error: No version argument supplied."
  exit 1
fi

echo "🚀 Bumping Android version to: $NEW_VERSION"

# 2. Define the list of all module build files you need to update
# (Based on your project structure)
MODULE_FILES=(
  "NewRelicVideoCore/build.gradle"
  "NRExoPlayerTracker/build.gradle"
  "NRIMATracker/build.gradle"
)

# 3. Loop through each file and update the version
for GRADLE_FILE in "${MODULE_FILES[@]}"; do

  if [ -f "$GRADLE_FILE" ]; then
      echo "   👉 Updating $GRADLE_FILE..."

      # Update 'versionName' ONLY (as per team requirement)
      sed -i.bak "s/versionName \".*\"/versionName \"$NEW_VERSION\"/" "$GRADLE_FILE"

      # Clean up backup file
      rm "${GRADLE_FILE}.bak"
  else
      echo "   ⚠️ Warning: Could not find file $GRADLE_FILE. Skipping."
  fi

done

echo "✅ All modules updated to version $NEW_VERSION"