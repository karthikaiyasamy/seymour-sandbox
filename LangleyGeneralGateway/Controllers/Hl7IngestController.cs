using System;
using System.IO;
using System.Text;
using System.Threading.Tasks;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using LangleyGeneralGateway.Data;
using LangleyGeneralGateway.Models;
using LangleyGeneralGateway.Utils;

namespace LangleyGeneralGateway.Controllers
{
    [ApiController]
    [Route("api/langleygeneral/hl7")]
    public class Hl7IngestController : ControllerBase
    {
        private readonly LangleyGeneralDbContext _context;

        public Hl7IngestController(LangleyGeneralDbContext context)
        {
            _context = context;
        }

        // POST /api/langleygeneral/hl7 — Ingest raw pipe-delimited HL7 v2 messages (ADT/ORU) in C#
        [HttpPost]
        [Consumes("text/plain", "application/hl7-v2", "application/json")]
        public async Task<IActionResult> IngestHl7Message()
        {
            string rawHl7;
            using (var reader = new StreamReader(Request.Body, Encoding.UTF8))
            {
                rawHl7 = await reader.ReadToEndAsync();
            }

            if (string.IsNullOrWhiteSpace(rawHl7))
            {
                return BadRequest(new
                {
                    status = "error",
                    message = "HL7 message body cannot be empty."
                });
            }

            try
            {
                var parsed = Hl7Parser.Parse(rawHl7);

                if (string.IsNullOrEmpty(parsed.PatientMrn))
                {
                    return BadRequest(new
                    {
                        status = "error",
                        message = "Missing MRN (PID-3) in HL7 message."
                    });
                }

                // Check for existing patient by MRN
                var patient = await _context.Patients.FirstOrDefaultAsync(p => p.Mrn == parsed.PatientMrn);
                bool isNew = false;

                if (patient == null)
                {
                    isNew = true;
                    patient = new GeneralPatient
                    {
                        Mrn = parsed.PatientMrn,
                        Phn = parsed.PatientPhn,
                        FirstName = !string.IsNullOrEmpty(parsed.FirstName) ? parsed.FirstName : "Unknown",
                        LastName = !string.IsNullOrEmpty(parsed.LastName) ? parsed.LastName : "Patient",
                        Gender = !string.IsNullOrEmpty(parsed.Gender) ? parsed.Gender : "unknown",
                        DateOfBirth = DateOnly.TryParse(parsed.DateOfBirth, out var dob) ? dob : DateOnly.FromDateTime(DateTime.UtcNow.AddYears(-30)),
                        SyncedAt = DateTime.UtcNow
                    };
                    _context.Patients.Add(patient);
                }
                else
                {
                    if (!string.IsNullOrEmpty(parsed.FirstName)) patient.FirstName = parsed.FirstName;
                    if (!string.IsNullOrEmpty(parsed.LastName)) patient.LastName = parsed.LastName;
                    if (!string.IsNullOrEmpty(parsed.PatientPhn)) patient.Phn = parsed.PatientPhn;
                    patient.SyncedAt = DateTime.UtcNow;
                }

                await _context.SaveChangesAsync();

                // Save any OBX observations attached to HL7 message
                int obsSavedCount = 0;
                foreach (var obsSeg in parsed.Observations)
                {
                    double.TryParse(obsSeg.Value, out double numVal);
                    var obsEntity = new GeneralObservation
                    {
                        PatientId = patient.Id,
                        Status = "final",
                        Code = obsSeg.Code,
                        CodeDisplay = obsSeg.Display,
                        ValueQuantity = numVal > 0 ? numVal : null,
                        ValueString = numVal == 0 ? obsSeg.Value : null,
                        ValueUnit = obsSeg.Units,
                        Interpretation = obsSeg.Flag,
                        EffectiveDateTime = DateTime.UtcNow
                    };
                    _context.Observations.Add(obsEntity);
                    obsSavedCount++;
                }

                if (obsSavedCount > 0)
                {
                    await _context.SaveChangesAsync();
                }

                return Ok(new
                {
                    status = "success",
                    messageType = parsed.MessageType,
                    messageControlId = parsed.MessageControlId,
                    patientId = patient.Id,
                    mrn = patient.Mrn,
                    phn = patient.Phn,
                    isNewPatient = isNew,
                    observationsIngested = obsSavedCount,
                    phnValid = PhnValidator.IsValidBCOnlyPHN(patient.Phn)
                });
            }
            catch (Exception ex)
            {
                return StatusCode(500, new
                {
                    status = "error",
                    message = "Failed parsing/ingesting HL7 message: " + ex.Message
                });
            }
        }
    }
}
