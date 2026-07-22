using System;
using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace LangleyGeneralGateway.Models
{
    [Table("general_observations")]
    public class GeneralObservation
    {
        [Key]
        [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
        public long Id { get; set; }

        [Required]
        public long PatientId { get; set; }

        [ForeignKey("PatientId")]
        public GeneralPatient? Patient { get; set; }

        [Required]
        [MaxLength(20)]
        public string Status { get; set; } = "final";

        [MaxLength(50)]
        public string Code { get; set; } = "8867-4";

        [Required]
        [MaxLength(200)]
        public string CodeDisplay { get; set; } = "Heart Rate";

        public double? ValueQuantity { get; set; }

        [MaxLength(30)]
        public string? ValueUnit { get; set; }

        [MaxLength(200)]
        public string? ValueString { get; set; }

        [MaxLength(10)]
        public string? Interpretation { get; set; }

        public DateTime EffectiveDateTime { get; set; } = DateTime.UtcNow;
    }
}
