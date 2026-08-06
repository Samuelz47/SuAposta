# Task Specifications

Every implementation task has one file in `phase-XX/`, created from `TEMPLATE.md` before test creation. The file is the handoff artifact between human, test, implementation, and QA roles; it is not a progress diary.

Keep each task small enough to review in one diff. Link only the documents needed for the behavior. Keep the task status synchronized with `docs/roadmap.md` and update it only through the transitions defined in `docs/development-workflow.md`. Record Red/Green commands, human approvals, approved-test changes, the implementation diff handoff, the `git add -N` evidence when applicable, and the final QA report. If a required gate is missing or contradictory, leave the status unchanged and report the divergence.
