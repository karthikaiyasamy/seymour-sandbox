using System;
using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace LangleyGeneralGateway.Models
{
    [Table("general_allergies")]
    public class GeneralAllergy
    {
        [Key]
        [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
        public long Id { get; set; }

        [Required]
        public long PatientId { get; set; }

        [ForeignKey("PatientId")]
        public GeneralPatient? Patient { get; set; }

        [MaxLength(50)]
        public string? Code { get; set; }

        [Required]
        [MaxLength(200)]
        public string Display { get; set; } = "Unspecified Allergy";

        [MaxLength(30)]
        public string Category { get; set; } = "medication";

        [MaxLength(30)]
        public string Criticality { get; set; } = "low";

        [MaxLength(200)]
        public string? Reaction { get; set; }

        public DateTime SyncedAt { get; set; } = DateTime.UtcNow;
    }
}
