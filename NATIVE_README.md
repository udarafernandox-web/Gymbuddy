# Gym Buddy — Native Android (Kotlin + Jetpack Compose)

Development branch: `native-android`. **This is a native Android UI, not a WebView or Lovable wrapper.** The original Capacitor wrapper remains on `main`.

## Configuration before use

Create repository GitHub Actions secrets:
- `GYMBUDDY_SUPABASE_URL`: the Supabase project URL used by Gym Buddy Lovable.
- `GYMBUDDY_SUPABASE_PUBLISHABLE_KEY`: public/anon key only (never a service-role key).

GitHub Actions builds a debug-signed APK. A build with missing secrets can compile but **cannot sign in or fetch data**. No keys are stored in source. Do not confuse a compiling APK with a tested, production-ready app.

## Features actually implemented

- Kotlin/Jetpack Compose four-tab bottom navigation: Today, Train, Log, Me.
- Supabase email/password sign-in, basic sign-up (email confirmation must be completed outside the app), session token stored in Android private app preferences; manual sign-out.
- Fetch dashboard totals, workouts, workout logs, exercise library, templates.
- Create named workout shell (no exercises/sets editor yet).
- Read attendance, daily check-in/check-out with existing one-visit-per-day constraint.
- Read/update today's sugar, oil and food note.
- Read personal records, goals, body measurements and achievements.
- Update display name; read friendships and profile.

## Not yet implemented / not tested

**This is NOT feature-complete.** The following requested features still require work and are intentionally NOT claimed:
- Workout exercise/set editing, live workout session, local autosave, rest timer, per-set records, and atomic `finish_workout` RPC integration; no native workout completion writes are enabled.
- Friendship invite, accept/reject, remove, invite code discovery.
- Profile weight/height settings, measurements/goals CRUD, progress charts.
- Password recovery, access-token refresh and secure encrypted token storage.
- Background offline sync, conflict resolution, local database persistence; internet is required.
- Account deletion, push notifications, native deep links, physical device/end-to-end QA.
- APK signing for Google Play production.

## Running/building

Build via **Actions > Native Kotlin Gym Buddy APK** on this branch. Download the `GymBuddy-Native-debug-APK` artifact and unzip `app-debug.apk`.

Local build with JDK 17 and Android SDK:
```sh
gradle :app:assembleDebug -PsupabaseUrl="https://YOUR_PROJECT.supabase.co" -PsupabaseKey="YOUR_PUBLIC_KEY"
```
Do not hardcode the key in source, and never put a service-role key in an Android application.

### Backend notes
Uses standard Supabase Auth REST and PostgREST endpoints with the existing schema. All access is expected to be restricted by the backend's existing row-level security policies. Schema compatibility and authenticated flows must be tested with a dedicated test user before release.

### Design / data safety
Original database is not migrated or altered by this repository. The app uses the signed-in user's token and existing backend. This is an initial native release candidate, not a claim of full feature parity.
