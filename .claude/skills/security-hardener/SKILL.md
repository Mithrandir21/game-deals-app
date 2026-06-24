---
name: security-hardener
description: MASVS-aligned Android security audit — encrypted storage, TLS and certificate pinning, exported component exposure, deep-link hijacking, WebView config, biometric/Keystore use, and debug/root posture trade-offs. Use whenever the user mentions "security", "MASVS", "MASTG", "OWASP Mobile", "pentest findings", "certificate pinning", "exported activity", "deep link hijack", "Keystore", "encrypted SharedPreferences", or wants to harden an app before a security review.
---

# Security Hardener

A structured Android security review focused on practical defects, not paranoia. Aligned with OWASP MASVS so findings map to a recognized standard.

## When to use

Triggers: "security audit", "MASVS", "MASTG", "pentest", "hardening", "exported", "deep link", "TLS pinning", "Keystore", "EncryptedSharedPreferences", "root detection", "tapjacking".

For "I want to encrypt user passwords in the app" — that's a design conversation. Use this skill for going through an existing app methodically.

## Process

### Phase 1: Inventory the attack surface

Grep / inspect for:

- **Exported components**: `android:exported="true"` in Activities, Services, Broadcast Receivers, Content Providers. Each is an attack surface.
- **Deep links**: `<intent-filter>` with `<data>` tags. Note schemes, hosts, paths.
- **Permissions**: declared in manifest, requested at runtime. Especially dangerous-level ones.
- **Network**: usesCleartextTraffic, network security config XML, TLS configuration.
- **Storage**: where the app writes data (`getFilesDir`, `getExternalFilesDir`, MediaStore, SharedPreferences, Room, DataStore).
- **WebViews**: any usage, JavaScript enabled, file access.
- **Native code**: presence of `.so` files, `System.loadLibrary` calls.
- **Crypto**: any direct use of `javax.crypto`, custom encryption, hardcoded keys.

### Phase 2: Walk the MASVS categories

**MASVS-STORAGE — sensitive data at rest**

- No secrets in `SharedPreferences` or unencrypted files. Use `EncryptedSharedPreferences` or DataStore with encryption, or store in Android Keystore.
- Tokens / refresh tokens encrypted. Ideally backed by Keystore-derived keys with `setUserAuthenticationRequired` for high-value secrets.
- No sensitive data in logs. Audit `Log.d`, `Log.i`, `println`. Use a wrapper that strips in release.
- No sensitive data in screenshots / recents. Set `FLAG_SECURE` on Activities with sensitive content.
- No backup of sensitive data. `android:allowBackup="false"`, or use `android:fullBackupContent` to exclude.

**MASVS-CRYPTO — cryptography**

- No hardcoded keys, IVs, or salts. Pen testers will find them.
- Use Android Keystore for key storage. Don't roll your own.
- Use Tink or `androidx.security.crypto` rather than raw `javax.crypto` — easy to misuse the primitives.
- For symmetric encryption: AES-GCM, fresh IV per message. No ECB. No CBC without HMAC.
- For asymmetric: RSA-OAEP-2048 or ECDSA P-256+. No PKCS1v1.5 padding.

**MASVS-AUTH — authentication and session management**

- Tokens have expiry; refresh tokens are server-revocable.
- Logout actually invalidates tokens server-side, not just clears local storage.
- Biometric prompt uses `BiometricPrompt` with `setAllowedAuthenticators(BIOMETRIC_STRONG)`. Don't trust class 2/3 weak biometrics for sensitive actions.
- For high-value actions (transactions, account changes), require fresh authentication, not just app open.

**MASVS-NETWORK — network communication**

- All traffic HTTPS. `usesCleartextTraffic="false"` and Network Security Config XML enforcing it. No exceptions for "staging".
- Certificate pinning for high-value APIs. Use `NetworkSecurityConfig` `<pin-set>` (declarative) over OkHttp `CertificatePinner` (still fine, just less centralized).
- TLS 1.2 minimum, prefer 1.3. Don't disable hostname verification.
- HTTP proxies / debug interceptors stripped in release builds.

