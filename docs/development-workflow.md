# Development Workflow

## Purpose

This project uses human-controlled, test-driven delivery. Documentation specifies behavior; tests make the behavior executable; implementation makes approved tests pass; QA independently audits the result.

No agent is authorized to replace a human approval gate.

## Workflow for every task

1. **Small planning:** create or update one task specification in `docs/tasks/` from the template.
2. **Behavioral specification:** document context, objective, acceptance criteria, boundaries, dependencies, and scope exclusions.
3. **Human approval of specification:** the task may proceed to tests only after the human approves its behavior.
4. **Tests in Red:** the test agent adds tests derived from the approved specification and records the Red evidence.
5. **Human approval of tests:** the human reviews the tests before production implementation begins.
6. **Implementation in Green:** the implementation agent changes the smallest necessary production code to satisfy approved tests.
7. **Refactoring:** improve code only while preserving behavior and keeping the approved suite green.
8. **Human diff review:** the human reviews the implementation diff and decides whether it may proceed.
9. **Independent QA audit:** the QA agent uses the original task, criteria, diff, tests, and relevant documents to issue a verdict.
10. **Corrections, relevant full suite, commit and PR:** resolve blockers, rerun the required suite, then commit only after human approval and a QA approval.

## Input contract per role

| Role | Receives | Must not rely on |
| --- | --- | --- |
| Human/documentation | Product decision and domain context | An agent's inferred requirements |
| Test agent | Approved task, relevant docs, test strategy, current structure | A ready-made implementation or proposed production solution |
| Implementation agent | Approved task, approved tests, architecture, current code | Permission to rewrite tests to fit its solution |
| QA agent | Original task, acceptance criteria, relevant docs, test changes, implementation diff, command evidence | The implementer's self-assessment |

Use a separate focused session for each role. Pass artifacts and facts, not private reasoning or persuasive handoffs.

## Human gates

The following transitions require explicit human approval:

- specification -> test creation;
- Red tests -> implementation;
- implementation/refactoring -> final QA;
- QA approval -> commit/PR;
- any change to an approved test.

Record the approver, date, and decision in the task file's Status section. Do not infer approval from silence.

## When tests and implementation disagree

Approved tests represent the accepted behavior. The implementation agent must stop the affected work when it finds a contradictory test, acceptance criterion, or documented contract. It must report the evidence, name the affected rule, and wait for a human decision. See the protected-test rule in `AGENTS.md` and the change policy in `testing-strategy.md`.

## Task lifecycle

```text
PLANNED
  -> SPECIFICATION APPROVED
  -> TESTS IN REVIEW (Red)
  -> TESTS APPROVED
  -> IMPLEMENTATION IN PROGRESS (Green)
  -> HUMAN DIFF REVIEW
  -> QA IN REVIEW
  -> DONE
```

`BLOCKED` may be used only with a specific missing decision, dependency, or inconsistency. State what is needed to continue.

## Commit rule

A commit is the final record of a completed task, not a substitute for review. Before committing, confirm the task meets `docs/definition-of-done.md`, the QA verdict is `APPROVED` (or human explicitly accepts documented reservations), and the diff is confined to scope.
