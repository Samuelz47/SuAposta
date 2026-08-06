# 0.2 — Define test strategy and Definition of Done

## Context

Tests must be designed before implementation and final QA must audit evidence, not just a green command.

## Objective

Define test levels, mock policy, Red/Green evidence, approved-test changes, and the completion/QA standard.

## Acceptance criteria

- [x] Test levels and required use cases are documented.
- [x] RestAssured, Testcontainers, mock, naming, and Given/When/Then policies are documented.
- [x] Red evidence and approved-test change policy are documented.
- [x] Definition of Done and a mandatory QA verdict format are documented.

## Boundary and negative cases

- [x] Green tests alone cannot produce QA approval.

## Out of scope

- Selecting coverage targets or adding test dependencies.

## Dependencies

- Task 0.1.

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
