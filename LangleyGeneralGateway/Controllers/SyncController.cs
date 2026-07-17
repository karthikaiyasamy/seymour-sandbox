using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using LangleyGeneralGateway.Data;
using LangleyGeneralGateway.Models;
using System;
using System.Collections.Generic;
using System.Threading.Tasks;

namespace LangleyGeneralGateway.Controllers
{
    [ApiController]
    [Route("api/langleygeneral")]
    public class SyncController : ControllerBase
    {
        private readonly LangleyGeneralDbContext _context;

        public SyncController(LangleyGeneralDbContext context)
        {
            _context = context;
        }

        // POST api/langleygeneral/sync
        [HttpPost("sync")]
        public async Task<IActionResult> SyncPatient([FromBody] SyncPatientRequest request)
        {
            if (request == null || string.IsNullOrWhiteSpace(request.Mrn) || string.IsNullOrWhiteSpace(request.FirstName) || string.IsNullOrWhiteSpace(request.LastName))
            {
                return BadRequest(new { status = "error", message = "Missing mandatory fields (mrn, firstName, lastName)" });
            }

            try
            {
                var existingPatient = await _context.Patients
                    .FirstOrDefaultAsync(p => p.Mrn == request.Mrn);

                if (existingPatient != null)
                {
                    // Update demographics
                    existingPatient.FirstName = request.FirstName;
                    existingPatient.LastName = request.LastName;
                    existingPatient.Phn = request.Phn;
                    existingPatient.Gender = request.Gender;
                    existingPatient.DateOfBirth = request.DateOfBirth;
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
                        Phn = request.Phn,
                        FirstName = request.FirstName,
                        LastName = request.LastName,
                        DateOfBirth = request.DateOfBirth,
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
        public string Mrn { get; set; } = string.Empty;
        public string? Phn { get; set; }
        public string FirstName { get; set; } = string.Empty;
        public string LastName { get; set; } = string.Empty;
        public DateOnly? DateOfBirth { get; set; }
        public string? Gender { get; set; }
    }
}
