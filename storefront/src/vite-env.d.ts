/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE: string;
  readonly VITE_STOREFRONT_API_KEY: string;
  readonly VITE_SECURITY_MODE?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
