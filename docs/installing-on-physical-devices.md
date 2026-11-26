# Installing the debug build on physical devices

Notes for sideloading `app-debug.apk` onto real hardware (esp. older tablets running
custom ROMs, e.g. the SM-T320 on LineageOS). These errors look like architecture or
SDK mismatches but usually aren't.

## TL;DR

```bash
export ANDROID_HOME=$HOME/Android/Sdk
$ANDROID_HOME/platform-tools/adb install --no-streaming -r -t \
  app/build/intermediates/apk/debug/app-debug.apk
```

`--no-streaming` + `-t` together fix the two errors below.

## Error 1: `failed to write; splice failed: EINVAL (Invalid argument)`

The adb **streamed-install** bug. adb streams the APK to `cmd package install -S` over a
pipe via the `splice()` syscall, which some device kernels (older hardware on newer
custom ROMs) reject with `EINVAL`. It fails during *transfer*, before any compatibility
check — so the message is misleading; it is **not** an ABI/minSdk problem.

**Fix:** `--no-streaming` uses the legacy push-to-`/data/local/tmp`-then-`pm install`
path (no `splice()`).

## Error 2: `INSTALL_FAILED_TEST_ONLY: Did you forget to add -t?`

AGP stamps `intermediates/apk/debug/app-debug.apk` with `android:testOnly="true"` (it's
the artifact Studio deploys directly). The package manager refuses `testOnly` APKs
unless told otherwise.

**Fix:** add `-t`.

## Android Studio Run button

Studio's deployer hits the same streaming path. To avoid Error 1 there:
`Help > Edit Custom Properties...` → add `android.deploy.streaming.install=false` →
restart. Studio handles the `testOnly` flag itself, so `-t` isn't needed via the IDE.

## Sanity checks (read-only)

```bash
adb devices -l                              # is the device attached?
adb shell getprop ro.build.version.sdk      # device API level vs. minSdk (26)
adb shell getprop ro.product.cpu.abilist    # device ABIs vs. APK's lib/<abi>
unzip -l app-debug.apk | grep lib/          # which ABIs the APK ships
```

The 32-bit-only devices (`armeabi-v7a`, no `arm64-v8a`) still work because the project
sets no `abiFilters`, so release APK/AABs include every ABI the native deps ship. Keep
`armeabi-v7a` in the ABI set if such devices must be supported.

## Verify install

```bash
adb shell pm list packages | grep pm.bam.gamedeals
adb shell monkey -p pm.bam.gamedeals -c android.intent.category.LAUNCHER 1
```
