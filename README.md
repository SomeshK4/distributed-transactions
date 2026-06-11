# Distributed Transactions

A minimal Spring Boot 4 application that demonstrates **distributed (XA) transactions** across two heterogeneous resource managers:

| Resource    | Role                | XA driver                           |
|-------------|---------------------|-------------------------------------|
| PostgreSQL  | Relational database | `org.postgresql.xa.PGXADataSource`  |
| ActiveMQ Artemis | JMS broker     | `ActiveMQXAConnectionFactory`       |

A single `@Transactional` method writes a row **and** publishes a message. Both branches either commit together or roll back together, coordinated by **Atomikos** via the **Jakarta JTA API**.

---

## Prerequisites

| Tool                  | Version              | Notes                                                                                    |
|-----------------------|----------------------|------------------------------------------------------------------------------------------|
| **JDK**               | 25                   | Set `JAVA_HOME` accordingly. The project compiles to `--release 25`.                     |
| **Maven**             | Bundled wrapper      | Use `./mvnw` / `mvnw.cmd` — no system Maven needed.                                      |
| **Docker + Compose**  | 20.10+               | Used to start Postgres and Artemis locally.                                              |
| **curl** (or HTTP client) | any              | For hitting the REST endpoint.                                                           |
| **OS**                | Windows / Linux / macOS | The instructions below use PowerShell on Windows. Translate paths for *nix as needed. |

### Spring Boot / library versions

| Library              | Version |
|----------------------|---------|
| Spring Boot          | 4.0.6   |
| Atomikos             | 6.0.1 (Jakarta classifier) |
| Hibernate / JPA      | 7.x (via Boot BOM) |
| Artemis client       | Jakarta variant (via `spring-boot-starter-artemis`) |
| PostgreSQL driver    | Via Boot BOM |

---

## Architecture

```
┌──────────────────┐    @Transactional    ┌──────────────────────────────┐
│ OrderController  │ ───────────────────► │ OrderService.createOrder()   │
└──────────────────┘                      │  ├── repository.save(...)    │  ──► Postgres (XA branch)
                                          │  └── jmsTemplate.send(...)   │  ──► Artemis (XA branch)
                                          └──────────────────────────────┘
                                                       │
                                                       ▼
                                          ┌──────────────────────────────┐
                                          │   JtaTransactionManager      │
                                          │  (Spring) ──► Atomikos JTA   │
                                          └──────────────────────────────┘
                                                       │
                                                       ▼
                                          ┌──────────────────────────────┐
                                          │  Two-phase commit / rollback │
                                          └──────────────────────────────┘
```

Key configuration classes:

| Class                       | Responsibility                                                                                  |
|-----------------------------|-------------------------------------------------------------------------------------------------|
| `JtaConfig`                 | Wires Atomikos's `UserTransactionManager` + `JtaTransactionManager` + Hibernate JTA platform.   |
| `XaDatasourceConfig`        | Wraps `PGXADataSource` in `AtomikosDataSourceBean`. Reads `spring.datasource.*`.                |
| `ArtemisConfig`             | Wraps `ActiveMQXAConnectionFactory` in `AtomikosConnectionFactoryBean`. Reads `spring.artemis.*`.|
| `OrderService.createOrder`  | The `@Transactional` method that drives the XA transaction.                                     |

---

## Setup

### 1. Clone & build

```powershell
git clone <repo-url> demo-xa
cd demo-xa
.\mvnw.cmd -DskipTests clean package
```

### 2. Start the infrastructure

```powershell
docker compose -f docker-compose\docker-compose.yml up -d
```

This brings up:

- **Postgres 16** on `localhost:5432`, db `swift_ref`, user/pass `postgres` / `postgres`, started with `max_prepared_transactions=100` so XA `PREPARE TRANSACTION` is allowed.
- **Artemis** on `localhost:61616` (broker) + `8161` (web console), user/pass `artemis` / `artemis`.

> ⚠️ If you ever change the broker credentials, recreate the container with `down -v` first — Artemis bakes the credentials into its instance dir on the very first boot and ignores env vars on subsequent starts.

Verify both are up:

