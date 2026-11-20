/**
 * @file api.js
 * @description Centralized HTTP client service for Langley Children's Hospital Backend.
 * Follows defensive healthcare integration standards with AbortController signal support.
 */

import { config } from "../config/appConfig";

const API_BASE = config.apiBaseUrl;

/**
 * Custom error class for API failures containing HTTP status codes.
 */
export class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

/**
 * Fetches all synchronized pediatric patients from the Spring Boot backend.
 * @param {AbortSignal} [signal] - Optional AbortSignal to cancel in-flight requests.
 * @returns {Promise<Array<Object>>} List of patient entities.
 */
export async function getPatients(signal) {
  try {
    const response = await fetch(`${API_BASE}/patients`, {
      headers: {
        Accept: "application/json"
      },
      signal
    });

    if (!response.ok) {
      throw new ApiError(
        `Failed to fetch patients from regional EHR [HTTP ${response.status}]`,
        response.status
      );
    }

    return await response.json();
  } catch (error) {
    if (error.name === "AbortError") {
      // Request was canceled cleanly by user/unmount; rethrow or suppress
      throw error;
    }
    console.error("[API] getPatients failed:", error);
    throw error;
  }
}

/**
 * Fetches immunization records for a specific patient.
 * @param {number|string} patientId - Unique primary key ID of the patient.
 * @param {AbortSignal} [signal] - Optional AbortSignal.
 * @returns {Promise<Array<Object>>} List of vaccination records.
 */
export async function getPatientVaccinations(patientId, signal) {
  try {
    const response = await fetch(`${API_BASE}/patients/${patientId}/vaccinations`, {
      headers: { Accept: "application/json" },
      signal
    });

    if (!response.ok) return [];
    return await response.json();
  } catch (error) {
    if (error.name === "AbortError") return [];
    console.warn(`[API] Could not load immunizations for patient #${patientId}:`, error);
    return [];
  }
}

/**
 * Fetches diagnostic lab panels and LOINC-coded results for a specific patient.
 * @param {number|string} patientId - Unique primary key ID of the patient.
 * @param {AbortSignal} [signal] - Optional AbortSignal.
 * @returns {Promise<Array<Object>>} List of lab result records.
 */
export async function getPatientLabs(patientId, signal) {
  try {
    const response = await fetch(`${API_BASE}/patients/${patientId}/labs`, {
      headers: { Accept: "application/json" },
      signal
    });

    if (!response.ok) return [];
    return await response.json();
  } catch (error) {
    if (error.name === "AbortError") return [];
    console.warn(`[API] Could not load lab panels for patient #${patientId}:`, error);
    return [];
  }
}
