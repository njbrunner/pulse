# Agent Instructions

## Purpose

This file defines how AI coding agents should work in this repository.

Follow existing project architecture and conventions unless the task explicitly requires a change. Prefer small, focused changes and avoid unnecessary complexity.

---

## Project Context

Pulse is an Android application providing a simple, glanceable view of personal health data.

The **Android home-screen widget is the primary product**. The companion application should remain minimal and exist mainly for authorization, configuration, synchronization, and settings.

Pulse is intentionally not a replacement for Google Health or dedicated fitness applications.

### Widget Scope

Daily metrics:

- Sleep duration
- Pulse Sleep Score
- Pulse Recovery Score
- Steps
- Resting heart rate
- Water intake

Weekly metrics:

- Average sleep duration
- Average Pulse Sleep Score
- Average daily steps
- Resting heart rate trend
- HRV trend

Workout tracking is out of scope.

---

## Architecture

Prefer the simplest architecture that reliably supports the widget.

Expected data flow:

```text
Google Health API
    ↓
Health data layer
    ↓
Local cache / calculations
    ↓
Android widget
```

The widget should render from locally available data rather than depend on a live API request.

Keep health-data access, scoring logic, persistence, and presentation reasonably separated.

Do not introduce a backend, cloud database, account system, or major dependency without a concrete requirement.

Use Android platform and Jetpack functionality where practical.

---

## Health Data

Use supported Google Health APIs as the external source for health data.

Request only permissions required by Pulse.

Treat health information as sensitive. Do not expose real health data, credentials, access tokens, API keys, or other secrets in:

- Git history
- Issues or pull requests
- Logs
- Tests or fixtures
- Documentation

Use synthetic health data for tests.

### Pulse Scores

Sleep Score and Recovery Score are calculated by Pulse and must never be represented as official Google or Fitbit scores.

Initial Sleep Score:

- Duration: 60%
- Efficiency: 25%
- Continuity: 15%

Initial Recovery Score:

- HRV vs. personal baseline: 40%
- Resting HR vs. personal baseline: 30%
- Pulse Sleep Score: 30%

Do not change these algorithms unless explicitly required by an issue.

Do not make medical or diagnostic claims based on Pulse data.

---

## Development

Before implementing an issue:

1. Read the complete issue and acceptance criteria.
2. Inspect relevant existing code.
3. Check for related issues or pull requests.
4. Identify appropriate tests.
5. Create a concise implementation plan.

Prefer:

- Small, focused changes.
- Existing project patterns.
- Clear Kotlin.
- Strong typing.
- Appropriate automated tests.
- Minimal dependencies.

Avoid:

- Unrelated refactoring.
- Speculative abstractions.
- Premature optimization.
- Scope expansion.
- Changing unrelated behavior.
- Weakening tests to make code pass.

Do not claim something works unless it has actually been validated.

---

## Testing

Prefer test-driven development when practical.

Add appropriate tests for new behavior. Tests should validate behavior rather than implementation details.

Use deterministic synthetic data when testing health calculations.

Before considering work complete, run all applicable:

- Unit tests.
- Instrumented tests when appropriate.
- Lint/static analysis.
- Build validation.
- Manual verification when automation is insufficient.

Use the project's Gradle wrapper.

If something cannot be tested, clearly state what was not tested and why.

---

## Git Workflow

Never commit directly to `main`.

For substantive issue work:

1. Ensure `main` is current.
2. Create a dedicated issue/feature branch.
3. Implement and test the change.
4. Review `git status` and `git diff`.
5. Commit the completed work.
6. Push the branch.
7. Create or update the pull request.
8. Verify the local working tree is clean.

Creating branches, committing completed work, pushing, and creating/updating the corresponding PR are normal workflow steps. **Do not ask the user for permission to perform them.**

Keep each branch focused on one issue.

Before committing, verify that:

- Only intended files changed.
- No secrets or personal health data are present.
- No unrelated changes are included.

Use normal Git operations for repository files. Use GitHub API/MCP for issues, pull requests, comments, labels, reviews, and other repository metadata.

Do not use remote file modifications in a way that leaves the local checkout out of sync.

Never discard existing local changes without first determining whether they are user-created or otherwise need to be preserved.

---

## Pull Requests

Before creating or updating a PR:

1. Review the complete diff against `main`.
2. Verify the issue acceptance criteria.
3. Run applicable validation.
4. Confirm the branch contains only relevant changes.
5. Confirm the working tree is clean.

PR descriptions should briefly include:

- What changed.
- Important implementation decisions.
- Testing performed.
- Known limitations or risks.

Reference the issue and use `Closes #<issue>` when the PR fully resolves it.

Do not claim tests passed unless they were actually run.

---

## Selecting Work

When asked what to work on next, consider:

- Current repository state.
- Open issues.
- Dependencies and prerequisites.
- Open pull requests.
- Existing or partially completed implementation.

Do not simply choose an issue based on issue number or age.

Explain briefly why the recommended issue should come next.

---

## Scope Control

Do not add functionality simply because health data is available.

Unless explicitly requested, avoid adding:

- Workout tracking.
- Weight/body-composition tracking.
- Detailed sleep analysis.
- SpO2 or respiratory dashboards.
- Medical/diagnostic functionality.
- Social features.
- Unnecessary cloud infrastructure.

Keep Pulse focused on its daily and weekly widget experience.

---

## Clarification

Ask for clarification when requirements are materially ambiguous, architectural choices have significant consequences, or a change would meaningfully expand scope.

Do not ask for clarification about minor implementation decisions that can be resolved from the issue, existing code, Android conventions, or this file.

Do not ask for permission to perform normal Git workflow steps defined above.

---

## Completion

An issue is complete when:

- Acceptance criteria are satisfied.
- Appropriate tests and validation pass.
- No secrets or personal health data were introduced.
- Changes are committed and pushed.
- The PR accurately describes the work.
- The local checkout matches the pushed state.
- The working tree is clean.

If something remains incomplete or unverified, report it rather than representing the issue as complete.
