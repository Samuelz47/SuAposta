# 0.1 — Document the TDD delivery workflow

## Context

The project is being restarted with documentation-first, human-gated TDD. See `docs/development-workflow.md` and `AGENTS.md`.

## Objective

Establish the roles, artifacts, approval gates, and lifecycle for every future task.

## Acceptance criteria

- [x] The sequence from specification through QA and commit is documented.
- [x] Test, implementation, QA, and human responsibilities are separated.
- [x] Approved tests are protected from implementation-driven weakening.
- [x] Every task records approval and evidence.

## Boundary and negative cases

- [x] A perceived invalid approved test stops implementation pending human review.

## Out of scope

- Automating human approval or creating application code.

## Dependencies

- None.

## Expected tests

- Documentation review; no automated test is meaningful.

## Definition of Done

Documentation-only exception: automated Red/Green evidence is not applicable. All documentation criteria are reviewed by the human.

## Status and evidence

| Gate | Decision / evidence | Date / approver |
| --- | --- | --- |
| Specification approved | Incorporated from the restart requirements | Pending human confirmation |
| Tests in Red | Not applicable: documentation-only | — |
| Tests approved | Not applicable | — |
| Implementation in Green | Documentation created | Pending human review |
| Human diff review | Pending | — |
| QA verdict | Pending | — |

### Approved-test changes

None.

### QA report

Pending.