```powershell
docker exec -it postgres-xa psql -U postgres -d swift_ref -c "SHOW max_prepared_transactions;"
# expected: 100

# Then open http://localhost:8161/console and log in with artemis / artemis
```

### 3. Run the app

```powershell
.\mvnw.cmd spring-boot:run
```

The app starts on `http://localhost:8080`.

---

## Usage

The REST API exposes a single endpoint:

```
POST /orders?product=<name>&fail=<true|false>
```

### Happy path (XA commit)

```powershell
curl -X POST "http://localhost:8080/orders?product=book"
# ► OK
```

Result:

- A new row is inserted into the `orders` table in Postgres.
- A `"created:book"` message is published to the `order.queue` in Artemis.
- The embedded `OrderListener` consumes the message and logs it.

### Forced rollback (XA rollback)

```powershell
curl -X POST "http://localhost:8080/orders?product=book&fail=true"
# ► 500 Internal Server Error
```

Result — **both branches roll back atomically**:

- **No** row in Postgres.
- **No** message in Artemis.

Verify:

```powershell
docker exec -it postgres-xa psql -U postgres -d swift_ref -c "SELECT * FROM orders;"
# Web console ► Queues ► order.queue ► message count unchanged
```

---

## Configuration reference

All connection details live in `src/main/resources/application.properties`:

```ini
# --- Postgres (XA) ----------------------------------------------------------
spring.datasource.url=jdbc:postgresql://localhost:5432/swift_ref
spring.datasource.username=postgres
spring.datasource.password=postgres

# --- Artemis (XA) -----------------------------------------------------------
spring.artemis.mode=native
spring.artemis.broker-url=tcp://localhost:61616
spring.artemis.user=artemis
spring.artemis.password=artemis
```

Override per environment via the usual Spring relaxed-binding env vars:

```
SPRING_DATASOURCE_URL=...
SPRING_DATASOURCE_USERNAME=...
SPRING_DATASOURCE_PASSWORD=...
SPRING_ARTEMIS_BROKER_URL=...
SPRING_ARTEMIS_USER=...
SPRING_ARTEMIS_PASSWORD=...
```

---

## Troubleshooting

| Symptom                                                                                 | Cause / Fix                                                                                                                                                  |
|-----------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `PGXAException: Error preparing transaction`                                            | Postgres `max_prepared_transactions = 0`. Ensure the `command:` override in `docker-compose.yml` is applied; recreate the container with `down -v` then `up`.|
| `NoClassDefFoundError: TransactionManagerCustomizers`                                   | Atomikos's auto-config is incompatible with Boot 4 — already worked around by excluding `transactions-spring-boot3` in the POM.                              |
| `ClassNotFoundException: com.atomikos.icatch.jta.hibernate4.AtomikosPlatform`           | Hibernate auto-detection of Atomikos's old companion class. Solved by `JtaConfig#jtaPlatformCustomizer` registering `SpringJtaPlatform`.                     |
| Artemis rejects `artemis` / `artemis`                                                   | First-boot env vars were ignored. Run `docker compose -f docker-compose\docker-compose.yml down -v` and `up -d` to re-initialise the broker instance dir.    |
| IntelliJ shows `Cannot resolve method "init"/"close"` on `@Bean(initMethod=..)`         | Stale model cache — `File ► Invalidate Caches…` or `Maven ► Reload All Maven Projects`. Maven builds cleanly.                                                |

---

## Project layout

```
demo-xa/
├── docker-compose/
│   └── docker-compose.yml          ← Postgres + Artemis
├── src/main/java/com/example/demoxa/
│   ├── DemoXaApplication.java
│   ├── config/
│   │   ├── JtaConfig.java          ← Atomikos + JtaTransactionManager
│   │   ├── XaDatasourceConfig.java ← Postgres XA pool
│   │   └── ArtemisConfig.java      ← Artemis XA pool + JMS template + listener factory
│   └── orders/
│       ├── OrderController.java    ← REST endpoint
│       ├── OrderService.java       ← @Transactional method
│       ├── OrderEntity.java
│       ├── OrderRepository.java
│       └── OrderListener.java      ← @JmsListener
├── src/main/resources/
│   └── application.properties
├── pom.xml
└── README.md
```

---

## License

For demonstration purposes only.

