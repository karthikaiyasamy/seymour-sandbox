/**
 * @file appConfig.js
 * @description Centralized application configuration.
 * Reads environment properties exposed by Vite (prefixed with `VITE_`)
 * and provides safe runtime fallback values.
 */

export const config = {
  /**
   * Base endpoint URL for the Spring Boot backend REST APIs.
   * e.g., "http://localhost:8081/api"
   */
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL || "http://localhost:8081/api",

  /**
   * Frequency (in milliseconds) of background polling for inbound clinical syncs.
   */
  pollingIntervalMs: Number(import.meta.env.VITE_POLLING_INTERVAL_MS) || 8000,

  /**
   * Portal Application Display Title.
   */
  appTitle: import.meta.env.VITE_APP_TITLE || "Langley Children's Hospital"
};
