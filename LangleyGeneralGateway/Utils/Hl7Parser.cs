using System;
using System.Collections.Generic;

namespace LangleyGeneralGateway.Utils
{
    public record Hl7ParsedMessage(
        string MessageControlId,
        string MessageType,
        string PatientMrn,
        string PatientPhn,
        string FirstName,
        string LastName,
        string DateOfBirth,
        string Gender,
        string VisitNumber,
        string AdmittingDiagnosis,
        List<Hl7ObservationSegment> Observations
    );

    public record Hl7ObservationSegment(
        string ObservationId,
        string Code,
        string Display,
        string Value,
        string Units,
        string Flag
    );

    public static class Hl7Parser
    {
        public static Hl7ParsedMessage Parse(string rawMessage)
        {
            if (string.IsNullOrWhiteSpace(rawMessage))
            {
                throw new ArgumentException("HL7 message content cannot be null or empty.");
            }

            string[] lines = rawMessage.Split(new[] { "\r\n", "\n", "\r" }, StringSplitOptions.RemoveEmptyEntries);
            
            string messageControlId = "MSG-" + DateTime.UtcNow.Ticks;
            string messageType = "ADT^A01";
            string mrn = "";
            string phn = "";
            string firstName = "";
            string lastName = "";
            string dob = "";
            string gender = "";
            string visitNumber = "";
            string admittingDiagnosis = "";
            var observations = new List<Hl7ObservationSegment>();

            foreach (var line in lines)
            {
                var fields = line.Split('|');
                if (fields.Length == 0) continue;

                string segmentName = fields[0].Trim().ToUpper();

                switch (segmentName)
                {
                    case "MSH":
                        if (fields.Length > 8) messageType = fields[8];
                        if (fields.Length > 9) messageControlId = fields[9];
                        break;

                    case "PID":
                        // PID-3: Patient ID / MRN
                        if (fields.Length > 3 && !string.IsNullOrEmpty(fields[3]))
                        {
                            var idParts = fields[3].Split('^');
                            mrn = idParts[0];
                        }
                        // PID-5: Patient Name (LastName^FirstName)
                        if (fields.Length > 5 && !string.IsNullOrEmpty(fields[5]))
                        {
                            var nameParts = fields[5].Split('^');
                            if (nameParts.Length > 0) lastName = nameParts[0];
                            if (nameParts.Length > 1) firstName = nameParts[1];
                        }
                        // PID-7: DOB (YYYYMMDD)
                        if (fields.Length > 7 && !string.IsNullOrEmpty(fields[7]))
                        {
                            string rawDob = fields[7];
                            if (rawDob.Length >= 8)
                            {
                                dob = $"{rawDob.Substring(0, 4)}-{rawDob.Substring(4, 2)}-{rawDob.Substring(6, 2)}";
                            }
                        }
                        // PID-8: Administrative Sex
                        if (fields.Length > 8 && !string.IsNullOrEmpty(fields[8]))
                        {
                            gender = fields[8].ToUpper() switch
                            {
                                "M" => "male",
                                "F" => "female",
                                _ => "other"
                            };
                        }
                        // PID-19: SSN / Health Card Number (BC PHN)
                        if (fields.Length > 19 && !string.IsNullOrEmpty(fields[19]))
                        {
                            phn = fields[19];
                        }
                        break;

                    case "PV1":
                        // PV1-19: Visit Number
                        if (fields.Length > 19 && !string.IsNullOrEmpty(fields[19]))
                        {
                            visitNumber = fields[19];
                        }
                        break;

                    case "DG1":
                        // DG1-3: Diagnosis Code^Description
                        if (fields.Length > 3 && !string.IsNullOrEmpty(fields[3]))
                        {
                            var diagParts = fields[3].Split('^');
                            admittingDiagnosis = diagParts.Length > 1 ? diagParts[1] : diagParts[0];
                        }
                        break;

                    case "OBX":
                        // OBX-3: Observation Identifier (Code^Text^System)
                        // OBX-5: Observation Value
                        // OBX-6: Units
                        // OBX-8: Abnormal Flags
                        string obsCode = "8867-4";
                        string obsDisplay = "Observation";
                        if (fields.Length > 3 && !string.IsNullOrEmpty(fields[3]))
                        {
                            var codeParts = fields[3].Split('^');
                            obsCode = codeParts[0];
                            if (codeParts.Length > 1) obsDisplay = codeParts[1];
                        }
                        string obsValue = fields.Length > 5 ? fields[5] : "";
                        string obsUnits = fields.Length > 6 ? fields[6] : "";
                        string obsFlag = fields.Length > 8 ? fields[8] : "N";

                        observations.Add(new Hl7ObservationSegment(
                            "OBX-" + (observations.Count + 1),
                            obsCode,
                            obsDisplay,
                            obsValue,
                            obsUnits,
                            obsFlag
                        ));
                        break;
                }
            }

            return new Hl7ParsedMessage(
                messageControlId,
                messageType,
                mrn,
                phn,
                firstName,
                lastName,
                dob,
                gender,
                visitNumber,
                admittingDiagnosis,
                observations
            );
        }
    }
}
