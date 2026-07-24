export interface CatalogItem {
  sku: string;
  name: string;
  displayTitle?: string;
  inventoryName?: string;
  slug?: string;
  shortDescription?: string;
  bodyText?: string;
  categorySlug?: string;
  categoryName?: string;
  tags?: string[];
  imageUrls?: string[];
  primaryImageUrl?: string;
  unitPrice: number;
  availableQty: number;
  inStock: boolean;
}

export interface FacetBucket {
  value: string;
  count: number;
}

export interface CatalogSearchResult {
  items: CatalogItem[];
  facets: {
    categories: FacetBucket[];
    tags: FacetBucket[];
  };
  total: number;
}

export type OrderStatus =
  | "PENDING"
  | "RESERVING"
  | "RESERVED"
  | "PAYMENT_PENDING"
  | "PAID"
  | "SHIPPING"
  | "DELIVERED"
  | "COMPLETED"
  | "CANCELLED"
  | "FAILED"
  | string;

export interface OrderLine {
  sku: string;
  quantity: number;
  unitPrice: number;
}

export interface ShippingAddress {
  recipientName: string;
  line1: string;
  line2?: string | null;
  city: string;
  state: string;
  postalCode: string;
  country: string;
  sourceAddressId?: string | null;
  isDefault?: boolean;
  id?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface Order {
  id: string;
  customerId: string;
  status: OrderStatus;
  totalAmount: number;
  currency: string;
  idempotencyKey: string;
  lines: OrderLine[];
  shippingAddress?: ShippingAddress | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateOrderInput {
  currency: string;
  lines: OrderLine[];
  shippingAddressId?: string;
  shippingAddress?: Omit<ShippingAddress, "id" | "createdAt" | "updatedAt" | "sourceAddressId">;
  razorpayOrderId: string;
  razorpayPaymentId: string;
  razorpaySignature: string;
}

export interface CustomerProfile {
  id: string;
  email: string;
  displayName: string;
  createdAt: string;
}

export interface AuthResponse {
  token: string;
  customer: CustomerProfile;
}

export type Address = ShippingAddress & {
  id: string;
  isDefault: boolean;
  createdAt: string;
  updatedAt: string;
};

export interface AddressInput {
  recipientName: string;
  line1: string;
  line2?: string;
  city: string;
  state: string;
  postalCode: string;
  country?: string;
  isDefault?: boolean;
}
