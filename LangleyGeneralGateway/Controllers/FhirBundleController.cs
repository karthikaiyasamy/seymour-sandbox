using System;
using System.Collections.Generic;
using System.Linq;
using System.Text.Json;
using System.Threading.Tasks;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using LangleyGeneralGateway.Data;
using LangleyGeneralGateway.Models;

namespace LangleyGeneralGateway.Controllers
{
    [ApiController]
    [Route("fhir")]
    public class FhirBundleController : ControllerBase
    {
        private readonly LangleyGeneralDbContext _context;

        public FhirBundleController(LangleyGeneralDbContext context)
        {
            _context = context;
        }

        // POST /fhir — FHIR R4 Bundle Transaction / Batch endpoint in C#
        [HttpPost]
        public async Task<IActionResult> ProcessBundle([FromBody] JsonElement body)
        {
            if (!body.TryGetProperty("resourceType", out var resTypeProp) || resTypeProp.GetString() != "Bundle")
            {
                return BadRequest(new
                {
                    resourceType = "OperationOutcome",
                    issue = new[] { new { severity = "error", code = "invalid", diagnostics = "Expected resourceType 'Bundle'." } }
                });
            }

            string bundleType = body.TryGetProperty("type", out var typeProp) ? typeProp.GetString() ?? "transaction" : "transaction";
            var responseEntries = new List<object>();

            if (body.TryGetProperty("entry", out var entriesProp) && entriesProp.ValueKind == JsonValueKind.Array)
            {
                using var transaction = await _context.Database.BeginTransactionAsync();

                try
                {
                    foreach (var entry in entriesProp.EnumerateArray())
                    {
                        if (!entry.TryGetProperty("resource", out var resElem)) continue;

                        string resourceType = resElem.GetProperty("resourceType").GetString() ?? "";

                        if (resourceType == "Patient")
                        {
                            string mrn = "MRN-" + Random.Shared.Next(100000, 999999);
                            string firstName = "Bundle";
                            string lastName = "Patient";
                            string phn = "";

                            if (resElem.TryGetProperty("name", out var nameArr) && nameArr.ValueKind == JsonValueKind.Array && nameArr.GetArrayLength() > 0)
                            {
                                var first = nameArr[0];
                                if (first.TryGetProperty("family", out var fam)) lastName = fam.GetString() ?? lastName;
                                if (first.TryGetProperty("given", out var giv) && giv.ValueKind == JsonValueKind.Array && giv.GetArrayLength() > 0)
                                {
                                    firstName = giv[0].GetString() ?? firstName;
                                }
                            }

                            var patient = new GeneralPatient
                            {
                                Mrn = mrn,
                                Phn = phn,
                                FirstName = firstName,
                                LastName = lastName,
                                DateOfBirth = DateOnly.FromDateTime(DateTime.UtcNow.AddYears(-25)),
                                Gender = "other",
                                SyncedAt = DateTime.UtcNow
                            };

                            _context.Patients.Add(patient);
                            await _context.SaveChangesAsync();

                            responseEntries.Add(new
                            {
                                response = new
                                {
                                    status = "201 Created",
                                    location = $"Patient/{patient.Id}",
                                    outcome = new { resourceType = "Patient", id = patient.Id.ToString() }
                                }
                            });
                        }
                        else if (resourceType == "Observation")
                        {
                            var firstPatient = await _context.Patients.FirstOrDefaultAsync();
                            long pid = firstPatient?.Id ?? 1;

                            var obs = new GeneralObservation
                            {
                                PatientId = pid,
                                Status = "final",
                                Code = "8867-4",
                                CodeDisplay = "Bundle Heart Rate",
                                ValueQuantity = 80.0,
                                ValueUnit = "beats/min",
                                EffectiveDateTime = DateTime.UtcNow
                            };

                            _context.Observations.Add(obs);
                            await _context.SaveChangesAsync();

                            responseEntries.Add(new
                            {
                                response = new
                                {
                                    status = "201 Created",
                                    location = $"Observation/{obs.Id}",
                                    outcome = new { resourceType = "Observation", id = obs.Id.ToString() }
                                }
                            });
                        }
                        else
                        {
                            responseEntries.Add(new
                            {
                                response = new
                                {
                                    status = "200 OK",
                                    location = $"{resourceType}/1"
                                }
                            });
                        }
                    }

                    await transaction.CommitAsync();
                }
                catch (Exception ex)
                {
                    await transaction.RollbackAsync();
                    return BadRequest(new
                    {
                        resourceType = "OperationOutcome",
                        issue = new[] { new { severity = "error", code = "transaction-failed", diagnostics = ex.Message } }
                    });
                }
            }

            return Ok(new
            {
                resourceType = "Bundle",
                type = $"{bundleType}-response",
                timestamp = DateTime.UtcNow.ToString("o"),
                entry = responseEntries
            });
        }
    }
}
