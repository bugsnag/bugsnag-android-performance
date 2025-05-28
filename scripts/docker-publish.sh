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

API_BASE="https://ossrh-staging-api.central.sonatype.com/manual"

function fetch_repositories() {
  echo "Fetching staging repositories..."
  local response
  response=$(curl -s -u "$PUBLISH_USER:$PUBLISH_PASS" "$API_BASE/search/repositories")

  if [[ -z "$response" ]]; then
    echo "Error: Empty response. Check your credentials or network connection." >&2
    exit 1
  fi

  echo "$response"
}

function select_open_repository() {
  local repos_json="$1"
  local open_repos
  open_repos=$(echo "$repos_json" | jq -r '.repositories[] | select(.state == "open") | .key')

  local count
  count=$(echo "$open_repos" | wc -l)

  if [[ $count -eq 0 ]]; then
    echo "No open repositories found."
    exit 1
  elif [[ $count -gt 1 ]]; then
    echo "Multiple open repositories found. Please specify which one to close:"
    echo "$open_repos"
    exit 1
  fi

  echo "$open_repos"
}

function close_repository() {
  local repo_key="$1"
  local url="$API_BASE/upload/repository/$repo_key?publishing_type=user_managed"

  echo "Closing repository: $repo_key ..."
  local response
  response=$(curl -s -w "\nHTTP_STATUS:%{http_code}" -X POST -u "$PUBLISH_USER:$PUBLISH_PASS" "$url")

  local body status
  body=$(echo "$response" | sed -n '/^HTTP_STATUS:/!p')
  status=$(echo "$response" | sed -n 's/^HTTP_STATUS://p')

  if [[ "$status" != "200" ]]; then
    echo "Failed to close repository. HTTP Status: $status"
    echo "$body" | jq -r
    exit 1
  fi

  echo "Repository $repo_key closed successfully."
}

# Main script execution
repos_json=$(fetch_repositories)
repo_key=$(select_open_repository "$repos_json")
close_repository "$repo_key"


