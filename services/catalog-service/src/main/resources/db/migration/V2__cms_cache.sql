ALTER TABLE catalog_products
    ADD COLUMN display_title VARCHAR(256),
    ADD COLUMN slug VARCHAR(256),
    ADD COLUMN short_description TEXT,
    ADD COLUMN body_text TEXT,
    ADD COLUMN category_slug VARCHAR(128),
    ADD COLUMN category_name VARCHAR(128),
    ADD COLUMN tags_json TEXT,
    ADD COLUMN image_urls_json TEXT,
    ADD COLUMN primary_image_url VARCHAR(1024),
    ADD COLUMN cms_published BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN cms_published_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN cms_updated_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN strapi_document_id VARCHAR(64);

CREATE UNIQUE INDEX uq_catalog_products_slug ON catalog_products (slug) WHERE slug IS NOT NULL;
CREATE INDEX idx_catalog_products_cms_published ON catalog_products (cms_published) WHERE deleted = FALSE;
