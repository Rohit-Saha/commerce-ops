import { api, API_BASE, ApiError, STOREFRONT_API_KEY } from "./client";
import { SECURITY_MODE, sessionCredential } from "../lib/authSession";

export interface Invoice {
  id: number;
  invoiceNumber: string;
  orderId: string;
  shipmentId: string | null;
  customerId: string;
  currency: string;
  subtotal: number;
  cgst: number;
  sgst: number;
  igst: number;
  total: number;
  status: string;
  paymentRef?: string | null;
  createdAt: string;
}

export function getInvoiceByOrder(orderId: string): Promise<Invoice> {
  return api.get<Invoice>(
    `/api/invoices/by-order/${encodeURIComponent(orderId)}`,
    undefined,
    sessionCredential(),
  );
}

export async function downloadInvoicePdf(invoiceId: number, filename?: string): Promise<void> {
  const headers: Record<string, string> = {};
  const init: RequestInit = { headers };
  if (SECURITY_MODE === "oidc") {
    init.credentials = "include";
  } else {
    const token = sessionCredential();
    headers["X-API-Key"] = STOREFRONT_API_KEY;
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }
  }
  const res = await fetch(`${API_BASE}/api/invoices/${invoiceId}/pdf`, init);
  if (!res.ok) {
    let message = `Request failed: ${res.status}`;
    try {
      const body = await res.json();
      message = (body as { message?: string }).message ?? message;
    } catch {
      // ignore
    }
    throw new ApiError(res.status, message);
  }
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename ?? `invoice-${invoiceId}.pdf`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
