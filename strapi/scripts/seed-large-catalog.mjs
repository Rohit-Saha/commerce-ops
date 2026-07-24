/**
 * One-shot seed: expands categories, creates ~50 inventory SKUs (via REST),
 * and publishes matching Strapi products with gallery images.
 *
 * Run inside the Strapi container:
 *   node /opt/app/scripts/seed-large-catalog.mjs
 *
 * Expects inventory API at INVENTORY_URL (default host.docker.internal:8082)
 * and GATEWAY with admin key optional — prefers direct inventory :8082.
 */
import { createRequire } from "module";
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import { createWriteStream } from "fs";
import { pipeline } from "stream/promises";
import { Readable } from "stream";

const require = createRequire(import.meta.url);
const __dirname = path.dirname(fileURLToPath(import.meta.url));

const INVENTORY_URL = process.env.INVENTORY_URL || "http://host.docker.internal:8082";
const ADMIN_KEY = process.env.INVENTORY_API_KEY || "dev-admin-key";
const TMP = path.join(__dirname, "../.tmp-seed-images");

const CATEGORIES = [
  { name: "Apparel", slug: "apparel", sortOrder: 1 },
  { name: "Drinkware", slug: "drinkware", sortOrder: 2 },
  { name: "Accessories", slug: "accessories", sortOrder: 3 },
  { name: "Home", slug: "home", sortOrder: 4 },
  { name: "Outdoor", slug: "outdoor", sortOrder: 5 },
  { name: "Care", slug: "care", sortOrder: 6 },
];

