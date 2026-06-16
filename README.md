# E-commerce Order Service

Backend de ciclo de vida de pedidos implementado com **Java 21 + Spring Boot 3.x**, seguindo Clean Architecture (Hexagonal), Spec-Driven Development e TDD.

## Pré-requisitos

| Ferramenta | Versão mínima |
|---|---|
| Docker Desktop | 4.x (com `Expose daemon on tcp://localhost:2375` habilitado para testes locais) |
| Docker Compose | v2 (embutido no Docker Desktop) |
| Java JDK | 21 (só necessário para rodar testes fora do Docker) |
| Maven | 3.9+ (ou use o wrapper `./mvnw`) |
| Python 3 + PyJWT | Para gerar tokens localmente (`pip install PyJWT cryptography`) |

## Subindo o ambiente local

```bash
docker-compose up -d
```

Aguarda automaticamente os healthchecks. Serviços disponíveis:

| Serviço | URL |
|---|---|
| order-service (API) | http://localhost:8081 |
| Swagger UI | http://localhost:8081/swagger-ui.html |
| WireMock (mocks externos) | http://localhost:8080 |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (admin/admin) |
| Jaeger UI | http://localhost:16686 |

## Gerando um token JWT

O projeto usa RS256 com chave de desenvolvimento versionada. Para gerar um token:

```bash
pip install PyJWT cryptography   # apenas na primeira vez

TOKEN=$(python3 scripts/generate-token.py --scope "orders:read orders:write payments:read payments:write")
echo $TOKEN
```

Ou com escopos específicos:

```bash
# Apenas leitura de pedidos
TOKEN=$(python3 scripts/generate-token.py --scope "orders:read")

# Token do gateway (para o webhook)
TOKEN_GW=$(python3 scripts/generate-token.py --scope "payments:write" --sub gateway)
```

## Fluxo de demonstração ponta a ponta

Os IDs fixos abaixo são os mapeados no WireMock (ver `wiremock/mappings/`).

```bash
BASE="http://localhost:8081/api/v1"
TOKEN=$(python3 scripts/generate-token.py)
AUTH="Authorization: Bearer $TOKEN"

# ── 1. Criar pedido ──────────────────────────────────────────────────────────
ORDER=$(curl -s -X POST "$BASE/orders" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"customerId":"11111111-1111-1111-1111-111111111111"}')
echo $ORDER | python3 -m json.tool
ORDER_ID=$(echo $ORDER | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

# ── 2. Adicionar itens ───────────────────────────────────────────────────────
curl -s -X POST "$BASE/orders/$ORDER_ID/items" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"productId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","quantity":2}' | python3 -m json.tool

curl -s -X POST "$BASE/orders/$ORDER_ID/items" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"productId":"cccccccc-cccc-cccc-cccc-cccccccccccc","quantity":1}' | python3 -m json.tool

# ── 3. Confirmar (total = 2×199.90 + 1×49.90 = 449.70 BRL) ──────────────────
curl -s -X POST "$BASE/orders/$ORDER_ID/confirm" \
  -H "$AUTH" | python3 -m json.tool

# ── 4. Iniciar pagamento (tok-approved → PAID) ───────────────────────────────
PAYMENT=$(curl -s -X POST "$BASE/payments" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"orderId\":\"$ORDER_ID\",\"method\":{\"type\":\"CARD\",\"cardToken\":\"tok-approved\"}}")
echo $PAYMENT | python3 -m json.tool
PAYMENT_ID=$(echo $PAYMENT | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

# ── 5. Consultar estado final ─────────────────────────────────────────────────
curl -s "$BASE/orders/$ORDER_ID" -H "$AUTH" | python3 -m json.tool
curl -s "$BASE/payments/$PAYMENT_ID" -H "$AUTH" | python3 -m json.tool
```

### Testar rejeição e auto-cancelamento (3ª rejeição)

```bash
# Cria novo pedido e confirma
ORDER2=$(curl -s -X POST "$BASE/orders" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"customerId":"11111111-1111-1111-1111-111111111111"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
curl -s -X POST "$BASE/orders/$ORDER2/items" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"productId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","quantity":1}' > /dev/null
curl -s -X POST "$BASE/orders/$ORDER2/confirm" -H "$AUTH" > /dev/null

# 3 rejeições → auto-cancelamento com PAYMENT_ATTEMPTS_EXCEEDED
for i in 1 2 3; do
  echo "--- Tentativa $i ---"
  curl -s -X POST "$BASE/payments" -H "$AUTH" -H "Content-Type: application/json" \
    -d "{\"orderId\":\"$ORDER2\",\"method\":{\"type\":\"CARD\",\"cardToken\":\"tok-rejected\"}}" | python3 -m json.tool
done

curl -s "$BASE/orders/$ORDER2" -H "$AUTH" | python3 -m json.tool
# status: CANCELLED, cancellationReason: PAYMENT_ATTEMPTS_EXCEEDED
```

