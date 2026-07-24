import { useEffect, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { sseUrl } from "../api/client";
import { useAuth } from "../lib/auth";
import { SECURITY_MODE } from "../lib/authSession";
import { useSseStatus } from "../lib/sseStatus";

export type StreamStatus = "connecting" | "live" | "disconnected";

/**
 * Subscribes to the order event stream and invalidates order-related queries
 * whenever a new event arrives. Auth: legacy uses ?apiKey=; OIDC uses
 * BFF session cookie (EventSource withCredentials) — see SSE_AUTH.md.
 */
export function useOrdersStream(): StreamStatus {
  const queryClient = useQueryClient();
  const { session } = useAuth();
  const authenticated = SECURITY_MODE === "oidc" ? !!session : !!session?.apiKey;
  const [status, setStatus] = useState<StreamStatus>("connecting");
  const retryRef = useRef<ReturnType<typeof setTimeout>>();

  useEffect(() => {
    if (!authenticated) {
      if (retryRef.current) {
        clearTimeout(retryRef.current);
        retryRef.current = undefined;
      }
      setStatus("disconnected");
      return;
    }

    let source: EventSource | null = null;
    let cancelled = false;

    const connect = () => {
      if (cancelled) return;
      setStatus("connecting");

      source = new EventSource(sseUrl("/api/stream/orders"), {
        withCredentials: SECURITY_MODE === "oidc",
      });

      source.onopen = () => {
        if (!cancelled) setStatus("live");
      };

      source.onmessage = () => {
        if (cancelled) return;
        setStatus("live");
        queryClient.invalidateQueries({ queryKey: ["orders"] });
        queryClient.invalidateQueries({ queryKey: ["order"] });
        queryClient.invalidateQueries({ queryKey: ["order-timeline"] });
        queryClient.invalidateQueries({ queryKey: ["shipments-by-order"] });
      };

      source.onerror = () => {
        if (cancelled) return;
        setStatus("disconnected");
        source?.close();
        if (retryRef.current) clearTimeout(retryRef.current);
        retryRef.current = setTimeout(() => {
          retryRef.current = undefined;
          connect();
        }, 4000);
      };
    };

    connect();

    return () => {
      cancelled = true;
      source?.close();
      source = null;
      if (retryRef.current) {
        clearTimeout(retryRef.current);
        retryRef.current = undefined;
      }
    };
  }, [queryClient, authenticated]);

  return status;
}

export function useConnectOrdersStream(): StreamStatus {
  const status = useOrdersStream();
  const { setStatus } = useSseStatus();

  useEffect(() => {
    setStatus(status);
  }, [status, setStatus]);

  return status;
}
