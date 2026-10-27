# Pulse 
[![CI](https://github.com/njbrunner/pulse/actions/workflows/ci.yml/badge.svg)](https://github.com/njbrunner/pulse/actions/workflows/ci.yml)

Pulse is an Android app that gives you a simple, at-a-glance view of your personal health data from a home-screen widget.

The widget is the main product. The companion app is kept minimal and is mostly used for authorization, configuration, sync status, and settings. Pulse is not meant to replace Google Health or dedicated fitness apps.

> **Status:** Early development. The v0.1 MVP is tracked in the [`v0.1 - MVP`](https://github.com/njbrunner/pulse/milestone/1) milestone.

## Development

### Requirements
- JDK 21
- Android SDK 36 (compileSdk)
- Gradle 8.13 (wrapper included)

### Build
```bash
./gradlew assembleDebug
```

### Test
```bash
./gradlew test
```

### Lint
```bash
./gradlew lint
```

## Widget

### Daily view

- Sleep duration
- Pulse Sleep Score
- Pulse Recovery Score
- Steps
- Resting heart rate
- Water intake

### Weekly view

- Average sleep duration
- Average Pulse Sleep Score
- Average daily steps
- Resting heart rate trend
- HRV trend

You can switch between the Daily and Weekly views directly on the widget.

## Pulse Scores

Pulse calculates its own Sleep and Recovery scores. These are **not** official Google or Fitbit scores, and they are not medical or diagnostic measurements.

**Pulse Sleep Score (0–100)**

| Component  | Weight |
|------------|--------|
| Duration   | 60%    |
| Efficiency | 25%    |
| Continuity | 15%    |

**Pulse Recovery Score (0–100)**

| Component                          | Weight |
|------------------------------------|--------|
| HRV vs. personal baseline          | 40%    |
| Resting HR vs. personal baseline   | 30%    |
| Pulse Sleep Score                  | 30%    |

## How It Works

```text
Google Health API
    ↓
Health data layer
    ↓
Local cache / calculations
    ↓
Android widget
```

- Health data is read from Google Health. Pulse requests only the permissions it needs: sleep, steps, resting heart rate, HRV, and hydration.
- Data is cached on the device, and the widget renders from that cache.
- There is no backend, cloud database, or account system.

## Out of Scope

Workout tracking, weight/body composition, detailed sleep analysis, SpO2/respiratory dashboards, medical or diagnostic features, and social features.

## Contributing

See [`AGENTS.md`](AGENTS.md) for development, testing, and Git workflow guidelines.
