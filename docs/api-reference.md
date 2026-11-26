# REST API reference

The API accepts Cash execution requests through the SQL Server transactional outbox. A scheduled dispatcher publishes pending requests to RabbitMQ; the API does not execute ETL steps itself.

## Configuration

Required environment variables:

- `FLOWFORGE_DB_JDBC_URL`
- `FLOWFORGE_DB_USER`
- `FLOWFORGE_DB_PASSWORD`
- `FLOWFORGE_RABBITMQ_URI`

Optional settings:

| Variable | Default |
| --- | --- |
| `FLOWFORGE_RABBITMQ_QUEUE` | `flowforge.pipeline.requests` |
| `FLOWFORGE_API_HOST` | `127.0.0.1` |
| `FLOWFORGE_API_PORT` | `8080` |
| `FLOWFORGE_API_DISPATCH_INTERVAL_MILLIS` | `1000` |
| `FLOWFORGE_API_DISPATCH_BATCH_SIZE` | `50` |
| `FLOWFORGE_API_PUBLISH_CONFIRM_TIMEOUT_MILLIS` | `5000` |
| `FLOWFORGE_UPLOAD_DIR` | `data/uploads` |
| `FLOWFORGE_OUTPUT_DIR` | `data/output` |
| `FLOWFORGE_BACK_OFFICE_CODE` | `BNYM` |

If `FLOWFORGE_RABBITMQ_DEAD_LETTER_EXCHANGE` is configured, use the same value in the API and worker so their queue declarations match.

## Endpoints

| Method and path | Purpose |
| --- | --- |
| `POST /api/executions` | Submit a Cash execution request. Returns `202 Accepted` with an execution ID and `QUEUED` status. |
| `POST /api/uploads` | Upload a raw CSV request body. Set `X-File-Name` to the URL-encoded filename and `Content-Type: text/csv`. Stores the file, then enqueues a Cash execution. Maximum 25 MiB. |
| `GET /api/executions?limit=25` | List recent executions. Limit defaults to 25 and must be between 1 and 100. |
| `GET /api/executions/{executionId}` | Read request-delivery and execution status. |
| `GET /api/executions/{executionId}/steps` | Read persisted step states; returns an empty list before execution starts. |

## Example request

```json
{
  "pipelineType": "cash",
  "parameters": {
    "input": "/shared/input.csv",
    "backOfficeCode": "BNYM",
    "outputDirectory": "/shared/output",
    "columns": "accountCode,currency,amount",
    "sourceFile": "batch-2026-11-22.csv"
  }
}
```

The required Cash parameters are `input`, `backOfficeCode`, and `outputDirectory`. File accessibility is validated by the worker because the API and worker may run on different hosts.

## Status semantics

- `QUEUED`: the request is waiting in the SQL Server outbox.
- `DISPATCHED`: RabbitMQ confirmed publication, but the worker has not yet created an execution snapshot.
- After the worker starts, the persisted execution status is returned.

## Security and limitations

This is a development API, not a public endpoint. It has no authentication, authorization, TLS termination, rate limiting, or production-grade CORS policy and binds to loopback by default. Do not expose it to an untrusted network.

The outbox dispatcher is a single-process polling loop; multiple API instances need an atomic outbox-claim/lease mechanism. A request dead-lettered before the worker creates an execution snapshot may remain reported as `DISPATCHED`; durable consumer/DLQ outcome tracking is future work.

The upload endpoint stores files beneath `FLOWFORGE_UPLOAD_DIR` and passes the absolute path to the Cash worker. The API and worker must share the same filesystem path (or a mounted shared volume); this first local-development implementation writes to the API host's filesystem. The dashboard's Vite origin is allowed for local development only. Uploads are not authenticated, so keep the API bound to loopback and never expose it to an untrusted network. Uploaded files are retained; configure an appropriate cleanup policy before using real or sensitive data.
