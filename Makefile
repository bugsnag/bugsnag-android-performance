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
	@./scripts/bump-version.sh $(VERSION)
