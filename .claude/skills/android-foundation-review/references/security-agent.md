# Security & Privacy Agent — Review Checklist & Rubric

You are evaluating the project's security and privacy posture. This is **not** a
penetration test or formal threat model — it's a structural review: does the project
have the patterns and tooling that prevent the common Android security failures?

The bar is calibrated by app sensitivity. A todo app with no PII has different needs
from a banking app. When evidence about app domain is ambiguous, default to assuming
moderate sensitivity (auth tokens, user accounts) and flag the calibration question
in your findings.

**Out of scope for you:**
- General error handling propagation → Architecture
- Crash reporter SDK choice → Architecture
- R8 minification (overlap with obfuscation) → Performance covers performance angle,
  you cover the obfuscation/security angle if it adds anything

---

## 1. Sensitive Data Storage

**What to look for:**
- **Auth tokens, credentials, API keys** stored in:
  - Encrypted DataStore wrapper or `EncryptedSharedPreferences` (`androidx.security:security-crypto`)
    — currently the standard, though the library is in maintenance.
  - **Tink** (`com.google.crypto.tink`) for newer encryption needs.
  - **Android Keystore** for keys, with hardware-backed storage on supported devices.
  - **Credential Manager API** (`androidx.credentials`) for sign-in flows, replacing
    Smart Lock and direct password storage.
- **NOT** in plain `SharedPreferences`, plain DataStore, or hardcoded files.
- **Database encryption** (SQLCipher / Room with passphrase) for sensitive local data.
- BiometricPrompt for unlocking sensitive data.

**How to investigate:**
```bash
echo "Encryption infrastructure:"
grep -rn "androidx.security.crypto\|EncryptedSharedPreferences\|MasterKey\|EncryptedFile" \
  --include="*.kt" --include="*.toml" . 2>/dev/null | head -10

grep -rn "com.google.crypto.tink\|AesGcm\|KeysetHandle" --include="*.kt" --include="*.toml" . 2>/dev/null | head -10

grep -rn "Keystore\|KeyGenParameterSpec\|AndroidKeyStore" --include="*.kt" . 2>/dev/null | head -10

grep -rn "androidx.credentials\|CredentialManager\|GetCredentialRequest" --include="*.kt" --include="*.toml" . 2>/dev/null | head -10

echo "BiometricPrompt:"
grep -rn "BiometricPrompt\|androidx.biometric" --include="*.kt" --include="*.toml" . 2>/dev/null | head -10

echo "Plain prefs storing tokens (smell):"
grep -rn "putString.*token\|putString.*password\|putString.*secret\|putString.*key" --include="*.kt" . 2>/dev/null | head -10

echo "DB encryption:"
grep -rn "SQLCipher\|SupportFactory\|net.zetetic" --include="*.kt" --include="*.toml" . 2>/dev/null | head -5
```

**Grading:**
- STRONG: Tokens in EncryptedSharedPreferences/Tink, Keystore-backed keys, Credential
  Manager for auth, BiometricPrompt where appropriate, sensitive DB encrypted.
- ADEQUATE: Some encryption (e.g., EncryptedSharedPreferences for tokens) but DB or
  files unencrypted.
- WEAK: Tokens or PII in plain SharedPreferences/DataStore/files.
- MISSING: Sensitive data stored without any encryption.

---

## 2. Network Security

**What to look for:**
- **Network Security Configuration** (`res/xml/network_security_config.xml`) referenced
  from the manifest. `cleartextTrafficPermitted="false"` for production.
- **Certificate pinning** via OkHttp `CertificatePinner` — relevant for finance, health,
  enterprise apps. Less critical for casual consumer apps.
- HTTPS enforced everywhere; no `http://` URLs in production code.
- TLS configuration not loosened (no custom `TrustManager` accepting all certs — a
  classic copy-paste vulnerability).
- For sensitive APIs: token rotation, refresh-token handling that doesn't log secrets.

