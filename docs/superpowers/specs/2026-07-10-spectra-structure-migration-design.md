# Spectra Structure Migration Design

## Goal

Make `spectra/sdd/` the sole Spectra specification and project-memory root, then remove the legacy root-level `sdd/` tree.

## Migration Strategy

- Copy the established project memory from `sdd/memory-bank/` over the corresponding files in `spectra/sdd/memory-bank/`.
- Preserve files that exist only in the new Spectra structure, including discovery documents, implementation brief, archives, and system runtime files.
- Use the installed Spectra CLI to refresh/migrate runtime metadata and regenerate the Codex adapter when supported.
- Remove the legacy root-level `sdd/` directory after the merged target is confirmed.
- Update repository-facing instructions so they reference the canonical Spectra layout rather than deleted paths.

## Validation

Run `spectra check`, `spectra verify`, and a stale-path scan. Record any remaining governance blocker explicitly rather than fabricating approval state.

