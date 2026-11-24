// Who may open gamedeals.bam.pm links in the app instead of the browser. Both files are public by design.

// Android App Links: SHA-256 fingerprints of the certificates that sign the Play build. Play App Signing re-signs
// uploads, so this must be Google's *app signing* key from Play Console → Test and release → App integrity → App
// signing (not the upload key). Format "AB:CD:…" (32 colon-separated bytes). While this list is empty the Worker
// serves no assetlinks.json, so a half-configured file is never live.
export const ANDROID_PACKAGE = "pm.bam.gamedeals";
// Play App Signing key (Play Console → App integrity → App signing), added 2026-11-24.
export const ANDROID_CERT_SHA256 = ["CB:80:73:86:41:6C:DF:90:5C:B3:AD:71:41:68:C0:2C:5A:F5:58:F1:10:77:54:29:71:76:89:D8:8E:C2:75:4A"];

// iOS Universal Links: <Team ID>.<bundle id>, from iosApp.xcodeproj (DEVELOPMENT_TEAM / PRODUCT_BUNDLE_IDENTIFIER).
export const IOS_APP_ID = "36N7FDX928.pm.bam.gamedeals.ios";

// Paths the apps claim. Keep in step with ShareLinks.kt (common/links) and the Android intent filter.
export const APP_LINK_PATHS = ["/g/*", "/w/*"];

const FINGERPRINT = /^([0-9A-F]{2}:){31}[0-9A-F]{2}$/;

/** The assetlinks.json statement list, or null when no valid fingerprint is configured. */
export function assetLinksJson(fingerprints = ANDROID_CERT_SHA256) {
  const valid = fingerprints.map((f) => f.trim().toUpperCase()).filter((f) => FINGERPRINT.test(f));
  if (valid.length === 0) return null;
  return JSON.stringify([
    {
      relation: ["delegate_permission/common.handle_all_urls"],
      target: { namespace: "android_app", package_name: ANDROID_PACKAGE, sha256_cert_fingerprints: valid },
    },
  ]);
}

/** The apple-app-site-association document (iOS 13+ "components" form). */
export function appleAppSiteAssociationJson() {
  return JSON.stringify({
    applinks: {
      details: [{ appIDs: [IOS_APP_ID], components: APP_LINK_PATHS.map((path) => ({ "/": path })) }],
    },
  });
}
