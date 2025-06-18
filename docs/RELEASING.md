# Releasing a new version

`bugsnag-android-performance` is released via [Sonatype](https://central.sonatype.com/). If you are a project maintainer you can release a new version by unblocking the publish step on CI and following the steps below.

## Pre-release checklist

This contains a prompt of checks which you may want to test, depending on the extent of the changeset:

- [ ] Has the full test suite been triggered on Buildkite and does it pass?
- [ ] Have versions of Android not covered by CI been considered?
- [ ] Does the build pass on the CI server?
- [ ] Are all Docs PRs ready to go?
- [ ] Do the installation instructions work when creating an example app from scratch?
- [ ] Has all new functionality been manually tested on a release build?
    - [ ] Ensure the example app sends traces when backgrounded
    - [ ] Ensure the example app sends traces after the standard timeout
    - [ ] If a response is not received from the server, is the report queued for later?
    - [ ] If no network connection is available, is the report queued for later?
    - [ ] Are queued traces sent when the device regains network access?
- [ ] Have the installation instructions been updated on the [dashboard](https://github.com/bugsnag/dashboard-js/tree/master/js/dashboard/components/integration_instructions) as well as the [docs site](https://github.com/bugsnag/docs.bugsnag.com)?
- [ ] Do the installation instructions work for a manual integration?
- 
## Making the release

- Check the performance benchmarks against the [baseline](BENCHMARKS.md) to confirm there are no serious regressions
- Create a new release branch from `next` -> `release/vN.N.N`
- Pull the release branch and update it locally:
    - [ ] Update the version number with `make bump`
    - [ ] Inspect the updated CHANGELOG, README, and version files to ensure they are correct
- Open a Pull Request from the release branch to `main`
  - The Pull Request title should be `Release vN.N.N`
  - Copy the CHANGELOG entries for the new release as the Pull Request description
- Once merged:
    - Pull the latest changes (checking out `main` if necessary)
    - On CI:
        - Trigger the release step by allowing the `Trigger package publish` step to continue
        - Verify the `Publish` step runs correctly and the artefacts are upload to sonatype.
    - Release to GitHub:
        - [ ] Create *and tag* the release from `main` on [GitHub Releases](https://github.com/bugsnag/bugsnag-android-performance/releases)
    - Checkout `main` and pull the latest changes
    - [ ] Test by input `rm -rf ~/.m2/repository/com/bugsnag` and `./gradlew publishToMavenlocal` to terminal and test the changes in the example app
    - [ ] "Promote" the release build on Maven Central:
        - Go to the [sonatype dashboard](https://central.sonatype.com/publishing/deployments)
        - Click the search box at the top right, and type “com.bugsnag”
        - Select the com.bugsnag staging repository
        - Ensure that AARs and POMs are present for each module, and that ProGuard rules are present for AARs which define ProGuard rules
        - Click the “close” button in the toolbar, no message
        - Click the “refresh” button
        - Select the com.bugsnag closed repository
        - Click the “release” button in the toolbar
    - Merge outstanding docs PRs related to this release

