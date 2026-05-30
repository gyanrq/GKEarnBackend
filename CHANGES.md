# EarnX-3 — Changes & Fixes

## v3412-fixed (May 2026)

### 🔴 Critical Fixes

#### 1. Redis Now Optional (Was: Mandatory — Crashed 1GB Server)
- **File:** `application-prod.yml`
- **Problem:** `spring.data.redis.host: ${REDIS_HOST}` had no fallback — app crashed on startup without Redis
- **Fix:** `REDIS_HOST:` (empty default) — CacheConfig already had Caffeine fallback, now it actually works
- **Impact:** App runs perfectly on 1GB RAM without Redis

#### 2. Hikari Pool Size Fixed (Was: 30 — OOM on 1GB)
- **File:** `application-prod.yml`, `application.yml`
- **Problem:** `maximum-pool-size: 30` on 1GB server → each MySQL connection ~5MB → 150MB just for DB pool
- **Fix:** `maximum-pool-size: 5, minimum-idle: 2` for prod
- **Impact:** ~125MB RAM saved

#### 3. MySQL Dialect Deprecated Warning Fixed
- **File:** `application.yml`
- **Problem:** `MySQL8Dialect` deprecated in Hibernate 6.x — shows WARN on every startup
- **Fix:** Changed to `org.hibernate.dialect.MySQLDialect`

#### 4. open-in-view Disabled
- **File:** `application.yml`
- **Problem:** `spring.jpa.open-in-view` defaulted to `true` — keeps DB connections open during view rendering
- **Fix:** `open-in-view: false` — releases connections faster

### 🟡 Important Fixes

#### 5. Fraud Thresholds Now Configurable
- **File:** `application.yml`
- **Problem:** Thresholds hardcoded — required code change + redeploy to adjust
- **Fix:** All fraud thresholds now env-var driven (`FRAUD_HOLD_THRESHOLD`, etc.)

#### 6. Flyway baseline-on-migrate Fixed for Prod
- **File:** `application-prod.yml`
- **Problem:** `baseline-on-migrate: false` in prod — fails on existing Aiven DB
- **Fix:** `baseline-on-migrate: true` — safe for existing databases

#### 7. Tomcat Thread Limits Added
- **File:** `application.yml`, `application-prod.yml`
- **Problem:** Default 200 Tomcat threads on 1GB = OOM under load
- **Fix:** `max: 20, min-spare: 5` appropriate for 1GB

### 🟢 Version Updates

| Dependency | Before | After |
|---|---|---|
| mysql-connector-j | 9.1.0 | **9.2.0** |
| google-api-client | 2.7.0 | **2.7.2** |
| lombok | 1.18.34 | **1.18.38** |
| guava | 33.3.1 | **33.4.8** |
| datafaker | 2.4.0 | **2.4.3** |
| testcontainers | 1.19.8 | **1.20.4** |
| springdoc | 2.6.0 | **2.8.8** |
| maven-compiler | 3.13.0 | **3.14.0** |

### 📁 File Changes

| File | Action |
|---|---|
| `application.yml` | Dialect fix, open-in-view, fraud env vars, Hikari fix |
| `application-prod.yml` | Redis optional, Hikari 5/2, Flyway fix, Tomcat threads |
| `pom.xml` | All versions updated |
| `.env.template` | Added — complete template with all variables |
| `deploy.sh` | Added — one-command production deploy |
| `.gitignore` | Updated — .env protected |
| `Dockerfile` | Removed — not used in production |

### 🚀 Deployment

```bash
# 1. Build
mvn clean package -DskipTests

# 2. Upload JAR
scp -i key.pem target/EarnX-3-0.0.1-SNAPSHOT.jar ubuntu@SERVER:/home/ubuntu/earnx/app.jar

# 3. Deploy
bash deploy.sh
```
