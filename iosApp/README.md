# iOS Compose example

Open `HanziAnimatorExample.xcodeproj`, select the shared `HanziAnimatorExample` scheme and an arm64 iOS Simulator, then Run. The deployment target is iOS 15. Choose your own signing team/bundle identifier to run on a device. This is a SwiftUI host for the shared Compose sample, not a standalone Swift package.

The first build phase runs `:sample:embedAndSignAppleFrameworkForXcode`; framework search paths point to Gradle's configuration/SDK-specific output. The static `HanziSample` framework supplies `MainViewControllerKt.MainViewController()`. Xcode user-script sandboxing is disabled for this build phase so Gradle can use its normal caches and outputs. Set `JAVA_HOME` to a JDK supported by the repository if Xcode cannot find it.

The final build phase copies the generated iOS `strokes` directory and all data license files to the **application** bundle. `iosBundledDataSource` reads from `NSBundle.mainBundle`; copying assets into only the framework is insufficient. The iOS profile uses Apple's Brotli system decoder and requires iOS 15+. Preserve the license texts when integrating the bundled dataset. The Compose Gradle resource integration separately copies any shared UI resources. Android/iOS use system fonts; the larger bundled CJK sample font is restricted to web.

Command-line simulator build:

```sh
xcodebuild -project iosApp/HanziAnimatorExample.xcodeproj \
  -scheme HanziAnimatorExample -configuration Debug \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath iosApp/build/DerivedData CODE_SIGNING_ALLOWED=NO build
```

To verify resource loading inside the installed application, install that `.app` with `xcrun simctl install`, launch with `--bundled-data-smoke`, and read `Documents/bundled-data-smoke.txt` from `xcrun simctl get_app_container <device> engineering.carr.hanzianimator.sample.ios data`. The Swift host first checks the data/license files in `Bundle.main`; the shared Kotlin verifier then loads actual stroke data, checks the complete character index and verifies practice/component metadata. A passing result begins `PASS:`. This verifies the iOS packaging and system decoder together.

Simulator application sizes are development artifacts, not App Store download estimates. Measure signed Release archives and App Thinning reports on the intended distribution profile before setting production download budgets.

Verified locally: Debug arm64 simulator build and the installed bundled-data smoke test passed on iPhone 16e / iOS 26. The report is in `../review-assets/current-implementation/ios-installed-smoke.json`. This is not a signed-device/App Store size measurement.