**How to investigate:**
```bash
echo "Network Security Config:"
find . -name "network_security_config.xml" 2>/dev/null
for nsc in $(find . -name "network_security_config.xml" 2>/dev/null); do
  echo "=== $nsc ==="
  cat "$nsc"
done
grep -rn "android:networkSecurityConfig\|usesCleartextTraffic" --include="*.xml" . 2>/dev/null | head -5

echo "Certificate pinning:"
grep -rn "CertificatePinner\|pin-set\|SSLPinning" --include="*.kt" --include="*.xml" . 2>/dev/null | head -10

echo "Cleartext URL smell:"
grep -rn '"http://' --include="*.kt" . 2>/dev/null | grep -v "/test/" | head -10

echo "Custom TrustManager (red flag):"
grep -rn "X509TrustManager\|TrustAllCertificates\|HostnameVerifier.*ALLOW_ALL\|checkServerTrusted.*\{\s*\}" \
  --include="*.kt" . 2>/dev/null | head -10
```

**Grading:**
- STRONG: Network Security Config locks down cleartext, certificate pinning where
  the app's risk profile warrants it, no cleartext URLs in prod, no loosened TLS.
- ADEQUATE: HTTPS enforced, no Network Security Config but no cleartext usage either.
- WEAK: `usesCleartextTraffic="true"` in production manifest, `http://` URLs in source.
- MISSING: Custom TrustManager accepting all certificates, or no HTTPS strategy at all.

---

## 3. Secrets Management

**What to look for:**
- API keys, signing keys, passwords **NOT** committed to source control. Look for
  patterns suggesting they are.
- Secrets injected via Gradle properties (`local.properties`, environment variables,
  `gradle.properties` in user home). Plugins like `secrets-gradle-plugin` or manual
  reads via `localProperties.getProperty()`.
- `keystore.properties` referenced but in `.gitignore`.
- `BuildConfig` fields for non-secret config; secret material loaded at runtime where
  possible (Keystore + remote provisioning).

**How to investigate:**
```bash
echo "secrets-gradle-plugin or manual property reads:"
grep -rn "secrets-gradle-plugin\|local.properties\|localProperties" --include="*.kts" --include="*.gradle" . 2>/dev/null | head -10

echo "BuildConfig fields:"
grep -rn "buildConfigField" --include="*.kts" --include="*.gradle" . 2>/dev/null | head -15

echo ".gitignore protection:"
cat .gitignore 2>/dev/null | grep -E "local.properties|keystore|\.jks|\.keystore|google-services.json" | head -10

echo "Suspicious literal API-key shapes (false positives expected):"
grep -rPn '"[A-Za-z0-9_]{32,}"' --include="*.kt" --include="*.kts" . 2>/dev/null \
  | grep -iE "api.?key|secret|token" | head -10

echo "Committed keystore files (red flag):"
find . \( -name "*.jks" -o -name "*.keystore" -o -name "release.keystore" \) -not -path "*/build/*" 2>/dev/null | head -5

echo "google-services.json status:"
find . -name "google-services.json" 2>/dev/null
```

**Grading:**
- STRONG: All secrets injected from outside source. `.gitignore` covers `local.properties`,
  keystores, env files. `secrets-gradle-plugin` or equivalent.
- ADEQUATE: Some secrets externalized but a few `BuildConfig.API_KEY = "..."` literals
  remain.
- WEAK: API keys hardcoded in source, but at least the keystore is gitignored.
- MISSING: Keystore files or live API keys committed to the repo.

---

## 4. Permissions & Privacy

**What to look for:**
- **Runtime permissions** requested only for what the app actually uses. Each declared
  `<uses-permission>` should map to a real feature.
- **Photo Picker** (`ActivityResultContracts.PickVisualMedia`, SDK 33+) instead of
  `READ_EXTERNAL_STORAGE` for image selection.
- **Scoped storage** compliance (no `requestLegacyExternalStorage="true"` unless
  documented).
