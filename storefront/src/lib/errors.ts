import { ApiError } from "../api/client";

export type ErrorContext =
  | "login"
  | "register"
  | "profile"
  | "addresses"
  | "checkout"
  | "orders"
  | "order"
  | "catalog"
  | "cart"
  | "invoice"
  | "generic";

const CONTEXT_DEFAULTS: Record<ErrorContext, string> = {
  login: "We couldn’t sign you in. Check your email and password, then try again.",
  register: "We couldn’t create your account. Please check your details and try again.",
  profile: "We couldn’t update your profile. Please try again.",
  addresses: "We couldn’t update your addresses. Please try again.",
  checkout: "We couldn’t complete checkout. Please try again.",
  orders: "We couldn’t load your orders. Please try again.",
  order: "We couldn’t load this order. Please try again.",
  catalog: "We couldn’t load the catalog. Please try again.",
  cart: "We couldn’t update your cart. Please try again.",
  invoice: "We couldn’t download the invoice. Please try again shortly.",
  generic: "Something went wrong. Please try again.",
};

function normalize(message: string): string {
  return message.trim().toLowerCase();
}

function mapKnownBackendMessage(raw: string, context: ErrorContext): string | null {
  const msg = normalize(raw);

  if (msg.includes("invalid email or password") || msg.includes("invalid credentials")) {
    return "That email or password doesn’t match our records.";
  }
  if (msg.includes("email already registered")) {
    return "An account with this email already exists. Try signing in instead.";
  }
  if (msg.includes("current password is incorrect")) {
    return "Your current password is incorrect.";
  }
  if (msg.includes("current password is required")) {
    return "Enter your current password to set a new one.";
  }
  if (msg.includes("insufficient") && msg.includes("stock")) {
    return "Some items are out of stock. Update your cart and try again.";
  }
  if (msg.includes("payment cancelled") || msg.includes("payment canceled")) {
    return "Payment cancelled";
  }
  if (msg.includes("payment failed") || msg.includes("payment was declined")) {
    return "Payment didn’t go through. You can try another method.";
  }
  if (msg.includes("failed to load razorpay") || msg.includes("razorpay checkout is unavailable")) {
    return "Payment checkout couldn’t load. Check your connection and try again.";
  }
  if (msg.includes("invoice not ready")) {
    return "Your invoice isn’t ready yet. Please wait a moment and try again.";
  }
  if (msg.includes("login required") || msg.includes("missing bearer") || msg.includes("invalid token")) {
    return "Please sign in to continue.";
  }
  if (msg.includes("select a delivery address")) {
    return "Select a delivery address or add a new one.";
  }
  if (context === "order" && (msg.includes("not found") || msg.includes("order not found"))) {
    return "We couldn’t find that order.";
  }
  if (context === "catalog" && msg.includes("not found")) {
    return "That product isn’t available.";
  }

  return null;
}

function looksTechnical(message: string): boolean {
  const msg = normalize(message);
  return (
    msg.startsWith("request failed:") ||
    msg.includes("internal server error") ||
    msg.includes("exception") ||
    msg.includes("stacktrace") ||
    msg.includes("nullpointer") ||
    msg.includes("sql") ||
    msg.includes("jdbc") ||
    msg.includes("hibernate") ||
    msg.includes("org.springframework") ||
    msg.includes("java.") ||
    msg.includes("{") ||
    msg.includes("}") ||
    /\b[a-z]+exception\b/.test(msg)
  );
}

function messageForStatus(status: number, context: ErrorContext): string {
  if (status === 0) {
    return "Unable to reach the server. Check your connection and try again.";
  }
  if (status === 400) {
    if (context === "login") return "Please check your email and password.";
    if (context === "register") return "Please check your details and try again.";
    if (context === "checkout") return "Some order details look invalid. Review and try again.";
    if (context === "profile") return "Please check the profile details and try again.";
    if (context === "addresses") return "Please check the address details and try again.";
    return "Please check your details and try again.";
  }
  if (status === 401) {
    if (context === "login") return "That email or password doesn’t match our records.";
    return "Your session expired. Please sign in again.";
  }
  if (status === 403) {
    return "You don’t have permission to do that.";
  }
  if (status === 404) {
    if (context === "order") return "We couldn’t find that order.";
    if (context === "invoice") return "Invoice not found yet. Please try again shortly.";
    if (context === "catalog") return "That product isn’t available.";
    return "We couldn’t find what you were looking for.";
  }
  if (status === 409) {
    if (context === "register") return "An account with this email already exists. Try signing in instead.";
    return "That action conflicts with the current state. Refresh and try again.";
  }
  if (status === 422) {
    return "Please check your details and try again.";
  }
  if (status === 429) {
    return "Too many requests. Please wait a moment and try again.";
  }
  if (status >= 500) {
    return "Something went wrong on our side. Please try again in a moment.";
  }
  return CONTEXT_DEFAULTS[context];
}

/** Map API / thrown errors to short, customer-facing copy. */
export function toUserMessage(err: unknown, context: ErrorContext = "generic"): string {
  if (err instanceof ApiError) {
    const known = mapKnownBackendMessage(err.message, context);
    if (known) return known;
    // Prefer standardized backend `message` when it is already customer-facing.
    if (!looksTechnical(err.message) && err.message.trim()) {
      return err.message;
    }
    return messageForStatus(err.status, context);
  }

  if (err instanceof TypeError) {
    return "Unable to reach the server. Check your connection and try again.";
  }

  if (err instanceof Error) {
    const known = mapKnownBackendMessage(err.message, context);
    if (known) return known;
    if (!looksTechnical(err.message) && err.message.trim()) {
      return err.message;
    }
  }

  return CONTEXT_DEFAULTS[context];
}

export function isBenignError(err: unknown): boolean {
  if (!(err instanceof Error)) return false;
  const msg = normalize(err.message);
  return msg === "payment cancelled" || msg === "payment canceled";
}
