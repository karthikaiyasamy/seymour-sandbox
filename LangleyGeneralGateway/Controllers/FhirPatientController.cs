using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using LangleyGeneralGateway.Data;
using LangleyGeneralGateway.Models;
using LangleyGeneralGateway.Utils;

namespace LangleyGeneralGateway.Controllers
{
    [ApiController]
    [Route("fhir/[controller]")]
    public class PatientController : ControllerBase
    {
        private readonly LangleyGeneralDbContext _context;

        public PatientController(LangleyGeneralDbContext context)
        {
            _context = context;
        }

        // GET /fhir/Patient — search or list patients
        [HttpGet]
        public async Task<IActionResult> GetPatients([FromQuery(Name = "_id")] string? idParam, [FromQuery] string? identifier, [FromQuery] string? name)
        {
            var query = _context.Patients.AsQueryable();

            if (!string.IsNullOrEmpty(idParam))
            {
                if (long.TryParse(idParam, out long numericId))
                {
                    query = query.Where(p => p.Id == numericId);
                }
                else
                {
                    query = query.Where(p => p.Phn == idParam || p.Mrn == idParam);
                }
            }

            if (!string.IsNullOrEmpty(name))
            {
                string term = name.ToLower();
                query = query.Where(p => p.FirstName.ToLower().Contains(term) || p.LastName.ToLower().Contains(term));
            }

            if (!string.IsNullOrEmpty(identifier))
            {
                string val = identifier.Contains('|') ? identifier.Split('|')[1] : identifier;
                query = query.Where(p => p.Mrn == val || p.Phn == val);
            }

            var patients = await query.ToListAsync();
            var entries = patients.Select(p => new { resource = ToFhirPatient(p) }).ToList();

            return Ok(new
            {
                resourceType = "Bundle",
                type = "searchset",
                total = entries.Count,
                timestamp = DateTime.UtcNow.ToString("o"),
                entry = entries
            });
        }

        // GET /fhir/Patient/{id}
        [HttpGet("{id}")]
        public async Task<IActionResult> GetPatientById(string id)
        {
            GeneralPatient? p = null;

            if (long.TryParse(id, out long numericId))
            {
                p = await _context.Patients.FindAsync(numericId);
            }

            if (p == null)
            {
                p = await _context.Patients.FirstOrDefaultAsync(x => x.Mrn == id || x.Phn == id);
            }

            if (p == null)
            {
                return NotFound(new
                {
                    resourceType = "OperationOutcome",
                    issue = new[]
                    {
                        new { severity = "error", code = "not-found", diagnostics = $"Patient {id} not found in Langley General Gateway." }
                    }
                });
            }

            return Ok(ToFhirPatient(p));
        }

        // POST /fhir/Patient
        [HttpPost]
        public async Task<IActionResult> CreatePatient([FromBody] Dictionary<string, object> payload)
        {
            string mrn = "MRN-" + Random.Shared.Next(100000, 999999);
            string phn = "";
            string firstName = "Unknown";
            string lastName = "Patient";
            string gender = "unknown";
            DateOnly dob = DateOnly.FromDateTime(DateTime.UtcNow.AddYears(-30));

            if (payload.TryGetValue("name", out object? nameObj) && nameObj is System.Text.Json.JsonElement nameElem && nameElem.ValueKind == System.Text.Json.JsonValueKind.Array && nameElem.GetArrayLength() > 0)
            {
                var first = nameElem[0];
                if (first.TryGetProperty("family", out var fam)) lastName = fam.GetString() ?? lastName;
                if (first.TryGetProperty("given", out var giv) && giv.ValueKind == System.Text.Json.JsonValueKind.Array && giv.GetArrayLength() > 0)
                {
                    firstName = giv[0].GetString() ?? firstName;
                }
            }

            if (payload.TryGetValue("gender", out object? genObj) && genObj != null)
            {
                gender = genObj.ToString() ?? "unknown";
            }

            if (payload.TryGetValue("birthDate", out object? dobObj) && dobObj != null && DateOnly.TryParse(dobObj.ToString(), out DateOnly parsedDob))
            {
                dob = parsedDob;
            }

            var patient = new GeneralPatient
            {
                Mrn = mrn,
                Phn = phn,
                FirstName = firstName,
                LastName = lastName,
                DateOfBirth = dob,
                Gender = gender,
                SyncedAt = DateTime.UtcNow
            };

            _context.Patients.Add(patient);
            await _context.SaveChangesAsync();

            return Created($"/fhir/Patient/{patient.Id}", ToFhirPatient(patient));
        }

        private object ToFhirPatient(GeneralPatient p)
        {
            return new
            {
                resourceType = "Patient",
                id = p.Id.ToString(),
                active = true,
                identifier = new object[]
                {
                    new
                    {
                        use = "official",
                        type = new { coding = new[] { new { system = "http://terminology.hl7.org/CodeSystem/v2-0203", code = "MR", display = "Medical Record Number" } } },
                        system = "urn:oid:2.16.840.1.113883.3.18.1.1",
                        value = p.Mrn
                    },
                    new
                    {
                        use = "official",
                        type = new { coding = new[] { new { system = "http://terminology.hl7.org/CodeSystem/v2-0203", code = "JHN", display = "Jurisdictional Health Number" } } },
                        system = "http://sharedhealth.exchange/fhir/NamingSystem/ca-bc-patient-phn",
                        value = p.Phn
                    }
                },
                name = new[]
                {
                    new
                    {
                        use = "official",
                        family = p.LastName,
                        given = new[] { p.FirstName }
                    }
                },
                gender = p.Gender ?? "unknown",
                birthDate = p.DateOfBirth?.ToString("yyyy-MM-dd")
            };
        }
    }
}
