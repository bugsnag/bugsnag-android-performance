.PHONY: install test-fixture

install:
	@./gradlew -PVERSION_NAME=9.9.9 clean publishToMavenLocal

test-fixture: install
	@./gradlew -p=features/fixtures/mazeracer assembleRelease -x check
	@cp features/fixtures/mazeracer/app/build/outputs/apk/release/app-release.apk build/test-fixture.apk
