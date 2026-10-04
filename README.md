# IPTVibe Clients

Official client and distribution interfaces for IPTVibe. Portal: https://iptvibe.pages.dev

- `portal/`: responsive portal, PWA, shared catalog rules, server account gateway and desktop client.
- `mobile/`: Android touch interface and Media3 player, using the same account API, source resolver, catalog model and playback admission rules.
- TV source remains in its existing repository. No private account records or signing keys belong in this repository.

Build portal: `cd portal && npm ci && npm test && npm run build`.
Desktop: run `node scripts/desktop-bundle.mjs` from portal, then install desktop dependencies and run the matching `dist:win` or `dist:mac` script.
Android: Gradle 9.6, JDK 17, Android SDK 37 and Media3. Release APK must be signed with the maintained mobile signing certificate before publication.

Desktop builds do not have a purchased signing certificate or Apple notarization. Browser playback requires HTTPS, supported codecs and source CORS. Native clients have a controlled media transport. DRM sources not supported by a client are unavailable there; security controls are retained.

Standard runners in these public repositories are used for builds; no paid runners, plans or services are configured.
