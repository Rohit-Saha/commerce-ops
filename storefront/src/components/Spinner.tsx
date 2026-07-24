type SpinnerSize = "sm" | "md" | "lg";

export function Spinner({
  size = "md",
  label = "Loading",
  className = "",
}: {
  size?: SpinnerSize;
  /** Accessible name; visually hidden unless `showLabel` is set on LoadingState. */
  label?: string;
  className?: string;
}) {
  return (
    <span className={`spinner spinner--${size} ${className}`.trim()} role="status" aria-label={label}>
      <span className="spinner__ring" aria-hidden="true" />
    </span>
  );
}

export function LoadingState({
  label = "Loading…",
  size = "md",
  className = "",
}: {
  label?: string;
  size?: SpinnerSize;
  className?: string;
}) {
  return (
    <div className={`loading-state ${className}`.trim()}>
      <Spinner size={size} label={label} />
      <span className="loading-state__label">{label}</span>
    </div>
  );
}