**MASVS-PLATFORM — interaction with the platform**

- **Exported components**: review each. Default exported activities accessible by other apps; consider `android:permission` or `android:exported="false"` if not intentional.
- **Intent handling**: validate every extra. Don't trust `Intent.getStringExtra` to be non-null or in expected format. `try/catch` around parsing.
- **Deep links**: validate every path parameter. A deep link to `/user/profile?id=123` is a potential IDOR if you don't check the user owns that profile.
- **App Links** (verified deep links): use Android App Links with Digital Asset Links to prevent other apps from hijacking your scheme.
- **PendingIntents**: use `FLAG_IMMUTABLE` (required on API 31+). Mutable PendingIntents are a known attack vector.
- **WebView**:
  - `setJavaScriptEnabled(true)` only if needed.
  - `setAllowFileAccess(false)` unless required.
  - `setAllowContentAccess(false)`.
  - `addJavascriptInterface` is dangerous — exposes Java methods to web JS. Avoid or restrict heavily.
  - Validate URLs before `loadUrl`.

**MASVS-CODE — code quality and build settings**

- `debuggable="false"` in release.
- Obfuscation enabled (R8 with `minifyEnabled`).
- Anti-tampering for high-value apps (signature verification at runtime). Tradeoffs: catches casual repackaging, doesn't stop determined attackers.
- Logging stripped from release (use a `Timber` debug-only tree, or wrap and `if (BuildConfig.DEBUG)`).
- No `@SuppressLint("ApplySharedPref")` or `@SuppressLint("HardwareIds")` without justification.

**MASVS-RESILIENCE — runtime integrity (high-value apps only)**

- Root detection: SafetyNet (deprecated) → Play Integrity API. Decide what to do when failed — block, warn, or just log.
- Emulator detection: same posture.
- Debugger detection: trips during local development too, so feature-flag.

Treat resilience as defense-in-depth, not core security.

### Phase 3: Tapjacking and overlay attacks

For sensitive screens (login, payment, biometric confirmation):

- `View.setFilterTouchesWhenObscured(true)` or `android:filterTouchesWhenObscured="true"`.
- Compose: not directly supported via modifier — use an `AndroidView` wrapper or set on root in the Activity.

### Phase 4: Verify

For each finding:

- Reproduce the attack locally (e.g. install a malicious sample app that fires the exported intent).
- Confirm the fix blocks it.
- Add a unit / instrumented test where possible (validation logic is testable; intent extraction is testable).

### Phase 5: Document

For each finding:

```
Category: [MASVS-STORAGE / NETWORK / etc.]
Issue: [what's wrong]
Risk: [Critical / High / Medium / Low — by impact AND likelihood]
Fix: [specific code change]
Reference: [MASTG test ID if you know it]
```

## Output

A report grouped by MASVS category, sorted by risk. Include a summary table at the top:

| Category | Critical | High | Medium | Low |
|---|---|---|---|---|
| Storage | 0 | 1 | 2 | 0 |
| Network | 1 | 0 | 0 | 0 |
| ... | | | | |

And a recommended remediation order.

## Common pitfalls

- **"We don't have sensitive data."** Most apps have at least auth tokens. Treat them as sensitive.
- **Root detection as a primary control.** It's bypassable. Use it as a signal, not a wall.
- **Certificate pinning without rotation strategy.** Pins expire. Plan for rotation, ship updates ahead of cert renewal.
- **Disabling cleartext for production but allowing for "staging".** Staging gets exploited too. Use proper certs everywhere.
- **`addJavascriptInterface` to "share data with the web view".** Use `postMessage` and `evaluateJavascript` instead.
- **`@SuppressLint("ExportedReceiver")` without reading what it's suppressing.** Lint warnings about exports are almost always real.
