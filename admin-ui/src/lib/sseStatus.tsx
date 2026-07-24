import { createContext, useContext, useMemo, useState, type ReactNode } from "react";
import type { StreamStatus } from "../hooks/useOrdersStream";

interface SseStatusContextValue {
  status: StreamStatus | "idle";
  setStatus: (status: StreamStatus) => void;
}

const SseStatusContext = createContext<SseStatusContextValue | undefined>(undefined);

/**
 * Holds the current SSE connection status so the header's connection
 * indicator can reflect it, even though the EventSource itself is only
 * opened on the Orders and Order detail pages (see useOrdersStream).
 */
export function SseStatusProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<StreamStatus | "idle">("idle");
  const value = useMemo(() => ({ status, setStatus }), [status]);
  return <SseStatusContext.Provider value={value}>{children}</SseStatusContext.Provider>;
}

export function useSseStatus() {
  const ctx = useContext(SseStatusContext);
  if (!ctx) throw new Error("useSseStatus must be used within SseStatusProvider");
  return ctx;
}
