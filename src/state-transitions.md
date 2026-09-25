## Convention
- A → STATE - Applied new shipmentState
- I - Ignored, shipmentState unchanged
- R - Rejected

### From Created
| Event | Mark                     |
|---|--------------------------|
| `LABEL_CREATED` | I                        |
| `PICKED_UP` | A → `IN_TRANSIT`         |
| `IN_TRANSIT` | A → `IN_TRANSIT`          | |
| `OUT_FOR_DELIVERY` | A → `IN_TRANSIT`          |         |
| `DELIVERED` | A → `DELIVERED` (forward skip) |
| `EXCEPTION` | A → `EXCEPTION`          |
| `CANCELLED` | A → `CANCELLED`    |

### IN_TRANSIT

| Event | Mark                         |
|---|------------------------------|
| `PICKED_UP` | I           |
| `IN_TRANSIT` | I |
| `OUT_FOR_DELIVERY` | A → `OUT_FOR_DELIVERY`       |
| `DELIVERED` | A → `DELIVERED`              |
| `EXCEPTION` | A → `EXCEPTION`              |
| `CANCELLED` | A → `CANCELLED`        |
| `LABEL_CREATED` | R                    |

### From `OUT_FOR_DELIVERY`

| Event | Mark                                       |
|---|--------------------------------------------|
| `OUT_FOR_DELIVERY` | I                                    |
| `IN_TRANSIT` | I (scan noise; do **not** move back) |
| `DELIVERED` | A → `DELIVERED`                      |
| `EXCEPTION` | A → `EXCEPTION`                      |
| `CANCELLED` | A → `CANCELLED`                      |
| `PICKED_UP`, `LABEL_CREATED` | Rejected                                   |

### From `DELIVERED` (terminal)

| Event | Result |
|---|---|
| `DELIVERED` | I |
| anything else | R reason includes `after_delivered` |

### From `EXCEPTION` (recoverable)

| Event | Result                       |
|---|------------------------------|
| `EXCEPTION` | I                            |
| `IN_TRANSIT` | A → `IN_TRANSIT`       |
| `OUT_FOR_DELIVERY` | A → `OUT_FOR_DELIVERY` |
| `DELIVERED` | A → `DELIVERED`        |
| `CANCELLED` | A → `CANCELLED`        |
| `PICKED_UP` | A → `IN_TRANSIT`       |
| `LABEL_CREATED` | R                     |

### From `CANCELLED` (terminal)

| Event | Result |
|---|---|
| `CANCELLED` | I |
| anything else | R reason includes `cancelled` |