- **Permission rationale** UI shown before re-requesting denied permissions.
- **Privacy-sensitive permissions** (location, camera, microphone, contacts, calendar,
  health) audited — does the app actually need them?
- **Data Safety form** considerations: Play Store now requires disclosing data use.
  Code-level signals: analytics SDKs, ad SDKs, tracking libraries.

**How to investigate:**
```bash
echo "Manifest permissions:"
for m in $(find . -name "AndroidManifest.xml" -path "*/main/*" 2>/dev/null); do
  echo "=== $m ==="
  grep "uses-permission\|uses-feature" "$m"
done

echo "Photo picker vs READ_EXTERNAL_STORAGE:"
grep -rn "PickVisualMedia\|ActivityResultContracts" --include="*.kt" . 2>/dev/null | head -5
grep -rn "READ_EXTERNAL_STORAGE\|READ_MEDIA_IMAGES" --include="*.kt" --include="*.xml" . 2>/dev/null | head -5

echo "Legacy storage opt-out:"
grep -rn "requestLegacyExternalStorage\|preserveLegacyExternalStorage" --include="*.xml" . 2>/dev/null

echo "Sensitive SDK signals (analytics, ads, tracking):"
grep -rn "FirebaseAnalytics\|GoogleAnalytics\|Mixpanel\|Segment\|AppsFlyer\|Adjust\|Branch\|Mopub\|AdMob" \
  --include="*.kts" --include="*.toml" . 2>/dev/null | head -10

echo "Permission rationale:"
grep -rn "shouldShowRequestPermissionRationale" --include="*.kt" . 2>/dev/null | head -5
```

**Grading:**
- STRONG: Minimal permissions, all justified by features. Photo Picker used. Scoped
  storage respected. Rationale shown. Sensitive permissions audited.
- ADEQUATE: Reasonable permission set, mostly modern APIs (Photo Picker), some legacy
  patterns.
- WEAK: Broad permission declarations, READ_EXTERNAL_STORAGE used where Photo Picker
  would suffice, no rationale flow.
- MISSING: Permission sprawl with no apparent justification, legacy storage opt-out.

---

## 5. Manifest Hardening & Component Exposure

**What to look for:**
- **`android:debuggable`** not set in production manifest (or only set false).
- **`android:allowBackup`** — defaults to `true`, which can leak data via ADB on
  rooted devices. Should be `false` for sensitive apps, or use
  `android:fullBackupContent` / `android:dataExtractionRules` (SDK 31+) to control
  what's included.
- **`android:exported`** — explicit on all components (mandatory since SDK 31).
  Components with `exported="true"` should have a clear reason and (where applicable)
  a permission requirement.
- **Intent filters** on exported activities should be examined; verifying they don't
  expose internal state.
- **FileProvider** for sharing files with other apps (vs file:// URIs which haven't
  worked since Android 7).

**How to investigate:**
```bash
echo "Manifests:"
for m in $(find . -name "AndroidManifest.xml" -path "*/main/*" 2>/dev/null); do
  echo "=== $m ==="
  cat "$m"
done

echo "exported=true count:"
grep -rn 'android:exported="true"' --include="*.xml" . 2>/dev/null | head -15

echo "Backup configuration:"
grep -rn "android:allowBackup\|android:fullBackupContent\|android:dataExtractionRules" --include="*.xml" . 2>/dev/null | head -10

echo "FileProvider:"
grep -rn "FileProvider\|androidx.core.content.FileProvider" --include="*.xml" --include="*.kt" . 2>/dev/null | head -10

echo "debuggable in release:"
grep -rn "isDebuggable\s*=\s*true\|debuggable true" --include="*.kts" --include="*.gradle" . 2>/dev/null | head -5
```

**Grading:**
- STRONG: All components explicitly `exported`, exported components justified and
  permission-protected, backup rules configured for sensitive data, FileProvider for
  sharing, no debuggable in release.
