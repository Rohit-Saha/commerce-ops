import { api, API_BASE, ApiError } from "./client";
import { loadSession } from "../lib/authSession";

export interface InvoiceLine {
  lineNo: number;
  sku: string;
  description: string;
  quantity: number;
  unitPrice: number;
  lineGross: number;
  taxable: number;
  cgst: number;
  sgst: number;
  igst: number;
}

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
  buyerName?: string | null;
  sellerLegalName?: string;
  sellerGstin?: string;
  paymentRef?: string | null;
  lines: InvoiceLine[];
  createdAt: string;
}

export function getInvoiceByOrder(orderId: string): Promise<Invoice> {
  return api.get<Invoice>(`/api/invoices/by-order/${encodeURIComponent(orderId)}`);
}

export async function downloadInvoicePdf(invoiceId: number, filename?: string): Promise<void> {
  const apiKey = loadSession()?.apiKey ?? "";
  const res = await fetch(`${API_BASE}/api/invoices/${invoiceId}/pdf`, {
    headers: apiKey ? { "X-API-Key": apiKey } : {},
  });
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
