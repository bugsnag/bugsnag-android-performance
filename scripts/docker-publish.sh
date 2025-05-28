#!/usr/bin/env bash

echo $KEY > ~/temp_key
base64 --decode ~/temp_key > /publishKey.gpg
mkdir "~/.gradle/"
echo "signing.keyId=$KEY_ID" >> ~/.gradle/gradle.properties
echo "signing.password=$KEY_PASS" >> ~/.gradle/gradle.properties
echo "signing.secretKeyRingFile=/publishKey.gpg" >> ~/.gradle/gradle.properties
echo "NEXUS_USERNAME=$PUBLISH_USER" >> ~/.gradle/gradle.properties
echo "NEXUS_PASSWORD=$PUBLISH_PASS" >> ~/.gradle/gradle.properties
echo "nexusUsername=$PUBLISH_USER" >> ~/.gradle/gradle.properties
echo "nexusPassword=$PUBLISH_PASS" >> ~/.gradle/gradle.properties

/app/gradlew assembleRelease publish --no-daemon --max-workers=1 && \
 echo "Go to https://oss.sonatype.org/ to release the final artefact. For the full release instructions, please read https://github.com/bugsnag/bugsnag-android-performance/blob/next/docs/RELEASING.md"

echo "Fetching staging repositories..."
REPOS_JSON=$(curl -s -u "$PUBLISH_USER:$PUBLISH_PASS" "https://ossrh-staging-api.central.sonatype.com/manual/search/repositories")

if [[ $? -ne 0 || -z "$REPOS_JSON" ]]; then
  echo "Failed to retrieve repository list. Check your credentials or network."
  exit 1
fi

REPO_KEY=$(echo "$REPOS_JSON" | jq -r '.repositories[] | select(.state == "open") | .key')

if [[ $(echo "$REPO_KEY" | wc -l) -gt 1 ]]; then
  echo "Multiple open repositories found. Please specify which repository to close."
  echo "$REPOS_JSON" | jq -r '.repositories[] | select(.state == "open") | .key'
  exit 1
fi

echo "Closing repository $REPO_KEY..."
URL="https://ossrh-staging-api.central.sonatype.com/manual/upload/repository/$REPO_KEY?publishing_type=user_managed"
RESPONSE=$(curl -s -w "\nHTTP_STATUS:%{http_code}" -X POST -u "$PUBLISH_USER:$PUBLISH_PASS" "$URL")
BODY=$(echo "$RESPONSE" | sed -n '/^HTTP_STATUS:/!p')
STATUS=$(echo "$RESPONSE" | sed -n 's/^HTTP_STATUS://p')

  if [[ "$STATUS" != "200" ]]; then
    echo "Failed to close repository. HTTP Status: $STATUS"
    echo "$BODY" | jq -r
    exit 1
  fi

  echo "Repository $REPO_KEY closed successfully."
