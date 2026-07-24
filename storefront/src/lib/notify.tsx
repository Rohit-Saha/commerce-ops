import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { isBenignError, toUserMessage, type ErrorContext } from "./errors";

export type NoticeTone = "error" | "success" | "info";

interface Notice {
  id: number;
  tone: NoticeTone;
  message: string;
}

interface NotifyContextValue {
  notify: (message: string, tone?: NoticeTone) => void;
  notifyError: (err: unknown, context?: ErrorContext) => void;
  dismiss: (id?: number) => void;
}

const NotifyContext = createContext<NotifyContextValue | null>(null);

let nextId = 1;

export function NotifyProvider({ children }: { children: ReactNode }) {
  const [notice, setNotice] = useState<Notice | null>(null);

  const dismiss = useCallback((id?: number) => {
    setNotice((current) => {
      if (!current) return null;
      if (id === undefined || current.id === id) return null;
      return current;
    });
  }, []);

  const notify = useCallback((message: string, tone: NoticeTone = "info") => {
    const text = message.trim();
    if (!text) return;
    setNotice({ id: nextId++, tone, message: text });
  }, []);

  const notifyError = useCallback(
    (err: unknown, context: ErrorContext = "generic") => {
      if (isBenignError(err)) return;
      notify(toUserMessage(err, context), "error");
    },
    [notify],
  );

  return (
    <NotifyContext.Provider value={{ notify, notifyError, dismiss }}>
      {children}
      {notice ? (
        <div className="notify-host" role="status" aria-live="polite">
          <div className={`notify-banner notify-banner--${notice.tone}`}>
            <p className="notify-banner__text">{notice.message}</p>
            <button
              type="button"
              className="notify-banner__close"
              aria-label="Dismiss notification"
              onClick={() => dismiss(notice.id)}
            >
              ×
            </button>
          </div>
        </div>
      ) : null}
    </NotifyContext.Provider>
  );
}

export function useNotify(): NotifyContextValue {
  const ctx = useContext(NotifyContext);
  if (!ctx) {
    throw new Error("useNotify must be used within NotifyProvider");
  }
  return ctx;
}

/** Push a friendly error banner once when a query fails. */
export function useNotifyQueryError(
  isError: boolean,
  error: unknown,
  context: ErrorContext,
): void {
  const { notifyError } = useNotify();
  const lastKey = useRef<string | null>(null);

  useEffect(() => {
    if (!isError) {
      lastKey.current = null;
      return;
    }
    const key = `${context}:${error instanceof Error ? `${error.name}:${error.message}` : "unknown"}`;
    if (lastKey.current === key) return;
    lastKey.current = key;
    notifyError(error, context);
  }, [isError, error, context, notifyError]);
}
