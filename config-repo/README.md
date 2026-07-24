# config-repo

Native backend for `config-server`. Non-secret shared and per-service YAML
files. Naming follows Spring Cloud Config: `{application}.yml` and
`{application}-{profile}.yml`.

Secrets (passwords, API keys, payment/shipping credentials) must **not** be
committed here — use env or Kubernetes Secrets.

Clients import with `optional:configserver:` so local classpath defaults still
work when Config Server is down. After editing a file here, **restart** the
affected client (v1 has no Spring Cloud Bus refresh).
