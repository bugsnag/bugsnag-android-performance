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

echo "Available repositories:"
echo "$REPOS_JSON" | jq -r


#URL="https://ossrh-staging-api.central.sonatype.com/manual/upload/repository/$REPO_KEY/com.bugsnag--default-repository?publishing_type=user_managed"
#
#echo "Closing repository $REPO_KEY..."
#RESPONSE=$(curl -s -w "\nHTTP Status: %{http_code}\n" -X POST -u "$PUBLISH_USER:$PUBLISH_PASS" "$URL")
#
#echo "$RESPONSE"