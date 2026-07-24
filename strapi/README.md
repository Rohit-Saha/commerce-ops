# Strapi (merchandising CMS)

Self-hosted Strapi 5 for product merchandising. Inventory remains source of
truth for SKU, price, and stock; Strapi owns display title, slug, copy,
category, tags, gallery, and draft/publish.

## Run

```bash
# from repo root (requires postgres + strapi_db)
docker compose up -d postgres strapi elasticsearch
```

Admin: http://localhost:1337/admin (create admin user on first boot).

Demo categories and published products for `SKU-TEE-001`, `SKU-MUG-001`, and
`SKU-HAT-001` are seeded on empty bootstrap.

## Join key

`Product.sku` must match an inventory SKU. Unpublished / draft entries are
never indexed by `catalog-service`.
