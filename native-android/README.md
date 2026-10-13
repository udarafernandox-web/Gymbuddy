# Gym Buddy Native (Kotlin + Jetpack Compose)

This branch contains an **actual native Android UI**, not a WebView. Existing `main` is unchanged. It communicates directly with the existing Gym Buddy Supabase project using its public publishable key, scoped to authenticated users and existing RLS policies. Never use a service-role key in the app.

## Current implementation status

Implemented in source: email/password sign-in and signup (email confirmation subject to Supabase settings), session token refresh attempt, four-tab Compose navigation, workout listing and creation, adding workout exercises, session checklist and local draft, atomic workout finishing via the existing `finish_workout` RPC, attendance check-in/check-out, daily sugar/oil nutrition edit and logs, workout log history, profile fields, goal/PR viewing, friendship status viewing and sign-out.

**Not fully implemented / not validated**: set editing and adaptive weights, complete historical session details and safe resumption after process death, persistent session UUID for network retry, automated attendance/check-out with workout start/end, workout templates, exercise library search, friend requests/accept/remove, profile settings, body measurements, goals create/edit, PR chart visualizations, password recovery, OAuth, accessibility audit, instrumented tests and offline database synchronization. Basic local draft stores workout ID only, not unsaved set progress.

**This is a development preview, not feature-parity production software.** Supabase requests and authenticated app flows require device testing. If the build succeeds, its downloadable artifact is a debug-signed APK, not a Play Store release.

## Build

Open GitHub Actions → **Build Native Gym Buddy** on the `native-development` branch. After a successful run download `GymBuddy-Native-debug-APK` in Artifacts. Locally, run `gradle assembleDebug` in `native-android` with Gradle 8.9, JDK 17 and Android SDK 35.
