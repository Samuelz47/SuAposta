# [Task ID] — [Title]

## Context

Relevant documents and current-system context.

## Objective

One observable outcome.

## Acceptance criteria

- [ ] Criterion.

## Boundary and negative cases

- [ ] Case.

## Out of scope

- Item deliberately excluded.

## Dependencies

- Prior task, decision, or service contract.

## Expected tests

- Level and behavior to prove. See `docs/testing-strategy.md`.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

Current status must match the corresponding row in `docs/roadmap.md`. Allowed lifecycle transitions are `PLANNED` -> `TESTS IN REVIEW` -> `IMPLEMENTATION IN PROGRESS` -> `QA IN REVIEW` -> `DONE`. The only exception is an explicit human instruction allowing the implementation agent to transition directly from `PLANNED` to `QA IN REVIEW`. No other transition or status jump is allowed.

| Current status | Pending gate |
| --- | --- |
| PLANNED | Test agent creates Red tests. |
| TESTS IN REVIEW | Human approves tests. |
| IMPLEMENTATION IN PROGRESS | Human approves implementation diff. |
| QA IN REVIEW | QA audit and human approval of QA outcome. |
| DONE | None. |

| Gate | Decision / evidence | Date / approver |
| --- | --- | --- |
| Specification provided | Pending | — |
| Tests in Red | Pending | — |
| Tests approved | Pending | — |
| Implementation in Green | Pending | — |
| Human diff review | Pending | — |
| QA verdict | Pending | — |

For new implementation files, record that only `git add -N <new-file>` was used before QA. Regular staging commands are prohibited.

### Approved-test changes

None. Any change requires a human decision, reason, and affected criterion.

### QA report

Use the mandatory format in `docs/definition-of-done.md`.
