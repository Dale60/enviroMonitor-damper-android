Android Damper Locator CI bundle

What this adds
- GitHub Actions workflow to build a debug APK on every push/PR to main
- Uploads the APK as a workflow artefact

Requirements in your repo
- Gradle wrapper committed: gradlew, gradlew.bat, gradle/wrapper/*
- Android module at ./app

Files
- .github/workflows/android-ci.yml  (recommended)
- .github/workflows/android-ci-container.yml  (optional, container-based)
- .gitignore  (basic Android ignore list)
