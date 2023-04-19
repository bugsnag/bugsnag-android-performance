.PHONY: install test-fixture check bump

check:
	@./gradlew --continue license detekt lint ktlintCheck test
	@cd features/fixtures/mazeracer && ./gradlew ktlintCheck detekt

install:
	@./gradlew -PVERSION_NAME=9.9.9 clean publishToMavenLocal

test-fixture: install
	@./gradlew -p=features/fixtures/mazeracer assembleRelease -x check
	@cp features/fixtures/mazeracer/app/build/outputs/apk/release/app-release.apk build/test-fixture.apk

bump:
ifneq ($(shell git diff --staged),)
	@git diff --staged
	@$(error You have uncommitted changes. Push or discard them to continue)
endif
ifeq ($(VERSION),)
	@$(error VERSION is not defined. Run with `make VERSION=number bump`)
endif
	@echo Bumping the version number to $(VERSION)
	@sed -i '' "s/VERSION_NAME=.*/VERSION_NAME=$(VERSION)/" gradle.properties
	@sed -i '' "s/const val VERSION: String = .*/const val VERSION: String = \"$(VERSION)\"/"\
	 bugsnag-android-performance/src/main/kotlin/com/bugsnag/android/performance/BugsnagPerformance.kt
	@sed -i '' "s/bugsnag-android-performance:.*\"/bugsnag-android-performance:v$(VERSION)\"/" examples/performance-example/app/build.gradle
	@sed -i '' "s/## TBD/## $(VERSION) ($(shell date '+%Y-%m-%d'))/" CHANGELOG.md