/** @type {Array<{sku:string,name:string,price:number,qty:number,category:string,title:string,slug:string,short:string,body:string,tags:string[]}>} */
const PRODUCTS = [
  // Apparel (12)
  { sku: "SKU-TEE-002", name: "Heavyweight Tee", price: 1360, qty: 80, category: "apparel", title: "Heavyweight Tee", slug: "heavyweight-tee", short: "Thick cotton tee with a soft hand feel.", body: "Cut for everyday layering. Reinforced collar and side seams.", tags: ["cotton", "basics"] },
  { sku: "SKU-TEE-003", name: "Pocket Tee", price: 1280, qty: 60, category: "apparel", title: "Pocket Tee", slug: "pocket-tee", short: "Classic pocket tee in midweight jersey.", body: "Chest pocket detail with a relaxed straight hem.", tags: ["cotton", "basics"] },
  { sku: "SKU-TEE-004", name: "Long Sleeve Tee", price: 1520, qty: 45, category: "apparel", title: "Long Sleeve Tee", slug: "long-sleeve-tee", short: "Year-round long sleeve in breathable cotton.", body: "Slightly tapered sleeves with ribbed cuffs.", tags: ["cotton", "layering"] },
  { sku: "SKU-HDY-001", name: "Fleece Hoodie", price: 2720, qty: 40, category: "apparel", title: "Fleece Hoodie", slug: "fleece-hoodie", short: "Brushed fleece hoodie for cool mornings.", body: "Kangaroo pocket and drawcord hood. Soft brushed interior.", tags: ["fleece", "outerwear"] },
  { sku: "SKU-HDY-002", name: "Zip Hoodie", price: 2880, qty: 35, category: "apparel", title: "Zip Hoodie", slug: "zip-hoodie", short: "Full-zip hoodie with clean metal hardware.", body: "Midweight fleece with ribbed hem and cuffs.", tags: ["fleece", "outerwear"] },
  { sku: "SKU-SWT-001", name: "Crew Sweatshirt", price: 2320, qty: 50, category: "apparel", title: "Crew Sweatshirt", slug: "crew-sweatshirt", short: "Loopback crew for daily wear.", body: "Set-in sleeves and a tidy rib collar.", tags: ["fleece", "basics"] },
  { sku: "SKU-PNT-001", name: "Work Pant", price: 3120, qty: 30, category: "apparel", title: "Work Pant", slug: "work-pant", short: "Straight work pant with reinforced knees.", body: "Durable twill with room in the thigh and a clean taper.", tags: ["bottoms", "workwear"] },
  { sku: "SKU-PNT-002", name: "Easy Trouser", price: 2960, qty: 28, category: "apparel", title: "Easy Trouser", slug: "easy-trouser", short: "Relaxed trouser with a soft drawcord waist.", body: "Lightweight cloth that drapes without looking sloppy.", tags: ["bottoms", "everyday"] },
  { sku: "SKU-SHT-001", name: "Oxford Shirt", price: 2560, qty: 42, category: "apparel", title: "Oxford Shirt", slug: "oxford-shirt", short: "Button-down oxford for desk or weekend.", body: "Breathable weave with a soft collar roll.", tags: ["shirts", "basics"] },
  { sku: "SKU-SHT-002", name: "Camp Collar Shirt", price: 2480, qty: 36, category: "apparel", title: "Camp Collar Shirt", slug: "camp-collar-shirt", short: "Open collar shirt in washed cotton.", body: "Short sleeve with a clean straight hem.", tags: ["shirts", "summer"] },
  { sku: "SKU-JKT-001", name: "Field Jacket", price: 5120, qty: 22, category: "apparel", title: "Field Jacket", slug: "field-jacket", short: "Light field jacket with four pockets.", body: "Water-resistant shell and a quilted lining.", tags: ["outerwear", "layering"] },
  { sku: "SKU-JKT-002", name: "Quilted Liner", price: 3920, qty: 26, category: "apparel", title: "Quilted Liner", slug: "quilted-liner", short: "Packable quilted liner jacket.", body: "Warm without bulk. Packs into its own pocket.", tags: ["outerwear", "travel"] },

  // Drinkware (8)
  { sku: "SKU-MUG-003", name: "Stoneware Mug", price: 720, qty: 90, category: "drinkware", title: "Stoneware Mug", slug: "stoneware-mug", short: "Speckled stoneware mug for morning coffee.", body: "Microwave safe with a comfortable handle.", tags: ["ceramic", "kitchen"] },
  { sku: "SKU-MUG-004", name: "Enamel Mug", price: 640, qty: 70, category: "drinkware", title: "Enamel Mug", slug: "enamel-mug", short: "Camp enamel mug with a rolled rim.", body: "Lightweight and tough for desk or trail.", tags: ["enamel", "outdoor"] },
  { sku: "SKU-TMB-001", name: "Travel Tumbler", price: 1280, qty: 55, category: "drinkware", title: "Travel Tumbler", slug: "travel-tumbler", short: "Insulated tumbler that keeps drinks hot.", body: "Leak-resistant lid and a slim grip.", tags: ["insulated", "travel"] },
  { sku: "SKU-TMB-002", name: "Cold Flask", price: 1440, qty: 48, category: "drinkware", title: "Cold Flask", slug: "cold-flask", short: "Double-wall flask for cold drinks.", body: "Sweat-free exterior and wide mouth.", tags: ["insulated", "travel"] },
  { sku: "SKU-BTL-001", name: "Glass Bottle", price: 1120, qty: 60, category: "drinkware", title: "Glass Bottle", slug: "glass-bottle", short: "Borosilicate bottle with a bamboo lid.", body: "Clean tasting water bottle for everyday carry.", tags: ["glass", "kitchen"] },
  { sku: "SKU-BTL-002", name: "Steel Bottle", price: 1360, qty: 65, category: "drinkware", title: "Steel Bottle", slug: "steel-bottle", short: "Single-wall steel bottle, no coating taste.", body: "Durable everyday bottle with a screw cap.", tags: ["steel", "everyday"] },
  { sku: "SKU-CUP-001", name: "Espresso Cup", price: 560, qty: 100, category: "drinkware", title: "Espresso Cup", slug: "espresso-cup", short: "Small ceramic cup for espresso.", body: "Thick walls hold heat for a short pull.", tags: ["ceramic", "kitchen"] },
  { sku: "SKU-CUP-002", name: "Tea Cup Set", price: 1680, qty: 40, category: "drinkware", title: "Tea Cup Set", slug: "tea-cup-set", short: "Pair of tea cups with saucers.", body: "Matte glaze and a quiet stackable profile.", tags: ["ceramic", "gift"] },

  // Accessories (10)
  { sku: "SKU-HAT-002", name: "Wool Beanie", price: 960, qty: 55, category: "accessories", title: "Wool Beanie", slug: "wool-beanie", short: "Ribbed wool beanie for cold days.", body: "Soft merino blend that holds its shape.", tags: ["hats", "winter"] },
  { sku: "SKU-HAT-003", name: "Sun Hat", price: 1440, qty: 40, category: "accessories", title: "Sun Hat", slug: "sun-hat", short: "Wide brim sun hat in cotton canvas.", body: "Breathable crown with an adjustable cord.", tags: ["hats", "outdoor"] },
  { sku: "SKU-BAG-001", name: "Canvas Tote", price: 1120, qty: 70, category: "accessories", title: "Canvas Tote", slug: "canvas-tote", short: "Sturdy tote for market or laptop days.", body: "Heavy canvas with reinforced handles.", tags: ["bags", "everyday"] },
  { sku: "SKU-BAG-002", name: "Day Pack", price: 3520, qty: 32, category: "accessories", title: "Day Pack", slug: "day-pack", short: "Compact day pack with a laptop sleeve.", body: "Water-resistant shell and clean exterior pockets.", tags: ["bags", "travel"] },
  { sku: "SKU-BLT-001", name: "Leather Belt", price: 1920, qty: 45, category: "accessories", title: "Leather Belt", slug: "leather-belt", short: "Vegetable-tanned belt with a simple buckle.", body: "Ages with wear. Cut to length if needed.", tags: ["leather", "basics"] },
  { sku: "SKU-WLT-001", name: "Card Wallet", price: 1680, qty: 50, category: "accessories", title: "Card Wallet", slug: "card-wallet", short: "Slim wallet for cards and folded notes.", body: "Minimal stitching and a secure cash pocket.", tags: ["leather", "everyday"] },
  { sku: "SKU-SCF-001", name: "Merino Scarf", price: 2160, qty: 38, category: "accessories", title: "Merino Scarf", slug: "merino-scarf", short: "Lightweight merino scarf.", body: "Warm without bulk. Soft against the neck.", tags: ["winter", "merino"] },
  { sku: "SKU-GLV-001", name: "Work Gloves", price: 1040, qty: 60, category: "accessories", title: "Work Gloves", slug: "work-gloves", short: "Canvas work gloves with grip palms.", body: "Tough enough for yard work, soft enough for errands.", tags: ["workwear", "outdoor"] },
  { sku: "SKU-KEY-001", name: "Key Organizer", price: 880, qty: 80, category: "accessories", title: "Key Organizer", slug: "key-organizer", short: "Quiet key holder that stops jingle.", body: "Compact leather wrap with a secure snap.", tags: ["leather", "everyday"] },
  { sku: "SKU-SOC-001", name: "Merino Socks", price: 720, qty: 120, category: "accessories", title: "Merino Socks", slug: "merino-socks", short: "Cushioned merino crew socks.", body: "Temperature regulating with a reinforced heel.", tags: ["merino", "basics"] },

  // Home (8)
  { sku: "SKU-TWL-001", name: "Bath Towel", price: 1440, qty: 50, category: "home", title: "Bath Towel", slug: "bath-towel", short: "Heavy cotton bath towel.", body: "Absorbent loop pile that softens with washes.", tags: ["cotton", "bath"] },
  { sku: "SKU-TWL-002", name: "Hand Towel Set", price: 1120, qty: 55, category: "home", title: "Hand Towel Set", slug: "hand-towel-set", short: "Pair of hand towels in soft cotton.", body: "Quick-dry weave for kitchen or bath.", tags: ["cotton", "bath"] },
  { sku: "SKU-THW-001", name: "Wool Throw", price: 3840, qty: 24, category: "home", title: "Wool Throw", slug: "wool-throw", short: "Herringbone wool throw for the sofa.", body: "Warm drape with fringed ends.", tags: ["wool", "living"] },
  { sku: "SKU-CND-001", name: "Soy Candle", price: 960, qty: 70, category: "home", title: "Soy Candle", slug: "soy-candle", short: "Clean-burning soy candle in a tin.", body: "Soft cedar scent. Roughly 40 hours of burn.", tags: ["home", "gift"] },
  { sku: "SKU-VSE-001", name: "Ceramic Vase", price: 1760, qty: 30, category: "home", title: "Ceramic Vase", slug: "ceramic-vase", short: "Matte ceramic vase for a few stems.", body: "Heavy base so it stays put on a shelf.", tags: ["ceramic", "living"] },
  { sku: "SKU-FRM-001", name: "Oak Frame", price: 1280, qty: 40, category: "home", title: "Oak Frame", slug: "oak-frame", short: "Solid oak photo frame.", body: "Fits a standard print with a clear acrylic front.", tags: ["wood", "living"] },
  { sku: "SKU-LMP-001", name: "Desk Lamp", price: 3120, qty: 28, category: "home", title: "Desk Lamp", slug: "desk-lamp", short: "Adjustable desk lamp with a warm LED.", body: "Steel arm and a weighted base.", tags: ["lighting", "desk"] },
  { sku: "SKU-MAT-001", name: "Door Mat", price: 1520, qty: 35, category: "home", title: "Door Mat", slug: "door-mat", short: "Coir door mat with a non-slip back.", body: "Scrubs mud without shedding everywhere.", tags: ["home", "entry"] },

  // Outdoor (7)
  { sku: "SKU-BLN-001", name: "Wool Blanket", price: 4400, qty: 20, category: "outdoor", title: "Wool Blanket", slug: "wool-blanket", short: "Camp wool blanket that shrugs off sparks.", body: "Dense weave for car, cabin, or picnic.", tags: ["wool", "camp"] },
  { sku: "SKU-CHR-001", name: "Camp Chair", price: 2560, qty: 25, category: "outdoor", title: "Camp Chair", slug: "camp-chair", short: "Packable camp chair with a carry bag.", body: "Stable frame and a cup holder that actually works.", tags: ["camp", "furniture"] },
  { sku: "SKU-CLP-001", name: "Utility Clip Set", price: 640, qty: 90, category: "outdoor", title: "Utility Clip Set", slug: "utility-clip-set", short: "Aluminum carabiners in a three-pack.", body: "Light, strong clips for keys, bags, and gear.", tags: ["gear", "travel"] },
  { sku: "SKU-TNT-001", name: "Shade Cloth", price: 1920, qty: 22, category: "outdoor", title: "Shade Cloth", slug: "shade-cloth", short: "Lightweight shade cloth for patio days.", body: "UV blocking weave with corner grommets.", tags: ["outdoor", "garden"] },
  { sku: "SKU-PLN-001", name: "Planter Pot", price: 1200, qty: 40, category: "outdoor", title: "Planter Pot", slug: "planter-pot", short: "Terracotta planter with a drainage hole.", body: "Classic clay pot for herbs and small shrubs.", tags: ["garden", "ceramic"] },
  { sku: "SKU-TCH-001", name: "Pocket Torch", price: 880, qty: 75, category: "outdoor", title: "Pocket Torch", slug: "pocket-torch", short: "Compact LED torch with a clip.", body: "USB-C rechargeable with a focused beam.", tags: ["gear", "everyday"] },
  { sku: "SKU-TRM-001", name: "Trail Bottle Cap", price: 480, qty: 100, category: "outdoor", title: "Trail Bottle Cap", slug: "trail-bottle-cap", short: "Filter cap that fits standard bottles.", body: "Adds a simple charcoal filter for day hikes.", tags: ["gear", "travel"] },

  // Care (5)
  { sku: "SKU-SOAP-001", name: "Bar Soap", price: 480, qty: 110, category: "care", title: "Bar Soap", slug: "bar-soap", short: "Olive oil bar soap with a clean scent.", body: "Gentle on skin. Comes in a recycled wrap.", tags: ["bath", "gift"] },
  { sku: "SKU-LBN-001", name: "Hand Lotion", price: 720, qty: 85, category: "care", title: "Hand Lotion", slug: "hand-lotion", short: "Light lotion that absorbs quickly.", body: "No sticky residue. Unscented option for desks.", tags: ["care", "everyday"] },
  { sku: "SKU-DTR-001", name: "Wool Detergent", price: 800, qty: 60, category: "care", title: "Wool Detergent", slug: "wool-detergent", short: "Gentle detergent for wool and delicates.", body: "Plant-based formula for cool washes.", tags: ["care", "laundry"] },
  { sku: "SKU-BRS-001", name: "Cedar Brush", price: 1040, qty: 45, category: "care", title: "Cedar Brush", slug: "cedar-brush", short: "Clothes brush with cedar wood handle.", body: "Lifts lint and refreshes wool between wears.", tags: ["care", "wardrobe"] },
  { sku: "SKU-TIN-001", name: "Lip Balm Tin", price: 320, qty: 150, category: "care", title: "Lip Balm Tin", slug: "lip-balm-tin", short: "Beeswax lip balm in a refillable tin.", body: "Simple formula. No flavoring overload.", tags: ["care", "everyday"] },
];

