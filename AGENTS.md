# Agent Operating Notes — Settlement

> **All commit messages, PR titles, PR descriptions, release notes, and documentation MUST be written in English.** No Korean in any project artifact. Code comments must be English only. Korean is allowed only for user-facing UI strings (app name, in-app labels) since this is a Korean app.

> Recurring problems and verified solutions for this repo. Every agent MUST read this file first.

## Language Policy

| Artifact | Language |
|----------|----------|
| Commit messages | English only |
| PR title | English only |
| PR description | English only |
| Release notes | English only |
| README.md | English only |
| AGENTS.md | English only |
| Code comments | English only |
| User-facing UI strings (app name, in-app labels) | Korean allowed |

## Release Management

- The APK is distributed via **GitHub Releases**, never committed to the repo (`*.apk` is gitignored). Do not commit `.apk` files.
- The "latest" download URL always resolves to the newest release tag, so existing links never break:
  `https://github.com/kimmaze027/Settlement/releases/latest/download/settlement.apk`
- To ship a new version: bump the tag (e.g. `v1.1.0`), build the APK, then `gh release create <tag> <apk> --title <tag> --notes "..."` (notes in English).

## App Identity

- App display name: **Settlement** (shown under the launcher icon). Set in BOTH `capacitor.config.ts` (`appName`) and `android/app/src/main/res/values/strings.xml` (`app_name`, `title_activity_main`) — they must match, or the next `npx cap sync android` will overwrite `strings.xml`.

## Project Basics

| Item | Value |
|------|-------|
| Repo | `kimmaze027/Settlement` (public) |
| App | Settlement |
| Stack | Capacitor 8 (Android WebView wrapper) |
| Backend | loads `https://www.kimmiro.com` |

## Pre-release checklist (MANDATORY)

- **Validate `www/index.html` JS syntax before every release.** A single missing `}` silently breaks the *entire* WebView script (no function runs, login included) with no build error. Extract the `<script>` body and run `node --check`, or the app will ship dead:
  ```sh
  node -e "const fs=require('fs');const h=fs.readFileSync('www/index.html','utf8');fs.writeFileSync('/tmp/app.js',h.match(/<script>([\s\S]*?)<\/script>/)[1])" && node --check /tmp/app.js
  ```
  - History: v1.3.0 dropped `registerPush()`'s closing brace → v1.3.0/v1.4.0 shipped with a fully broken app (users could not log in). Caught only by booting an emulator.
- **Boot an emulator (or device) and confirm the login screen is interactive** before publishing. `cap sync` + a green gradle build do NOT catch JS syntax errors.
- Keep `@codetrix-studio/capacitor-google-auth` patched via `bun patchedDependencies` (see `patches/`). The patch makes `refresh()` use `silentSignIn()` for a fresh idToken and builds the client in `load()`.
