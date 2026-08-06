# Definition of Done

A task is complete only when all applicable items below have evidence in its task file.

## Required conditions

- The task has an approved behavioral specification and acceptance criteria.
- The test agent created tests in Red and the human approved them before implementation.
- Implementation did not alter approved tests without explicit human authorization.
- All acceptance criteria and documented edge cases are satisfied.
- Focused task tests pass and the relevant full suite passes.
- Required linting and static analysis pass, when configured.
- Affected architecture, domain, API, event, and task documentation is updated.
- The diff contains no unrelated files, refactors, contract changes, or behavior additions.
- The human reviewed the implementation diff.
- The QA agent issued a final verdict with evidence.
- The human approved the QA outcome before commit/PR.

## Final QA audit

The QA agent must independently inspect:

### Requirements

- Were all acceptance criteria met?
- Is anything partial or missing?
- Was behavior added that was not requested?

### Tests

- Would the tests fail for a plausible incorrect implementation?
- Are positive, negative, and edge scenarios covered?
- Are assertions specific and meaningful?
- Is mocking excessive or concealing a real integration issue?
- Do tests validate behavior rather than internal details?

### Code and architecture

- Is there unnecessary duplication or abstraction?
- Are layered architecture and service boundaries respected?
- Does the domain remain independent of frameworks?
- Are exceptions appropriately specific?
- Are transaction, concurrency, idempotency, and decimal-precision concerns handled where applicable?

### Security

- Were secrets or credentials introduced?
- Are inputs validated?
- Are authentication and authorization treated separately?
- Do error messages avoid internal details and sensitive data?

### Scope

- Did the change touch files outside the task scope?
- Did it include unrelated refactors?
- Did it change contracts or documents without an explained requirement?

## Mandatory QA report format

```text
VERDICT: APPROVED | APPROVED WITH RESERVATIONS | REJECTED

Blockers:
- ...

Important issues:
- ...

Non-blocking improvements:
- ...

Evidence:
- commands executed;
- tests passed/failed;
- files inspected;
- acceptance criteria validated.
```

`APPROVED WITH RESERVATIONS` requires explicit human acceptance before a commit. `REJECTED` requires corrections and a new QA audit.
