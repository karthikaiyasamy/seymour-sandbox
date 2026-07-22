using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using LangleyGeneralGateway.Data;
using LangleyGeneralGateway.Models;

namespace LangleyGeneralGateway.Controllers
{
    [ApiController]
    [Route("fhir/Observation")]
    public class FhirObservationController : ControllerBase
    {
        private readonly LangleyGeneralDbContext _context;

        public FhirObservationController(LangleyGeneralDbContext context)
        {
            _context = context;
        }

        // GET /fhir/Observation
        [HttpGet]
        public async Task<IActionResult> GetObservations()
        {
            var list = await _context.Observations.Include(o => o.Patient).ToListAsync();
            var entries = list.Select(o => new { resource = ToFhirObservation(o) }).ToList();

            return Ok(new
            {
                resourceType = "Bundle",
                type = "searchset",
                total = entries.Count,
                timestamp = DateTime.UtcNow.ToString("o"),
                entry = entries
            });
        }

        // POST /fhir/Observation
        [HttpPost]
        public async Task<IActionResult> CreateObservation([FromBody] Dictionary<string, object> payload)
        {
            var patient = await _context.Patients.FirstOrDefaultAsync();
            if (patient == null)
            {
                return BadRequest(new
                {
                    resourceType = "OperationOutcome",
                    issue = new[] { new { severity = "error", code = "invalid", diagnostics = "No registered patients available to link observation." } }
                });
            }

            string code = "8867-4";
            string display = "Heart Rate";
            double? valueQty = 75.0;
            string unit = "beats/min";

            if (payload.TryGetValue("code", out object? codeObj) && codeObj is System.Text.Json.JsonElement codeElem)
            {
                if (codeElem.TryGetProperty("text", out var txt)) display = txt.GetString() ?? display;
                if (codeElem.TryGetProperty("coding", out var codings) && codings.ValueKind == System.Text.Json.JsonValueKind.Array && codings.GetArrayLength() > 0)
                {
                    var firstCoding = codings[0];
                    if (firstCoding.TryGetProperty("code", out var c)) code = c.GetString() ?? code;
                    if (firstCoding.TryGetProperty("display", out var d)) display = d.GetString() ?? display;
                }
            }

            if (payload.TryGetValue("valueQuantity", out object? valObj) && valObj is System.Text.Json.JsonElement valElem)
            {
                if (valElem.TryGetProperty("value", out var v) && v.TryGetDouble(out double dVal)) valueQty = dVal;
                if (valElem.TryGetProperty("unit", out var u)) unit = u.GetString() ?? unit;
            }

            var obs = new GeneralObservation
            {
                PatientId = patient.Id,
                Status = "final",
                Code = code,
                CodeDisplay = display,
                ValueQuantity = valueQty,
                ValueUnit = unit,
                EffectiveDateTime = DateTime.UtcNow
            };

            _context.Observations.Add(obs);
            await _context.SaveChangesAsync();

            return Created($"/fhir/Observation/{obs.Id}", ToFhirObservation(obs));
        }

        private object ToFhirObservation(GeneralObservation o)
        {
            return new
            {
                resourceType = "Observation",
                id = o.Id.ToString(),
                status = o.Status,
                code = new
                {
                    coding = new[]
                    {
                        new { system = "http://loinc.org", code = o.Code, display = o.CodeDisplay }
                    },
                    text = o.CodeDisplay
                },
                subject = new
                {
                    reference = $"Patient/{o.PatientId}",
                    display = o.Patient != null ? $"{o.Patient.FirstName} {o.Patient.LastName}" : $"Patient/{o.PatientId}"
                },
                valueQuantity = o.ValueQuantity.HasValue ? new
                {
                    value = o.ValueQuantity.Value,
                    unit = o.ValueUnit ?? "",
                    system = "http://unitsofmeasure.org",
                    code = o.ValueUnit ?? ""
                } : null,
                effectiveDateTime = o.EffectiveDateTime.ToString("o")
            };
        }
    }
}
