# Contributing

## Branch and commit rules
- Small, focused commits.
- No hidden fallback behavior.
- Any parser logic change must include tests.

## Local validation
```powershell
./gradlew test build
./gradlew runExportSim -PsimArgs="tools/_sim_case.json --show-order"
```

## Export/parser changes checklist
- Keep strict geometry (`-2x/-1x`) unless explicitly changed in docs + tests.
- Add/adjust unit tests in `src/test/java/.../feature/export`.
- Keep debug marker line with active logic.

## Release safety
- Do not ship if CI is red.
- Keep user-facing RU messages readable (no mojibake).
