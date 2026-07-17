using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using LangleyGeneralGateway.Data;
using LangleyGeneralGateway.Models;
using LangleyGeneralGateway.Utils;
using Microsoft.Extensions.Logging;
using System;
using System.Collections.Generic;
using System.Text.RegularExpressions;
using System.Threading.Tasks;

namespace LangleyGeneralGateway.Controllers
{
    [ApiController]
    [Route("api/langleygeneral")]
    public class SyncController : ControllerBase
    {
        private readonly LangleyGeneralDbContext _context;
        private readonly ILogger<SyncController> _logger;

        public SyncController(LangleyGeneralDbContext context, ILogger<SyncController> logger)
        {
            _context = context;
            _logger = logger;
        }

        // POST api/langleygeneral/sync
        [HttpPost("sync")]
        public async Task<IActionResult> SyncPatient([FromBody] SyncPatientRequest request)
        {
            if (request == null || string.IsNullOrWhiteSpace(request.Mrn) || string.IsNullOrWhiteSpace(request.FirstName) || string.IsNullOrWhiteSpace(request.LastName))
            {
                return BadRequest(new { status = "error", message = "Missing mandatory fields (mrn, firstName, lastName)" });
            }

            // Normalize and Validate PHN if provided
            string? normalizedPhn = null;
            if (!string.IsNullOrWhiteSpace(request.Phn))
            {
                normalizedPhn = Regex.Replace(request.Phn, @"[^0-9]", "");
                if (!PhnValidator.IsValidBCOnlyPHN(normalizedPhn))
                {
                    _logger.LogWarning("Demographics sync rejected: Invalid PHN format/checksum '{Phn}'", PhnValidator.MaskPHN(normalizedPhn));
                    return BadRequest(new { status = "error", message = "Invalid British Columbia PHN format or checksum." });
                }
            }

            // Parse DateOfBirth safely
            DateOnly? parsedDob = null;
            if (!string.IsNullOrWhiteSpace(request.DateOfBirth))
            {
                if (DateOnly.TryParse(request.DateOfBirth, out var dob))
                {
                    parsedDob = dob;
                }
                else
                {
                    return BadRequest(new { status = "error", message = $"Invalid dateOfBirth format: '{request.DateOfBirth}'. Expected YYYY-MM-DD." });
                }
            }

            _logger.LogInformation("Processing demographics sync for MRN: {Mrn}, PHN: {Phn}", 
                request.Mrn, 
                PhnValidator.MaskPHN(normalizedPhn));

            try
            {
                var existingPatient = await _context.Patients
                    .FirstOrDefaultAsync(p => p.Mrn == request.Mrn);

                if (existingPatient != null)
                {
                    // Update demographics
                    existingPatient.FirstName = request.FirstName;
                    existingPatient.LastName = request.LastName;
                    existingPatient.Phn = normalizedPhn;
                    existingPatient.Gender = request.Gender;
                    existingPatient.DateOfBirth = parsedDob;
                    existingPatient.SyncedAt = DateTime.UtcNow;

                    _context.Patients.Update(existingPatient);
                    await _context.SaveChangesAsync();

                    return Ok(new { status = "success", message = $"Patient {request.Mrn} updated successfully." });
                }
                else
                {
                    // Create new patient
                    var newPatient = new GeneralPatient
                    {
                        Mrn = request.Mrn,
                        Phn = normalizedPhn,
                        FirstName = request.FirstName,
                        LastName = request.LastName,
                        DateOfBirth = parsedDob,
                        Gender = request.Gender,
                        SyncedAt = DateTime.UtcNow
                    };

                    await _context.Patients.AddAsync(newPatient);
                    await _context.SaveChangesAsync();

                    return CreatedAtAction(nameof(GetPatients), new { status = "success", message = $"Patient {request.Mrn} created successfully." });
                }
            }
            catch (Exception ex)
            {
                return StatusCode(500, new { status = "error", message = $"Database write failed: {ex.Message}" });
            }
        }

        // GET api/langleygeneral/patients
        [HttpGet("patients")]
        public async Task<ActionResult<IEnumerable<GeneralPatient>>> GetPatients()
        {
            var patients = await _context.Patients.ToListAsync();
            return Ok(patients);
        }
    }

    public class SyncPatientRequest
    {
        public string? Mrn { get; set; }
        public string? Phn { get; set; }
        public string? FirstName { get; set; }
        public string? LastName { get; set; }
        public string? DateOfBirth { get; set; }
        public string? Gender { get; set; }
    }
}
