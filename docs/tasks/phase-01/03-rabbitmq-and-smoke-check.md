Leia:

- AGENTS.md
- docs/architecture.md
- docs/events.md
- docs/definition-of-done.md
- docs/infrastructure.md
- docs/tasks/phase-01/03-rabbitmq-and-smoke-check.md

Implemente somente a Task 1.3.

Não configure exchanges, queues, bindings, consumidores, publishers ou eventos de aplicação.

Provisione apenas o RabbitMQ e valide a infraestrutura local completa com PostgreSQL + RabbitMQ.

Siga as validações operacionais e o fluxo de status definidos na própria tarefa.

Não faça commit, push ou merge.

## Status and evidence

| Field | Value |
| --- | --- |
| Current status | DONE |
| Implementation | Completed; direct implementation was explicitly requested by the human |
| Human implementation review | Approved by human, 2026-08-06 |
| QA verdict | APPROVED |

The human approved the implementation diff and the final QA outcome on 2026-08-06. The task is now `DONE`. This implementation did not commit, push, or merge.

### Validation evidence

- Files changed for Task 1.3: `compose.yaml`, `docker/rabbitmq/rabbitmq.conf`, `docs/infrastructure.md`, and this task file. `.env.example` already contained the RabbitMQ environment contract and was not changed.
- `docker compose --env-file .env.example config --quiet`: passed.
- `docker compose config --quiet`: passed with the local `.env`.
- Rendered Compose contains only `postgres` and `rabbitmq` services, `suaposta-network`, and the `postgres-data`/`rabbitmq-data` volumes. No application service or application messaging topology was added.
- RabbitMQ image: `rabbitmq:3.13.7-management`; AMQP `5672`; Management UI `15672`.
- `docker compose up -d`: passed for PostgreSQL + RabbitMQ.
- `docker compose ps`: both services reported `healthy` before and after the non-destructive restart.
- PostgreSQL readiness: `pg_isready` accepted connections; the three existing service databases remained owned by their matching service roles.
- RabbitMQ readiness: `rabbitmq-diagnostics -q ping` and `check_running` passed; listeners reported HTTP `15672` and AMQP `5672`.
- Management UI smoke check: `http://127.0.0.1:15672` returned HTTP `200`.
- Persistence check: `docker compose down` followed by `docker compose up -d` preserved the PostgreSQL databases/marker and restored RabbitMQ healthy; `docker compose down -v` was not executed.
- `git check-ignore -v .env`: passed.
- `git diff --check`: passed.
- QA final verdict: `APPROVED` on 2026-08-06. The scoped human review approved the outcome; no blockers or reservations remain.
- Final human decision: Approved by human on 2026-08-06.
