import { useSseStatus } from "../lib/sseStatus";

const LABELS: Record<string, string> = {
  idle: "idle",
  connecting: "connecting",
  live: "live",
  disconnected: "disconnected",
};

export function ConnectionIndicator() {
  const { status } = useSseStatus();
  return (
    <div className={`conn-indicator ${status}`} title="Live order stream status">
      <span className="conn-indicator__dot" />
      <span>{LABELS[status] ?? status}</span>
    </div>
  );
}
