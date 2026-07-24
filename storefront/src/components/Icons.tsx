/** Northline wordmark mark — horizon line through an N. */
export function BrandMark({ className }: { className?: string }) {
  return (
    <svg
      className={className}
      viewBox="0 0 40 40"
      width="40"
      height="40"
      aria-hidden="true"
      focusable="false"
    >
      <rect width="40" height="40" rx="10" fill="currentColor" opacity="0.12" />
      <path
        d="M11 28V12h3.2l8.4 11.2V12H26v16h-3.2L14.4 16.8V28H11Z"
        fill="currentColor"
      />
      <path d="M8 20.5h24" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" opacity="0.55" />
    </svg>
  );
}

export function CartIcon({ className }: { className?: string }) {
  return (
    <svg
      className={className}
      viewBox="0 0 24 24"
      width="22"
      height="22"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      <path d="M6.5 7h12.2l-1.1 9.2a1.6 1.6 0 0 1-1.6 1.4H9.2a1.6 1.6 0 0 1-1.6-1.4L6.5 7Z" />
      <path d="M9 7V5.6A2.6 2.6 0 0 1 11.6 3h.8A2.6 2.6 0 0 1 15 5.6V7" />
      <circle cx="10" cy="20" r="1.1" fill="currentColor" stroke="none" />
      <circle cx="16.5" cy="20" r="1.1" fill="currentColor" stroke="none" />
    </svg>
  );
}

export function UserIcon({ className }: { className?: string }) {
  return (
    <svg
      className={className}
      viewBox="0 0 24 24"
      width="20"
      height="20"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      <circle cx="12" cy="8" r="3.2" />
      <path d="M5.5 19.2c1.6-3 4-4.5 6.5-4.5s4.9 1.5 6.5 4.5" />
    </svg>
  );
}
