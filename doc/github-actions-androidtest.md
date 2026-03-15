# Android Instrumentation Tests on PRs (GitHub Actions)

## 1) Keep `google-services.json` out of Git
This project already ignores `google-services.json`. Keep it that way and do not commit it.

## 2) Add encrypted secret in GitHub
Encode your local JSON as base64 and store it as a repository secret named:

- `GOOGLE_SERVICES_JSON_B64`

PowerShell (from repo root):

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("app/google-services.json")) | Set-Clipboard
```

Then in GitHub:

1. `Settings` -> `Secrets and variables` -> `Actions`
2. `New repository secret`
3. Name: `GOOGLE_SERVICES_JSON_B64`
4. Value: paste clipboard content

## 3) Workflow location
The CI workflow is:

- `.github/workflows/android-instrumentation-tests.yml`

It runs `./gradlew connectedDebugAndroidTest` on pull requests and uploads test reports as artifacts.

## 4) Make failed tests block merge
Enable branch protection for your default branch:

1. `Settings` -> `Branches` -> `Add rule`
2. Select your branch (for example `main`)
3. Enable `Require status checks to pass before merging`
4. Add required check:
   - `connectedDebugAndroidTest`
5. Save rule

When a test fails, GitHub marks the check red and PR merge is blocked.

## 5) Important security note for fork PRs
GitHub does not expose secrets to workflows from fork pull requests (by design).
So instrumentation tests that need Firebase config cannot run in untrusted fork context with secrets.

Current workflow behavior:

- Same-repo PRs: run instrumentation tests with secret-injected `google-services.json`
- Fork PRs: skip instrumentation tests to protect secrets

If you need to test fork PRs with secrets, do it only in a trusted maintainer-controlled flow (never expose secrets to untrusted PR code).