async function ensureInventory(product) {
  const listRes = await fetch(`${INVENTORY_URL}/api/inventory/${encodeURIComponent(product.sku)}`);
  if (listRes.ok) {
    return;
  }
  const res = await fetch(`${INVENTORY_URL}/api/inventory`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-API-Key": ADMIN_KEY,
    },
    body: JSON.stringify({
      sku: product.sku,
      name: product.name,
      unitPrice: product.price,
      availableQty: product.qty,
    }),
  });
  if (!res.ok && res.status !== 409) {
    const text = await res.text();
    // inventory may not use API key — retry without
    if (res.status === 401 || res.status === 403) {
      const res2 = await fetch(`${INVENTORY_URL}/api/inventory`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          sku: product.sku,
          name: product.name,
          unitPrice: product.price,
          availableQty: product.qty,
        }),
      });
      if (!res2.ok && res2.status !== 409) {
        throw new Error(`inventory ${product.sku}: ${res2.status} ${await res2.text()}`);
      }
      return;
    }
    throw new Error(`inventory ${product.sku}: ${res.status} ${text}`);
  }
}

async function downloadImage(sku, category) {
  fs.mkdirSync(TMP, { recursive: true });
  const file = path.join(TMP, `${sku}.jpg`);
  if (fs.existsSync(file) && fs.statSync(file).size > 1000) {
    return file;
  }
  const seed = encodeURIComponent(sku);
  const url = `https://picsum.photos/seed/${seed}/900/900`;
  const res = await fetch(url);
  if (!res.ok || !res.body) {
    throw new Error(`image download failed for ${sku}: ${res.status}`);
  }
  await pipeline(Readable.fromWeb(res.body), createWriteStream(file));
  return file;
}

