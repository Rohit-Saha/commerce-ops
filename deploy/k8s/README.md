# Kubernetes / EKS manifests

Minimal Deployments + ClusterIP Services for **DNS-based service discovery**.
Service `metadata.name` values match the hosts in each app's `application-k8s.yml`
and `config-repo/*-k8s.yml` (e.g. `order-service` → `http://order-service:8081`).

## Prerequisites

- Container images tagged `commerceops/<service>:0.1.0-SNAPSHOT` available to the cluster
  (include `commerceops/config-server:0.1.0-SNAPSHOT`)
- In-cluster data plane Services named `postgres`, `redis`, `kafka` (and optionally
  `otel-collector`, `elasticsearch`, `strapi`) — not bundled here
- Secret `commerce-ops-secrets` in namespace `commerce-ops` (from `secret.example.yaml`)

## Apply

```bash
kubectl apply -f deploy/k8s/namespace.yaml
cp deploy/k8s/secret.example.yaml deploy/k8s/secret.yaml   # edit values
kubectl apply -f deploy/k8s/secret.yaml
kubectl apply -k deploy/k8s/
```

Pods start with `SPRING_PROFILES_ACTIVE=k8s,oidc`,
`COMMERCE_SECURITY_MODE=oidc`, and
`OAUTH2_ISSUER_URI=http://keycloak:8080/realms/commerce-ops`.
Gateway Auth BFF secrets (`KEYCLOAK_*_BFF_SECRET`, frontend redirect URLs) come from
`commerce-ops-secrets`. `config-server` serves baked `config-repo`. **Keycloak** is
included (`apps/keycloak.yaml` + realm ConfigMap). Only expose `api-gateway` (and
optionally Keycloak admin) via Ingress — domain Services stay ClusterIP. Browsers
must not reach Keycloak; login goes through `/api/auth/login` on the gateway.

## Verify discovery

```bash
kubectl -n commerce-ops exec deploy/api-gateway -- \
  wget -qO- http://order-service:8081/actuator/health

kubectl -n commerce-ops exec deploy/api-gateway -- \
  wget -qO- http://config-server:8888/api-gateway/k8s
```

Scale without changing gateway config:

```bash
kubectl -n commerce-ops scale deploy/order-service --replicas=2
```

## Images

Build fat jars (`mvn -DskipTests package`), then Dockerize each module and push to
your registry. Point `image:` in `apps/*.yaml` at your registry as needed.