### Testar gateway instável (circuit breaker)

```bash
# Envia requisição com tok-unstable — espera 502 após retries/timeout
curl -s -X POST "$BASE/payments" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"orderId\":\"$ORDER_ID\",\"method\":{\"type\":\"CARD\",\"cardToken\":\"tok-unstable\"}}" \
  | python3 -m json.tool
# Retorna 502 problem+json em < 10s. O pedido permanece PAYMENT_PENDING.
```

### Simular webhook

```bash
TOKEN_GW=$(python3 scripts/generate-token.py --scope "payments:write" --sub gateway)
curl -s -X POST "$BASE/payments/$PAYMENT_ID/callback" \
  -H "Authorization: Bearer $TOKEN_GW" -H "Content-Type: application/json" \
  -d "{\"eventId\":\"evt-$(python3 -c 'import uuid; print(uuid.uuid4())')\",\"paymentId\":\"$PAYMENT_ID\",\"status\":\"APPROVED\",\"transactionId\":\"tx-manual\",\"occurredAt\":\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"}"
```

## Executando os testes

```bash
cd order-service

# Testes unitários (sem Docker)
mvn test -Dtest="MoneyTest,OrderTest,PaymentTest,QuantityTest,ValueObjectsTest,ArchitectureTest,OrderMapperTest,PaymentMapperTest,OrderControllerTest,PaymentControllerTest,OpenApiDocTest"

# Pitest — MSI do domínio (sem Docker)
mvn org.pitest:pitest-maven:mutationCoverage -DskipPitest=false

# Testes de integração (requer Docker Desktop com TCP:2375 habilitado)
mvn test -Dtest="FlywayMigrationTest,OrderRepositoryIntegrationTest,PaymentRepositoryIntegrationTest,ExternalClientsIntegrationTest,OrderUseCasesIntegrationTest,PaymentUseCasesIntegrationTest,WebhookIntegrationTest,ResilienceIntegrationTest,SecurityIntegrationTest,CorrelationIdFilterTest,BusinessMetricsTest"

# Suite completa (build + todos os testes + JaCoCo + Pitest)
mvn verify
```

> **Windows + Docker Desktop 4.40+:** habilite *Settings → General → Expose daemon on tcp://localhost:2375 without TLS* antes de rodar os testes de integração.

## Observabilidade

- **Logs JSON**: saída estruturada com `correlationId` propagado via `X-Correlation-Id`
- **Métricas Prometheus**: `http://localhost:9090` — `orders_confirmed_total`, `payments_rejected_total`, `orders_auto_cancelled_total`, métricas de circuit breaker
- **Dashboard Grafana**: `http://localhost:3000` → dashboard *Order Service — Business & Resilience Metrics*
- **Tracing (Jaeger)**: `http://localhost:16686` — serviço `order-service`

## Estrutura do repositório

```
.
├── order-service/          # Spring Boot app (único serviço implementado)
│   ├── src/main/java/com/ecommerce/orders/
│   │   ├── domain/         # Java puro — agregados, VOs, eventos, exceções
│   │   ├── application/    # Casos de uso + ports (in/out)
│   │   └── infrastructure/ # Web, persistence, clients, security, observability
│   └── Dockerfile          # Multi-stage: maven → JRE 21 Alpine
├── wiremock/
│   ├── mappings/           # Contratos dos serviços externos (customer, catalog, payment, notification, jwks)
│   └── __files/            # Corpos de resposta + jwks.json
├── observability/
│   ├── prometheus.yml
│   └── grafana/provisioning/
├── scripts/
│   └── generate-token.py   # Gerador de JWT RS256 para desenvolvimento
├── docs/
│   └── architecture.md     # ADRs e decomposição do domínio
├── docker-compose.yml
└── .github/workflows/ci.yml
```

## Checklist de entrega

- [x] Repositório com código completo e estrutura de P1
- [x] README com execução local via `docker-compose up`
- [x] 10 endpoints implementados e documentados no Swagger (`/swagger-ui.html`)
- [x] Suíte de testes verde (unit + integração Testcontainers + WireMock com `wiremock/mappings/` reutilizados)
- [x] JaCoCo ≥ 80% e Pitest MSI ≥ 75% no domínio (alcançado: MSI 100%)
- [x] CI configurado (.github/workflows/ci.yml — build, testes, Trivy)
- [x] `docs/architecture.md` presente
- [x] Logs JSON com correlationId + métricas Prometheus + tracing Jaeger funcionais
- [x] JWT com escopos + controles OWASP (validação, rate limit, headers)
- [x] Nenhum mock/stub em código de produção
- [x] Erros RFC 7807 em toda a API
- [x] Todos os cenários Gherkin de S5 cobertos por testes automatizados