async function main() {
  const { createStrapi } = require("@strapi/strapi");
  console.log(`Loading Strapi… (${PRODUCTS.length} products)`);
  const app = await createStrapi({ distDir: path.join(__dirname, "../dist") }).load();

  // Ensure categories
  /** @type {Record<string,string>} */
  const categoryIds = {};
  for (const cat of CATEGORIES) {
    const existing = await app.documents("api::category.category").findMany({
      filters: { slug: cat.slug },
      limit: 1,
    });
    if (existing.length) {
      categoryIds[cat.slug] = existing[0].documentId;
    } else {
      const created = await app.documents("api::category.category").create({
        data: cat,
      });
      categoryIds[cat.slug] = created.documentId;
      console.log(`category created: ${cat.slug}`);
    }
  }

  let ok = 0;
  for (const product of PRODUCTS) {
    try {
      await ensureInventory(product);

      const existing = await app.documents("api::product.product").findMany({
        filters: { sku: product.sku },
        limit: 1,
        status: "draft",
      });
      const publishedExisting = await app.documents("api::product.product").findMany({
        filters: { sku: product.sku },
        limit: 1,
        status: "published",
      });
      if (publishedExisting.length) {
        console.log(`skip published ${product.sku}`);
        ok++;
        continue;
      }

      const imagePath = await downloadImage(product.sku, product.category);
      const uploadService = app.plugin("upload").service("upload");
      const stats = fs.statSync(imagePath);
      const uploaded = await uploadService.upload({
        data: {
          fileInfo: {
            name: `${product.sku}.jpg`,
            alternativeText: product.title,
          },
        },
        files: {
          filepath: imagePath,
          originalFilename: `${product.sku}.jpg`,
          mimetype: "image/jpeg",
          size: stats.size,
        },
      });
      const media = Array.isArray(uploaded) ? uploaded[0] : uploaded;

      const data = {
        sku: product.sku,
        displayTitle: product.title,
        slug: product.slug,
        shortDescription: product.short,
        body: product.body,
        tags: product.tags,
        category: categoryIds[product.category],
        gallery: media?.id ? [media.id] : [],
      };

      if (existing.length) {
        await app.documents("api::product.product").update({
          documentId: existing[0].documentId,
          data,
          status: "published",
        });
      } else {
        await app.documents("api::product.product").create({
          data,
          status: "published",
        });
      }
      ok++;
      console.log(`seeded ${product.sku} (${product.category})`);
    } catch (err) {
      console.error(`FAIL ${product.sku}:`, err.message || err);
    }
  }

  console.log(`Done. ${ok}/${PRODUCTS.length} products seeded.`);
  await app.destroy();
  process.exit(0);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
