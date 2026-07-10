# Getting a downloadable APK via GitHub Actions

This project now includes everything needed to build automatically in the cloud —
no Android Studio required for this part.

## One-time setup

1. Create a new repository on github.com (public or private, either works)
2. From this unzipped `jpapp/` folder, run:
   ```
   git init
   git add .
   git commit -m "initial project"
   git branch -M main
   git remote add origin https://github.com/<your-username>/<your-repo>.git
   git push -u origin main
   ```

## Getting the APK

1. On github.com, open your repo → the **Actions** tab
2. You'll see a "Build APK" run in progress (it starts automatically on every push to `main`)
3. Wait for the green checkmark (a few minutes)
4. Click into that run → scroll to **Artifacts** at the bottom → download `jpreader-debug-apk`
5. It downloads as a `.zip` — unzip it to get `app-debug.apk`

## Installing it on your phone

1. Get that `.apk` file onto your phone (easiest: upload it to Google Drive from your
   computer, then open the Drive app on your phone and download it — or just do the
   whole thing from your phone's browser if you did the GitHub steps there too)
2. Tap the downloaded `.apk` file
3. Android will ask to allow installs from that source (Files app / Drive / Chrome,
   whichever you used) — allow it, this is a one-time permission per app
4. Tap Install

This is a debug build (self-signed, not from the Play Store), so Android will show a
warning about installing from an unknown source — that's expected for any app you
sideload yourself, not a sign anything's wrong.

## Re-running the build

Any time you (or I) change the code and push to `main`, GitHub Actions builds a fresh
APK automatically. You can also trigger it manually from the Actions tab without a new
push (the "Run workflow" button, thanks to `workflow_dispatch` in the workflow file).
