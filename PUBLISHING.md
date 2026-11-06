# F-Droid and IzzyOnDroid publishing

This document tracks the upstream preparation for distributing MoneyLog through F-Droid and IzzyOnDroid.

## App identity

- Application ID: `com.openfinanceai`
- Current upstream version: `1.0.1` (version code `2`)
- Source: https://github.com/Joe-cell-ai/MoneyLog-Finance-Tracker
- License: Apache-2.0

Do not change the application ID after publication without a compelling migration plan. Publish a Git tag for every release and ensure the tag points to the exact source used for the release.

## Metadata

Store descriptions and changelog are maintained under `fastlane/metadata/android/en-US/`. Before submission, add the final 512×512 app icon and genuine phone screenshots under:

- `fastlane/metadata/android/en-US/images/icon.png`
- `fastlane/metadata/android/en-US/images/phoneScreenshots/`

Screenshots must come from the actual release build and must not contain personal financial information.

## F-Droid requirements still to verify

1. Ensure every runtime and build dependency is free/open source and available to the F-Droid build environment.
2. In particular, review the Google ML Kit Text Recognition dependency (`com.google.mlkit:text-recognition`). It must not be included in an F-Droid build unless its licensing and distribution status are accepted by F-Droid. Prefer a fully libre OCR implementation or make OCR an optional non-F-Droid product flavor with no proprietary dependency in the F-Droid variant.
3. Verify all Android permissions are necessary, documented, and requested only when their feature is enabled. SMS permissions require particular scrutiny and clear user-facing disclosure.
4. Verify the build succeeds offline/with F-Droid's supported build environment, without downloading prebuilt binaries or requiring secrets.
5. Confirm release version code/name, source tag, Gradle wrapper, SDK/build-tools versions, and dependency versions are pinned and reproducible.
6. Run unit tests and an installable release build. Check that no debug/test-only flags or proprietary services are packaged.
7. Complete a dependency licence inventory and include required third-party notices.

## F-Droid submission

After the checks above pass and a release tag exists, prepare a package metadata file for `com.openfinanceai` in the official `fdroiddata` repository and submit it as a merge request. Use the official F-Droid inclusion checklist and build metadata reference.

## IzzyOnDroid submission

After a signed release APK is published as a GitHub Release asset for the matching source tag, request inclusion through the IzzyOnDroid maintenance repository. Keep the APK signed with the same stable developer release key for future updates. Provide the source repository, release URL, app description, icon, screenshots, and signing information requested by maintainers.

## Release signing

Never commit keystores, passwords, private keys, API keys, or signing secrets. Configure signing secrets in the release workflow or protected repository secrets. Keep the developer signing key backed up securely; losing it can prevent upgrades for users of developer-signed builds.

## Current status

This repository preparation is not an inclusion approval or a published store listing. F-Droid and IzzyOnDroid maintainers make their own acceptance decisions. The Google ML Kit dependency, visual metadata assets, dependency licence inventory, reproducible build, signed release APK, and release tag must be resolved or verified before submission.
