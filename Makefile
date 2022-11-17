.PHONY: install test-fixture check

check:
	@./gradlew --continue detekt lint ktlintCheck test
	@cd features/fixtures/mazeracer && ./gradlew ktlintCheck detekt

install:
	@./gradlew -PVERSION_NAME=9.9.9 clean publishToMavenLocal

test-fixture: install
	@./gradlew -p=features/fixtures/mazeracer assembleRelease -x check
	@cp features/fixtures/mazeracer/app/build/outputs/apk/release/app-release.apk build/test-fixture.apk
