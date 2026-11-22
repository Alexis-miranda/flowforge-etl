# Roadmap and release boundaries

FlowForge is an active personal development project and a demo candidate, not a production-certified service.

## Before a safe public demo

- [ ] Connect the dashboard to real API data and display real execution/step states.
- [ ] Design secure input-file upload and worker-accessible storage; do not rely on a browser-supplied local filesystem path.
- [ ] Add authentication and authorization.
- [ ] Define HTTPS termination, CORS policy, request limits, and rate limiting.
- [ ] Add deployment configuration for API, worker, SQL Server, RabbitMQ, and shared/object storage.
- [ ] Add structured logs, health/readiness checks, metrics, alerting, and operational runbooks.
- [ ] Define backup/restore, retention, dead-letter inspection/replay, and artifact cleanup procedures.
- [ ] Validate cancellation and fencing at every side-effect boundary; define multi-instance outbox claiming.
- [ ] Add deployed end-to-end tests using synthetic data.
- [ ] Integrate SFTP publication into the Cash workflow if required by the target use case.
- [ ] Confirm official input/output specifications, business mappings, and operational requirements.

## Outside the first demo scope

Kafka, multi-tenant administration, a scheduling UI, and additional pipeline types are not part of the current first demo scope.

Before using FlowForge with real business data, review database permissions, credentials, network exposure, data handling, recovery behavior, and operational requirements.
