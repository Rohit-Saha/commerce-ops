import type { Core } from '@strapi/strapi';

async function notifyCatalog(event: string, sku: string | undefined, documentId?: string) {
  const url = process.env.CATALOG_WEBHOOK_URL;
  const secret = process.env.CATALOG_WEBHOOK_SECRET || 'commerce-ops-strapi-webhook';
  if (!url || !sku) {
    return;
  }
  try {
    await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Webhook-Secret': secret,
      },
      body: JSON.stringify({ event, sku, documentId }),
    });
  } catch (err) {
    strapi.log.warn(`Catalog webhook failed for sku=${sku}: ${err}`);
  }
}

async function seedDemoContent(strapi: Core.Strapi) {
  const existing = await strapi.documents('api::product.product').findMany({ limit: 1 });
  if (existing.length > 0) {
    return;
  }

  const apparel = await strapi.documents('api::category.category').create({
    data: { name: 'Apparel', slug: 'apparel', sortOrder: 1 },
  });
  const drinkware = await strapi.documents('api::category.category').create({
    data: { name: 'Drinkware', slug: 'drinkware', sortOrder: 2 },
  });
  const accessories = await strapi.documents('api::category.category').create({
    data: { name: 'Accessories', slug: 'accessories', sortOrder: 3 },
  });

  const seeds = [
    {
      sku: 'SKU-TEE-001',
      displayTitle: 'Classic Tee',
      slug: 'classic-tee',
      shortDescription: 'Soft midweight cotton tee for everyday wear.',
      body: 'Cut for a relaxed fit with reinforced seams. Pair with denim or layer under a jacket.',
      tags: ['cotton', 'basics', 'everyday'],
      category: apparel.documentId,
    },
    {
      sku: 'SKU-MUG-001',
      displayTitle: 'Ceramic Mug',
      slug: 'ceramic-mug',
      shortDescription: 'Stoneware mug that holds heat and morning coffee.',
      body: 'Dishwasher-safe glaze with a generous handle. Ideal for desk or kitchen.',
      tags: ['ceramic', 'kitchen'],
      category: drinkware.documentId,
    },
    {
      sku: 'SKU-HAT-001',
      displayTitle: 'Baseball Cap',
      slug: 'baseball-cap',
      shortDescription: 'Structured cap with an adjustable strap.',
      body: 'Breathable crown and a curved brim for sun cover on walkabouts.',
      tags: ['hats', 'outdoor'],
      category: accessories.documentId,
    },
  ];

  for (const seed of seeds) {
    await strapi.documents('api::product.product').create({
      data: {
        sku: seed.sku,
        displayTitle: seed.displayTitle,
        slug: seed.slug,
        shortDescription: seed.shortDescription,
        body: seed.body,
        tags: seed.tags,
        category: seed.category,
      },
      status: 'published',
    });
  }

  strapi.log.info('Seeded demo categories + published products for inventory SKUs');
}

export default {
  register(/* { strapi }: { strapi: Core.Strapi } */) {},

  async bootstrap({ strapi }: { strapi: Core.Strapi }) {
    const publicRole = await strapi.db.query('plugin::users-permissions.role').findOne({
      where: { type: 'public' },
    });
    if (publicRole) {
      const actions = [
        'api::category.category.find',
        'api::category.category.findOne',
        'api::product.product.find',
        'api::product.product.findOne',
      ];
      for (const action of actions) {
        const existing = await strapi.db.query('plugin::users-permissions.permission').findOne({
          where: { action, role: publicRole.id },
        });
        if (!existing) {
          await strapi.db.query('plugin::users-permissions.permission').create({
            data: { action, role: publicRole.id },
          });
        }
      }
    }

    try {
      await seedDemoContent(strapi);
    } catch (err) {
      strapi.log.warn(`Demo seed skipped: ${err}`);
    }

    strapi.db.lifecycles.subscribe({
      models: ['api::product.product'],
      async afterCreate(event) {
        const sku = event.result?.sku as string | undefined;
        await notifyCatalog('entry.create', sku, event.result?.documentId);
      },
      async afterUpdate(event) {
        const sku = event.result?.sku as string | undefined;
        await notifyCatalog('entry.update', sku, event.result?.documentId);
      },
      async afterDelete(event) {
        const sku = event.result?.sku as string | undefined;
        await notifyCatalog('entry.delete', sku, event.result?.documentId);
      },
    });
  },
};
