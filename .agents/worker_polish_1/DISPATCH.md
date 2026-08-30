## 2026-08-29T03:25:12Z
You are teamwork_preview_worker (Build Polish Worker).
Working directory: c:\Users\abhis\Downloads\MUSIC APP\.agents\worker_polish_1

File Ownership (you exclusively own and write these files):
- `spotify/build.gradle.kts`

Reference Files (Read-only):
- `c:\Users\abhis\Downloads\MUSIC APP\.agents\ORIGINAL_REQUEST.md`
- `c:\Users\abhis\Downloads\MUSIC APP\AGENTS.md`
- `c:\Users\abhis\Downloads\MUSIC APP\PROJECT.md`
- `c:\Users\abhis\Downloads\MUSIC APP\.agents\reviewer_1\handoff.md`

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Core Tasks:
1. In `spotify/build.gradle.kts`, append:
   ```kotlin
   tasks.test {
       useJUnit()
   }
   ```
2. Run the test suite:
   ```powershell
   .\gradlew :spotify:test --warning-mode all
   ```
   Confirm all test tasks run and pass 100%.
3. Run release build:
   ```powershell
   .\gradlew :app:assembleRelease --warning-mode all --no-parallel
   ```
   Confirm build is successful.
4. Verify 0 `@Suppress` and 0 `@SuppressLint` across all repository code.
5. Write your handoff report to `c:\Users\abhis\Downloads\MUSIC APP\.agents\worker_polish_1\handoff.md` and message the orchestrator.
