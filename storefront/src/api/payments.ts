import { api } from "./client";
import { sessionCredential } from "../lib/authSession";

export interface RazorpayOrderSession {
  keyId: string;
  razorpayOrderId: string;
  amountPaise: number;
  currency: string;
  provider: string;
}

export function createRazorpayOrder(amount: number, currency = "INR", receipt?: string): Promise<RazorpayOrderSession> {
  return api.post<RazorpayOrderSession>(
    "/api/payments/razorpay/orders",
    { amount, currency, receipt },
    undefined,
    sessionCredential(),
  );
}
