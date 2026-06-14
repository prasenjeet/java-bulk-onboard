# CSV Format

The upload template is a plain UTF-8 CSV with a header row followed by one
account per line.  Download a pre-filled template from the UI or via:

```
GET http://localhost:8080/api/onboard/template
```

---

## Columns

| # | Column | Required | Type | Notes |
|---|--------|----------|------|-------|
| 1 | `account_id` | ✅ | String | Unique identifier for the account |
| 2 | `account_name` | ✅ | String | Full name or company name |
| 3 | `email` | ✅ | Email | Must match standard email format |
| 4 | `phone` | ✅ | String | Any format — no pattern enforcement |
| 5 | `account_type` | ✅ | Enum | See [Account Types](#account-types) below |
| 6 | `country` | ✅ | String | ISO 3166-1 alpha-2 code (e.g. `US`, `GB`) |
| 7 | `currency` | ✅ | Enum | See [Supported Currencies](#supported-currencies) below |
| 8 | `credit_limit` | ❌ | Decimal | Must be ≥ 0 if provided |
| 9 | `status` | ❌ | String | Free text; e.g. `ACTIVE`, `PENDING` |

Column names are **case-insensitive** and leading/trailing whitespace is stripped.

---

## Account Types

| Value | Description |
|-------|-------------|
| `PERSONAL` | Individual consumer account |
| `BUSINESS` | Small to medium business account |
| `CORPORATE` | Large enterprise account |
| `SAVINGS` | Savings / deposit account |
| `CHECKING` | Chequing / current account |

---

## Supported Currencies

`USD` `EUR` `GBP` `JPY` `CAD` `AUD` `INR` `SGD` `AED` `CHF`

---

## Minimal Example

```csv
account_id,account_name,email,phone,account_type,country,currency,credit_limit,status
ACC001,Alice Johnson,alice@example.com,+1-555-0101,PERSONAL,US,USD,5000.00,ACTIVE
ACC002,Acme Corp,billing@acme.com,+1-555-0200,BUSINESS,US,USD,50000.00,ACTIVE
```

---

## Full Sample (20 rows with intentional errors)

See `sample-data/accounts_sample.csv` in the repository root.

Intentional bad rows in the sample file and the errors they trigger:

| Row | account_id | Problem | Failure message |
|-----|-----------|---------|----------------|
| 11 | ACC011 | `email = not-an-email` | `email format is invalid: not-an-email` |
| 12 | ACC012 | `account_type` is blank | `account_type is required` |
| 13 | ACC013 | `currency = BITCOIN` | `currency must be one of [USD, EUR, …]; got: BITCOIN` |
| 14 | ACC014 | `credit_limit = -500.00` | `credit_limit cannot be negative` |
| 17 | ACC017 | `account_type = INVALID_TYPE` | `account_type must be one of [PERSONAL, …]; got: INVALID_TYPE` |

---

## Validation Rules

The backend validates every row before processing it.  All errors for a row
are concatenated and displayed in the **Failures** table with a `;` separator.

```
email format is invalid: foo@; account_type must be one of [...]; got: UNKNOWN
```

### Email
Must match the pattern `[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}`.

### Account Type
Must exactly match one of the five enum values (case-insensitive internally —
but store your CSVs in UPPER CASE to be explicit).

### Credit Limit
- Must be parseable as a `double`.
- Must be ≥ 0.
- If the column is blank the row is still accepted (treated as 0).

### Random downstream failure (demo only)
~10 % of otherwise-valid records receive a simulated timeout error:
```
Downstream system error: account creation timeout
```
This demonstrates what real integration failures look like in the UI.
Remove the random block in `OnboardingService.processAsync()` when wiring up
a real backend system.

---

## Character Encoding

The file must be **UTF-8**.  If your CSV tool exports in Windows-1252 or
Latin-1, convert it first:

```bash
iconv -f windows-1252 -t utf-8 input.csv > output.csv
```

---

## File Size Limit

Default maximum is **10 MB**, configurable via:

```properties
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
```
