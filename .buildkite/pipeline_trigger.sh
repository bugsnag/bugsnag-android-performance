#!/usr/bin/env sh

if [[ "$BUILDKITE_MESSAGE" == *"[run-benchmarks]"* ||
  "$BUILDKITE_BRANCH" == "main" ||
  "$BUILDKITE_PULL_REQUEST_BASE_BRANCH" == "main" ||
  ! -z "$FULL_SCHEDULED_BUILD" ]]; then
  # Full build
  buildkite-agent pipeline upload .buildkite/pipeline.benchmark.yml
fi
