# Gym Buddy Native (Kotlin + Jetpack Compose)

This is a **native** Android app with an actual Jetpack Compose interface, not a WebView. Built independently from the existing online wrapper, on the `native-development` branch. It connects directly to the existing Supabase project. **The build requires a publishable/anon key**, configured as GitHub repository secret `GYMBUDDY_SUPABASE_PUBLISHABLE_KEY`; do not use a service-role key. If the secret is not configured, the APK compiles but sign-in is disabled.

Current functionality and gaps are listed in the project report. Do not merge to main or distribute as production-ready without authentication, on-device and data-isolation testing.
