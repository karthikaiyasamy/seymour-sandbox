/**
 * @file usePatients.js
 * @description Custom React hook managing the patient synchronization lifecycle.
 * Encapsulates polling, abort controllers, error handling, and manual refreshes.
 */

import { useState, useEffect, useCallback, useRef } from "react";
import { getPatients } from "../services/api";
import { config } from "../config/appConfig";

const POLLING_INTERVAL_MS = config.pollingIntervalMs;

/**
 * Custom hook to subscribe to the regional pediatric patient roster.
 * @returns {{
 *   patients: Array<Object>,
 *   isLoading: boolean,
 *   isConnected: boolean,
 *   lastSyncTime: Date | null,
 *   error: string | null,
 *   refresh: () => Promise<void>
 * }}
 */
export function usePatients() {
  const [patients, setPatients] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isConnected, setIsConnected] = useState(false);
  const [lastSyncTime, setLastSyncTime] = useState(null);
  const [error, setError] = useState(null);

  // Keep a reference to the active AbortController to cancel pending requests on unmount
  const abortControllerRef = useRef(null);

  const fetchRoster = useCallback(async (isManual = false) => {
    // Abort previous in-flight request if still running
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }
    abortControllerRef.current = new AbortController();

    if (isManual) setIsLoading(true);
    setError(null);

    try {
      const data = await getPatients(abortControllerRef.current.signal);
      setPatients(data || []);
      setIsConnected(true);
      setLastSyncTime(new Date());
    } catch (err) {
      if (err.name === "AbortError") {
        // Intentional cancellation, ignore
        return;
      }
      setIsConnected(false);
      setError(
        "Unable to connect to Langley Pediatric Backend (Port 8081). Ensure the Spring Boot service is active."
      );
    } finally {
      if (isManual) setIsLoading(false);
    }
  }, []);

  // Polling lifecycle effect
  useEffect(() => {
    fetchRoster(true); // Initial fetch with spinner

    const intervalId = setInterval(() => {
      fetchRoster(false); // Background periodic poll
    }, POLLING_INTERVAL_MS);

    return () => {
      clearInterval(intervalId);
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }
    };
  }, [fetchRoster]);

  const refresh = useCallback(() => fetchRoster(true), [fetchRoster]);

  return {
    patients,
    isLoading,
    isConnected,
    lastSyncTime,
    error,
    refresh
  };
}
