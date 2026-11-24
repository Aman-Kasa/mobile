# BURDEW — Android (v0.1.0)

Offline-first electricity and water bill collection for Asmara, Eritrea.
Native Android app in Java. Room/SQLite, platform GPS (no Google Play Services), Mapsforge offline OpenStreetMap, native SMS.

> BURDEW is an independent service. It is not an official government or utility application.

---

## Status: be honest about what's verified

| Area | State |
|---|---|
| Core business rules (`core/`): status transitions + roles, earnings, payment-evidence rules, SMS parser, duplicate fingerprint, phone normalization, PBKDF2 password hashing, money math | **Written and tested.** 21 JUnit tests pass (`app/src/test/.../CoreLogicTest.java`), including the RFC PBKDF2-SHA256 test vectors and the spec's 50/500/5,000 NFA examples. |
| Everything Android (Room DB, screens, GPS, map, SMS, camera, backup) | **Written, never compiled.** The workspace this was written in has no Android SDK, so nothing Android-specific has been built or run yet. Expect a handful of compile fixes on the first build. |

Nothing below is "working" until it has been built and run on a phone.

## Open and build

1. Open the `burdew-android` folder in Android Studio (Koala or newer). Let Gradle sync. It downloads AGP 8.5.2, Room 2.6.1, Mapsforge 0.21.0, and so on.
2. Run the unit tests: `./gradlew test`
3. Run on a phone: `./gradlew installDebug`. For field use, sideload the APK. The app doesn't need the Play Store.

**Check first on the first build** (my most likely mistakes):
- `MapsforgeThemes` import in `ui/map/MapActivity.java`. If 0.21.0 doesn't resolve it, use `org.mapsforge.map.rendertheme.internal.MapsforgeThemes` or the older `InternalRenderTheme.DEFAULT`.
- `Marker.contains(...)` and `onTap(...)` signatures in the same file.
- Room annotation-processor errors in `data/db/Daos.java` (query/column mismatches show up only at compile time).
- Mapsforge's own `minSdk`. If it's above 21, raise `minSdk` in `app/build.gradle`.

## First run

1. Onboarding, then **create the administrator** (first launch only).
2. Settings → **Offline maps** → import a Mapsforge `.map` file. See below.
3. Service providers → add providers (each gets their own username and password).
4. Customers → register. Requests → create and assign.

### Getting the Asmara/Eritrea map (one-time, needs internet once, on any computer)
- Download `eritrea.map` from the Mapsforge download server (download.mapsforge.org → maps → v5 → africa). It's a few MB.
- If it isn't listed, build one from an OpenStreetMap extract of Eritrea (e.g. Geofabrik) with `osmosis` and the `mapsforge-map-writer` plugin.
- Copy it to the phone (USB, Bluetooth, SD card) and import it in Settings. Name the region `asmara` or `eritrea`. Other cities are just more `.map` files.
- Attribution "© OpenStreetMap contributors" is shown on the map and in About (ODbL requirement).

## How the workflow is enforced

Request: `NEW → (PENDING) → ASSIGNED → IN_PROGRESS → PAYMENT_COMPLETED → RECEIPT_DELIVERED` (also `FAILED`, `CANCELLED`)
Payment (separate): `NOT_COLLECTED → COLLECTED → SUBMITTED → VERIFIED` (or `REFUNDED`)

- **Providers** can only move *their own* requests forward. Assigning, cancelling and retrying are admin-only.
- `PAYMENT_COMPLETED` needs a payment marked SUBMITTED, with amount paid > 0 and paid ≤ collected.
- **Verify** needs a receipt number *or* a receipt photo. Verifying snapshots the provider fee onto the transaction, so changing the fee later never rewrites past earnings.
- `RECEIPT_DELIVERED` needs a VERIFIED payment. **Only verified + delivered + non-voided transactions earn** (default 50 NFA, configurable).
- Each request has exactly one transaction (unique index), so a payment can't be recorded twice.
- A request that has collected money can't be cancelled until a refund is recorded.
- Every write goes through `BurdewRepository` in one DB transaction, with an **audit log** (who, what, before → after) and a **sync-queue** entry for the future BURDEW HUB.
- Utility bill money and BURDEW service income are reported separately.

## SMS

Customers send (newlines or `;`, any case, `:` or `=`):
```
SERVICE: ELECTRICITY        (or WATER, E, W)
NAME: Selam Tesfay
ADDRESS: Mai Temenai, near the school
ACCOUNT: 884213
PHONE: 07123456             (optional, defaults to the sender)
```
Every incoming message is stored first. Ordinary messages are marked IGNORED. A repeat of the same text from the same sender within 24 h is marked DUPLICATE. Invalid messages are marked FAILED_PARSE and get a short format-help reply. Valid messages create the customer (if new) and the request, and the reply gives a reference like `R-7KQ4M2`. The broadcast is never aborted, so the phone's normal SMS app still gets everything. Failed messages can be turned into requests by hand from the SMS screen. Outgoing messages record SENT/DELIVERED/failed. Both auto-create and auto-reply can be switched off in Settings.

Note: Google Play restricts SMS permissions. That doesn't matter for a sideloaded APK, but it would matter if you ever publish on Play.

## Security

- Local accounts with PBKDF2-HMAC-SHA256 (120k iterations, random salt, constant-time compare), and a 5-minute lockout after 5 failed attempts. Nothing is hardcoded.
- The session lives in memory only, so closing the app signs out. `allowBackup=false`, which keeps customer data out of cloud backups.
- Customers are archived, never deleted. Receipt photos are stored in app-private storage as compressed JPEG (≤1600 px). Only paths are kept in SQLite.
- CSV export guards against spreadsheet formula injection.
- Restore checks that the file is a BURDEW database, runs an integrity check, refuses backups from newer versions, and **saves a safety copy of the current data before replacing it**.

## Layout

```
app/src/main/java/com/burdew/app/
  core/        pure-Java business rules (unit-tested, no Android imports)
  config/      AppConfig: names, defaults, keys (branding in res/values/strings.xml)
  data/        BurdewRepository (service layer), Session, DataExport (backup/CSV/restore)
  data/db/     Room entities, DAOs, BurdewDatabase (burdew.db)
  location/    one-shot GPS via LocationManager.GPS_PROVIDER
  map/         offline region files (Mapsforge)
  sms/         receiver, sender, delivery-status receiver
  ui/          screens (forms built from string resources, ready to translate)
docs/BURDEW_PROMPT.md   your spec, corrected to describe a new standalone project
```

## Not done yet (honest list)

- **Not compiled or run on a device yet** (see above). Next step: build, then work through the offline test list in spec Part 20.
- MVVM: screens observe Room `LiveData` through the repository, but the logic hasn't been split into ViewModels yet. That's the next refactor once it compiles.
- Database migrations: schema v1 only. `exportSchema` is on, so each future change needs a real `Migration` (destructive fallback is deliberately off).
- Map search covers saved customers only. Searching landmarks and street names needs a Mapsforge POI file (planned).
- Tigrinya/Arabic: all text is in `strings.xml` and RTL is enabled, but there are no translations yet. Native speakers must check them.
- Backup excludes receipt photos (metadata only). It's unencrypted, so share it carefully.
- Phase 7–8: BURDEW HUB (Spring Boot + PostgreSQL), sync worker, admin web dashboard, iOS.
- Placeholder logo (`res/drawable/ic_burdew.xml`).
