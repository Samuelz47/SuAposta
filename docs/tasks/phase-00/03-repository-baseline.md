# 0.3 — Restore repository tracking and project baseline

## Context

The previous Git repository was intentionally deleted. Documentation now defines the new delivery workflow.

## Objective

Initialize a clean repository baseline after human review of the documentation.

## Acceptance criteria

- [ ] A new Git repository is initialized only with human authorization.
- [ ] The first commit contains the approved documentation baseline and no credentials.
- [ ] Ignore rules protect local secrets and build outputs.

## Boundary and negative cases

- [ ] No deleted historical repository or remote is assumed recoverable.

## Out of scope

- Creating a GitHub remote, pushing, or writing application code.

## Dependencies

- Tasks 0.1 and 0.2 approved by the human.

## Expected tests

- Repository-state inspection; no application test is applicable.

## Definition of Done

Apply `docs/definition-of-done.md` with the documented infrastructure/repository exception.

## Status and evidence

| Gate | Decision / evidence | Date / approver |
| --- | --- | --- |
| Specification approved | Pending | — |
| Tests in Red | Not applicable until repository tooling exists | — |
| Tests approved | Pending | — |
| Implementation in Green | Pending | — |
| Human diff review | Pending | — |
| QA verdict | Pending | — |

### Approved-test changes

None.

### QA report

Pending.