- ADEQUATE: Exported flags present, backup defaults but no sensitive data, file sharing
  via FileProvider.
- WEAK: Components without explicit exported, `allowBackup="true"` on app with sensitive
  data, file:// URIs in source.
- MISSING: Manifest without exported flags (build fails on SDK 31+ — flag if you see
  this, build is probably broken), debuggable in release.

---

## 6. Authentication & Anti-Tamper

**What to look for:**
- **Play Integrity API** (`com.google.android.play:integrity`) for apps where device
  tampering matters (finance, gaming, premium content).
- Token refresh logic that doesn't log full tokens.
- Logout flow that revokes server tokens AND clears local sensitive data.
- **Root detection** — some apps need it (banking), most don't. Don't ding apps that
  skip this unless the domain warrants it.
- **Intent redirection** prevention: don't blindly resolve and start Intents from
  untrusted sources.

**How to investigate:**
```bash
echo "Play Integrity:"
grep -rn "PlayIntegrity\|integrityManager\|IntegrityTokenRequest" --include="*.kt" --include="*.toml" . 2>/dev/null | head -5

echo "Logout and token clearing:"
grep -rn "logout\|signOut\|clearTokens\|revoke" --include="*.kt" . 2>/dev/null | head -10

echo "Intent redirection risk:"
grep -rn "getParcelableExtra.*Intent\|getIntent.*Intent\|intent.extras" --include="*.kt" . 2>/dev/null | head -10

echo "Token logging risk:"
grep -rn "Log\.\|Timber\." --include="*.kt" . 2>/dev/null \
  | grep -iE "token|password|secret" | grep -v "/test/" | head -10
```

**Grading:**
- STRONG: Play Integrity for high-stakes apps, logout properly clears state, no token
  logging, intent redirection guarded.
- ADEQUATE: Logout works, no logging of secrets, no Play Integrity (acceptable for
  low-stakes apps).
- WEAK: Logout doesn't clear tokens, secrets appear in logs, intents from untrusted
  sources resolved without validation.
- MISSING / N/A: Trivial app with no auth — N/A. Sensitive app with none of the above
  — MISSING.

---

## 7. Dependency Hygiene

**What to look for:**
- **Vulnerability scanning** in CI: OWASP Dependency-Check, Snyk, GitHub Dependabot,
  Renovate, Gradle's `dependencyUpdates` task.
- Dependencies on **maintained** libraries — flag obviously abandoned ones (last
  release 3+ years, no GitHub activity).
- No JCenter references (sunset 2022; if still present, the build is fragile).
- Reproducible builds: dependencies pinned to exact versions, not ranges.

**How to investigate:**
```bash
echo "Dependency-update tooling:"
grep -rn "ben-manes.versions\|dependencyUpdates\|com.github.ben-manes" --include="*.kts" --include="*.toml" --include="*.gradle" . 2>/dev/null | head -5
grep -rn "owasp\|dependency-check\|snyk" --include="*.kts" --include="*.gradle" --include="*.yml" . 2>/dev/null | head -5

echo "Dependabot / Renovate:"
find . -name "dependabot.yml" -o -name "renovate.json" -o -name ".github/dependabot.yml" 2>/dev/null | head -5

echo "JCenter (red flag):"
grep -rn "jcenter()" --include="*.kts" --include="*.gradle" . 2>/dev/null | head -5

echo "Version ranges (mild smell):"
grep -rn ":+\b\|:[0-9.]*+\"" --include="*.kts" --include="*.gradle" --include="*.toml" . 2>/dev/null | head -5
```

**Grading:**
- STRONG: Dependabot/Renovate configured, vulnerability scanning in CI, no abandoned
  libs, no JCenter, exact versions.
- ADEQUATE: `dependencyUpdates` task or manual update cadence; no automated vuln
  scanning.
- WEAK: No update process, JCenter still referenced, version ranges.
- MISSING: No dependency hygiene at all.
