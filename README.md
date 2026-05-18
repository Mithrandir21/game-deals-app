# game-deals-android-app
A repository for an Android app showing game deals from CheapShark and GamerPower API

#### App explanations
- Architecture: MVVM
- Storage: Room + SharedPreferences
- Separation of code into logical modules (see [MODULES.md](MODULES.md))
- Domain layer separation from API
- Unit testing of standalone logic
- Instrumentation testing of end-to-end journeys

#### Usage
- App can be used as a standalone app

#### Compose stability gate

CI runs `./gradlew debugStabilityCheck` on every PR. It diffs the live build against the committed `<module>/stability/*-debug.stability` baselines and fails the PR on any composable-stability drift. If your PR's `Build` job fails with `❌ Stability check failed!`, see [docs/patterns/compose-correctness.md](docs/patterns/compose-correctness.md#ci-stability-gate-debugstabilitycheck-against-committed-stability-baselines) for the two remediation paths (fix the regression OR regenerate the baseline and commit it with justification).

##### List of improvements
- Instrumentation testing tooling should be improved to allow less boilerplate code and more generalisation and abstraction, allowing faster writing of tests.
- App designs should be done by actual designer.
- Themes and styles could be created for specific elements to allow faster and more uniform styling of the UI elements.
- Pagination could be used for loading items from Room database.

# External Tools
## Code Quality
[![Quality gate](https://sonarcloud.io/api/project_badges/quality_gate?project=Mithrandir21_game-deals-android-app)](https://sonarcloud.io/summary/new_code?id=Mithrandir21_game-deals-android-app)


[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=Mithrandir21_game-deals-android-app&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=Mithrandir21_game-deals-android-app)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=Mithrandir21_game-deals-android-app&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=Mithrandir21_game-deals-android-app)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=Mithrandir21_game-deals-android-app&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=Mithrandir21_game-deals-android-app)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=Mithrandir21_game-deals-android-app&metric=bugs)](https://sonarcloud.io/summary/new_code?id=Mithrandir21_game-deals-android-app)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=Mithrandir21_game-deals-android-app&metric=coverage)](https://sonarcloud.io/summary/new_code?id=Mithrandir21_game-deals-android-app)
[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?project=Mithrandir21_game-deals-android-app&metric=ncloc)](https://sonarcloud.io/summary/new_code?id=Mithrandir21_game-deals-android-app)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=Mithrandir21_game-deals-android-app&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=Mithrandir21_game-deals-android-app)
[![Duplicated Lines (%)](https://sonarcloud.io/api/project_badges/measure?project=Mithrandir21_game-deals-android-app&metric=duplicated_lines_density)](https://sonarcloud.io/summary/new_code?id=Mithrandir21_game-deals-android-app)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=Mithrandir21_game-deals-android-app&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=Mithrandir21_game-deals-android-app)
[![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=Mithrandir21_game-deals-android-app&metric=sqale_index)](https://sonarcloud.io/summary/new_code?id=Mithrandir21_game-deals-android-app)
