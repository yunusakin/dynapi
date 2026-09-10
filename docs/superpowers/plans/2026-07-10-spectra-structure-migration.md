# Spectra Structure Migration Implementation Plan

> **For agentic workers:** Execute inline; preserve project state and validate every destructive transition.

**Goal:** Consolidate legacy Spectra state under `spectra/sdd/` and delete the obsolete root `sdd/` tree.

**Architecture:** The installed `spectra/` directory remains authoritative. Legacy memory-bank content overwrites only corresponding generated placeholders, while new-only Spectra runtime and discovery files remain intact.

**Tech Stack:** Spectra CLI, Markdown/YAML state, shell validation.

## Global Constraints

- Preserve all established project decisions, progress, backlog, and technical specifications.
- Do not invent governance approvals.
- Delete `sdd/` only after copying its project memory.

### Task 1: Merge state

- [ ] Copy legacy memory-bank content into `spectra/sdd/memory-bank/`.
- [ ] Confirm new-only files remain present.

### Task 2: Refresh integration

- [ ] Run the supported Spectra update/migration command.
- [ ] Refresh repository agent instructions for the new paths.

### Task 3: Retire legacy tree

- [ ] Delete root-level `sdd/`.
- [ ] Scan active repository instructions for stale legacy references.

### Task 4: Verify

- [ ] Run `spectra check`.
- [ ] Run `spectra verify`.
- [ ] Update migrated progress and active context with the outcome.

