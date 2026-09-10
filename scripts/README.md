# Project Scripts

Project-specific development and maintenance scripts live here. Spectra runtime commands are provided by `./spectra/bin/spectra` and documented under `spectra/docs/`.

## Available Script

| Script | Purpose | When to use |
|---|---|---|
| `check-demo-boundary.sh` | Rejects pro-only endpoint and capability markers in `src/main/java` | Every push and pull request |

## Spectra Validation

```bash
./spectra/bin/spectra check
./spectra/bin/spectra verify
```

CI runs `spectra check` plus `bash scripts/check-demo-boundary.sh`. Run `spectra verify` locally for readiness reporting; the current Lite runtime blocks it because Full-profile governance inputs are absent.
