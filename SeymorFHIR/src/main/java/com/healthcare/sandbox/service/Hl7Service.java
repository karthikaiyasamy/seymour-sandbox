package com.healthcare.sandbox.service;

import com.healthcare.sandbox.model.AdtEvent;
import com.healthcare.sandbox.model.Hl7AuditLog;
import com.healthcare.sandbox.model.Patient;
import com.healthcare.sandbox.repository.Hl7AuditLogRepository;
import com.healthcare.sandbox.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import com.healthcare.sandbox.model.PatientMatchReview;
import com.healthcare.sandbox.repository.PatientMatchReviewRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class Hl7Service {

    private final PatientRepository patientRepo;
    private final Hl7AuditLogRepository auditLogRepo;
    private final PatientMatchReviewRepository matchReviewRepo;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * Generates a pipe-delimited HL7 v2.4 ADT message from an AdtEvent.
     */
    public String generateHl7(AdtEvent event) {
        Patient patient = event.getPatient();
        String dobStr = patient.getDateOfBirth() != null ? patient.getDateOfBirth().format(DATE_FORMATTER) : "";
        String eventTimeStr = event.getEventDatetime() != null ? event.getEventDatetime().format(DATETIME_FORMATTER) : LocalDateTime.now().format(DATETIME_FORMATTER);

        String genderCode = "U";
        if (patient.getGender() != null) {
            String g = patient.getGender().toLowerCase();
            if (g.startsWith("m")) genderCode = "M";
            else if (g.startsWith("f")) genderCode = "F";
            else if (g.startsWith("o")) genderCode = "O";
        }

        String patientClassCode = "O";
        if (event.getPatientClass() != null) {
            String pc = event.getPatientClass().toUpperCase();
            if (pc.contains("INPATIENT")) patientClassCode = "I";
            else if (pc.contains("EMERGENCY")) patientClassCode = "E";
            else if (pc.contains("OUTPATIENT")) patientClassCode = "O";
        }

        // 1. MSH Segment
        String msh = String.format("MSH|^~\\&|SANDBOX_EHR|%s|REC_APP|REC_FAC|%s||ADT^%s^ADT_%s|MSG%05d|P|2.4",
                escape(event.getFacility()), eventTimeStr, event.getEventCode(), event.getEventCode(), event.getId() != null ? event.getId() : 1);

        // 2. PID Segment
        String addressLine = patient.getAddressLine() != null ? patient.getAddressLine() : "";
        String city = patient.getCity() != null ? patient.getCity() : "";
        String province = patient.getProvince() != null ? patient.getProvince() : "";
        String postalCode = patient.getPostalCode() != null ? patient.getPostalCode() : "";
        String pid = String.format("PID|1||%s^^^MRN||%s^%s||%s|%s|||%s^^%s^%s^%s^CA||%s||||||%s",
                patient.getMrn(), escape(patient.getLastName()), escape(patient.getFirstName()),
                dobStr, genderCode, escape(addressLine), escape(city), escape(province), escape(postalCode),
                patient.getPhone() != null ? patient.getPhone() : "",
                patient.getHealthCardNumber() != null ? patient.getHealthCardNumber() : "");

        // 3. PV1 Segment
        String ward = event.getWard() != null ? event.getWard() : "";
        String room = event.getRoom() != null ? event.getRoom() : "";
        String bed = event.getBed() != null ? event.getBed() : "";
        String facility = event.getFacility() != null ? event.getFacility() : "";
        String pv1 = String.format("PV1|1|%s|%s^%s^%s^%s||||%s|||||||||||%s|||||||||||||||||||||||||%s",
                patientClassCode, escape(ward), escape(room), escape(bed), escape(facility),
                escape(event.getAttendingPhysician()), event.getVisitNumber(), eventTimeStr);

        // 4. DG1 Segment
        String dg1 = "";
        if (event.getAdmittingDiagnosis() != null && !event.getAdmittingDiagnosis().isEmpty()) {
            dg1 = "\nDG1|1||||" + escape(event.getAdmittingDiagnosis());
        }

        return msh + "\n" + pid + "\n" + pv1 + (dg1.isEmpty() ? "" : dg1);
    }

    /**
     * Parses a pipe-delimited HL7 v2 ADT message and returns an AdtEvent.
     * Registers a new patient if the MRN is not already found in the database.
     * Includes full message audit logging and MSH-10 idempotency protection.
     */
    public AdtEvent parseHl7(String hl7Text) {
        String correlationId = UUID.randomUUID().toString();
        String payloadHash = calculateSha256(hl7Text);

        String[] lines = hl7Text.split("[\\r\\n]+");
        
        String messageControlId = "MSG-" + System.currentTimeMillis();
        String sendingFacility = "UNKNOWN_FACILITY";
        String sendingApp = "UNKNOWN_APP";
        String eventType = "ADMIT";
        String eventCode = "A01";
        LocalDateTime eventDatetime = LocalDateTime.now();

        // Extract MSH details first
        for (String line : lines) {
            String[] fields = line.split("\\|");
            if (fields.length > 0 && "MSH".equals(fields[0])) {
                if (fields.length > 2) sendingApp = fields[2];
                if (fields.length > 3) sendingFacility = fields[3];
                if (fields.length > 6) eventDatetime = parseDateTime(fields[6]);
                if (fields.length > 8) {
                    String eventStr = fields[8];
                    String[] subfields = eventStr.split("\\^");
                    if (subfields.length > 1) {
                        eventCode = subfields[1];
                        eventType = mapEventCodeToType(eventCode);
                    }
                }
                if (fields.length > 9 && !fields[9].isEmpty()) {
                    messageControlId = fields[9];
                }
                break;
            }
        }

        // 1. Check MSH-10 Control ID Idempotency FIRST
        Optional<Hl7AuditLog> existingControlLog = auditLogRepo.findByMessageControlId(messageControlId);
        if (existingControlLog.isPresent()) {
            log.warn("HL7 Idempotency Rejection: MSH-10 Message Control ID [{}] already processed.", messageControlId);
            throw new IllegalArgumentException("Duplicate MSH-10 Message Control ID rejected: " + messageControlId);
        }

        // 2. Check Payload SHA-256 Hash Idempotency SECOND
        Optional<Hl7AuditLog> existingHashLog = auditLogRepo.findByPayloadHash(payloadHash);
        if (existingHashLog.isPresent()) {
            Hl7AuditLog prevLog = existingHashLog.get();
            log.warn("HL7 Idempotency Rejection: Duplicate payload received. Original Correlation ID: {}", prevLog.getCorrelationId());
            throw new IllegalArgumentException("Duplicate HL7 payload rejected. Previously processed under Correlation ID: " + prevLog.getCorrelationId());
        }

        // Create Initial Audit Log (RECEIVED)
        Hl7AuditLog auditLog = auditLogRepo.save(Hl7AuditLog.builder()
                .messageControlId(messageControlId)
                .correlationId(correlationId)
                .sendingFacility(sendingFacility)
                .sendingApplication(sendingApp)
                .eventType(eventType + "^" + eventCode)
                .payloadHash(payloadHash)
                .status("RECEIVED")
                .receivedAt(LocalDateTime.now())
                .build());

        try {
            // Patient demographics
            String mrn = null;
            String lastName = "";
            String firstName = "";
            LocalDate dob = null;
            String gender = "unknown";
            String phone = "";
            String addressLine = "";
            String city = "";
            String province = "BC";
            String postalCode = "";
            String healthCardNumber = "";

            // Encounter details
            String patientClass = "OUTPATIENT";
            String ward = null;
            String room = null;
            String bed = null;
            String facility = sendingFacility;
            String attendingPhysician = "";
            String visitNumber = null;
            String admittingDiagnosis = null;

            for (String line : lines) {
                String[] fields = line.split("\\|");
                if (fields.length == 0) continue;
                
                String segmentName = fields[0];
                switch (segmentName) {
                    case "PID":
                        if (fields.length > 3) {
                            mrn = parseSubfield(fields[3], 0);
                        }
                        if (fields.length > 5) {
                            lastName = parseSubfield(fields[5], 0);
                            firstName = parseSubfield(fields[5], 1);
                        }
                        if (fields.length > 7 && !fields[7].trim().isEmpty()) {
                            dob = parseDate(fields[7]);
                        }
                        if (fields.length > 8) {
                            String g = fields[8];
                            if ("M".equalsIgnoreCase(g)) gender = "male";
                            else if ("F".equalsIgnoreCase(g)) gender = "female";
                            else if ("O".equalsIgnoreCase(g)) gender = "other";
                        }
                        if (fields.length > 11) {
                            addressLine = parseSubfield(fields[11], 0);
                            city = parseSubfield(fields[11], 2);
                            province = parseSubfield(fields[11], 3);
                            postalCode = parseSubfield(fields[11], 4);
                        }
                        if (fields.length > 13) {
                            phone = fields[13];
                        }
                        if (fields.length > 19) {
                            healthCardNumber = fields[19];
                        }
                        break;

                    case "PV1":
                        if (fields.length > 2) {
                            String pc = fields[2];
                            if ("I".equalsIgnoreCase(pc)) patientClass = "INPATIENT";
                            else if ("E".equalsIgnoreCase(pc)) patientClass = "EMERGENCY";
                            else if ("O".equalsIgnoreCase(pc)) patientClass = "OUTPATIENT";
                        }
                        if (fields.length > 3) {
                            ward = parseSubfield(fields[3], 0);
                            room = parseSubfield(fields[3], 1);
                            bed = parseSubfield(fields[3], 2);
                            facility = parseSubfield(fields[3], 3);
                        }
                        if (fields.length > 7) {
                            attendingPhysician = parseSubfield(fields[7], 0);
                        }
                        if (fields.length > 19) {
                            visitNumber = fields[19];
                        }
                        break;

                    case "DG1":
                        if (fields.length > 5) {
                            admittingDiagnosis = parseSubfield(fields[5], 1);
                            if (admittingDiagnosis == null || admittingDiagnosis.isEmpty()) {
                                admittingDiagnosis = parseSubfield(fields[5], 0);
                            }
                        }
                        break;
                }
            }

        if (mrn == null || mrn.isEmpty()) {
            throw new IllegalArgumentException("HL7 message is missing Patient MRN (PID-3)");
        }

        if (visitNumber == null || visitNumber.isEmpty()) {
            visitNumber = "VN-" + System.currentTimeMillis() % 1000000;
        }

        // Patient Demographics & Identity Match Resolution
        Optional<Patient> existingMrnPatient = patientRepo.findByMrn(mrn);
        Patient patient;

        if (existingMrnPatient.isPresent()) {
            patient = existingMrnPatient.get();
        } else {
            // Compute match score against active patient database for conflict detection
            double maxMatchScore = 0.0;
            for (Patient p : patientRepo.findByActiveTrue()) {
                double score = 0.0;
                if (p.getLastName() != null && p.getLastName().trim().equalsIgnoreCase(lastName.trim())) score += 0.35;
                if (p.getFirstName() != null && p.getFirstName().trim().equalsIgnoreCase(firstName.trim())) score += 0.25;
                if (p.getDateOfBirth() != null && dob != null && p.getDateOfBirth().equals(dob)) score += 0.40;
                if (score > maxMatchScore) maxMatchScore = score;
            }

            // Ambiguous Match Threshold (Conflict Resolution Trigger)
            if (maxMatchScore >= 0.35 && maxMatchScore < 0.85) {
                log.warn("HL7 Identity Conflict Detected (Match Score: {}). Flagging PENDING_REVIEW record and aborting patient creation.", maxMatchScore);
                matchReviewRepo.save(PatientMatchReview.builder()
                        .inboundMrn(mrn)
                        .inboundPhn(healthCardNumber)
                        .inboundFirstName(firstName)
                        .inboundLastName(lastName)
                        .inboundDob(dob != null ? dob.toString() : null)
                        .matchScore(maxMatchScore)
                        .status("PENDING_REVIEW")
                        .createdAt(LocalDateTime.now())
                        .build());
                
                throw new IllegalArgumentException("HL7 Identity Conflict Detected (Match Score: " + maxMatchScore + "). Patient creation halted. Record queued for manual PENDING_REVIEW.");
            }

            log.info("HL7 Parser: Registering new patient record from HL7 ADT message");
            patient = patientRepo.save(Patient.builder()
                    .mrn(mrn)
                    .firstName(firstName)
                    .lastName(lastName)
                    .dateOfBirth(dob)
                    .gender(gender)
                    .phone(phone)
                    .addressLine(addressLine)
                    .city(city)
                    .province(province)
                    .postalCode(postalCode)
                    .healthCardNumber(healthCardNumber)
                    .active(true)
                    .build());
        }

            auditLog.setStatus("TRANSFORMED");
            auditLogRepo.save(auditLog);

            return AdtEvent.builder()
                    .patient(patient)
                    .eventType(eventType)
                    .eventCode(eventCode)
                    .eventDatetime(eventDatetime)
                    .facility(facility)
                    .ward(ward)
                    .room(room)
                    .bed(bed)
                    .attendingPhysician(attendingPhysician)
                    .admittingDiagnosis(admittingDiagnosis)
                    .visitNumber(visitNumber)
                    .patientClass(patientClass)
                    .notes("Generated via HL7 v2 Message parsing.")
                    .build();

        } catch (Exception ex) {
            auditLog.setStatus("FAILED");
            auditLog.setFailureReason(ex.getMessage());
            auditLog.setProcessedAt(LocalDateTime.now());
            auditLogRepo.save(auditLog);
            log.error("HL7 Processing Failure for Correlation ID [{}]: {}", correlationId, ex.getMessage());
            throw ex;
        }
    }

    public String calculateSha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(text.hashCode());
        }
    }

    private String mapEventCodeToType(String code) {
        return switch (code) {
            case "A01" -> "ADMIT";
            case "A02" -> "TRANSFER";
            case "A03" -> "DISCHARGE";
            case "A04" -> "REGISTER";
            case "A08" -> "UPDATE";
            default -> "UPDATE";
        };
    }

    private String parseSubfield(String field, int index) {
        if (field == null) return "";
        String[] parts = field.split("\\^");
        if (parts.length > index) {
            return parts[index].trim();
        }
        return parts.length > 0 && index == 0 ? parts[0].trim() : "";
    }

    private LocalDate parseDate(String val) {
        try {
            if (val.length() >= 8) {
                return LocalDate.parse(val.substring(0, 8), DATE_FORMATTER);
            }
        } catch (Exception ex) {
            log.warn("Failed to parse HL7 date: {}", val);
        }
        return null;
    }

    private LocalDateTime parseDateTime(String val) {
        try {
            if (val.length() >= 14) {
                return LocalDateTime.parse(val.substring(0, 14), DATETIME_FORMATTER);
            } else if (val.length() >= 8) {
                return LocalDate.parse(val.substring(0, 8), DATE_FORMATTER).atStartOfDay();
            }
        } catch (Exception ex) {
            log.warn("Failed to parse HL7 datetime: {}", val);
        }
        return LocalDateTime.now();
    }

    public String generateVxu(Patient patient, String vaccineCode, String vaccineName, String dateStr, String lotNumber) {
        String now = LocalDateTime.now().format(DATETIME_FORMATTER);
        String dob = patient.getDateOfBirth() != null ? patient.getDateOfBirth().format(DATE_FORMATTER) : "";
        String gender = patient.getGender() != null && !patient.getGender().isEmpty() ? patient.getGender().substring(0, 1).toUpperCase() : "U";

        return String.format(
            "MSH|^~\\&|SEYMOUR_EHR|SEYMOUR_CLINIC|MIRTH_INTEGRATION|MIRTH_FACILITY|%s||VXU^V04^VXU_V04|MSG%d|P|2.4\n" +
            "PID|1||%s^^^MRN||%s^%s||%s|%s|||%s^^%s^%s^%s^CA\n" +
            "ORC|RE|||||||Dr. Arthur Pendelton\n" +
            "RXA|0|1|%s|%s|%s^%s^CVX|0.5|mL^^ISO||00^Administered^NIP||||||%s",
            now, System.currentTimeMillis() % 1000000, patient.getMrn(), escape(patient.getLastName()), escape(patient.getFirstName()),
            dob, gender, escape(patient.getAddressLine() != null ? patient.getAddressLine() : ""),
            escape(patient.getCity() != null ? patient.getCity() : ""), escape(patient.getProvince() != null ? patient.getProvince() : ""),
            escape(patient.getPostalCode() != null ? patient.getPostalCode() : ""), dateStr, dateStr, vaccineCode, escape(vaccineName), escape(lotNumber)
        );
    }

    public String generateOru(Patient patient, String testCode, String testName, String value, String unit, String flag, String dateStr) {
        String now = LocalDateTime.now().format(DATETIME_FORMATTER);
        String dob = patient.getDateOfBirth() != null ? patient.getDateOfBirth().format(DATE_FORMATTER) : "";
        String gender = patient.getGender() != null && !patient.getGender().isEmpty() ? patient.getGender().substring(0, 1).toUpperCase() : "U";

        return String.format(
            "MSH|^~\\&|SEYMOUR_EHR|SEYMOUR_CLINIC|MIRTH_INTEGRATION|MIRTH_FACILITY|%s||ORU^R01^ORU_R01|MSG%d|P|2.4\n" +
            "PID|1||%s^^^MRN||%s^%s||%s|%s|||%s^^%s^%s^%s^CA\n" +
            "OBR|1||VN-%d|%s^%s^LN|||%s\n" +
            "OBX|1|NM|%s^%s^LN||%s|%s||%s|||F|||%s",
            now, System.currentTimeMillis() % 1000000, patient.getMrn(), escape(patient.getLastName()), escape(patient.getFirstName()),
            dob, gender, escape(patient.getAddressLine() != null ? patient.getAddressLine() : ""),
            escape(patient.getCity() != null ? patient.getCity() : ""), escape(patient.getProvince() != null ? patient.getProvince() : ""),
            escape(patient.getPostalCode() != null ? patient.getPostalCode() : ""), System.currentTimeMillis() % 100000,
            testCode, escape(testName), dateStr, testCode, escape(testName), value, unit, flag, dateStr
        );
    }

    private String escape(String str) {
        if (str == null) return "";
        return str.replace("|", "\\F\\").replace("^", "\\S\\").replace("&", "\\T\\");
    }
}
