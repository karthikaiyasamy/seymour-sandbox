using System;
using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace LangleyGeneralGateway.Models
{
    [Table("general_patients")]
    public class GeneralPatient
    {
        [Key]
        [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
        public int Id { get; set; }

        [Required]
        [MaxLength(50)]
        public string Mrn { get; set; } = string.Empty;

        [MaxLength(50)]
        public string? Phn { get; set; }

        [Required]
        [MaxLength(100)]
        public string FirstName { get; set; } = string.Empty;

        [Required]
        [MaxLength(100)]
        public string LastName { get; set; } = string.Empty;

        public DateOnly? DateOfBirth { get; set; }

        [MaxLength(20)]
        public string? Gender { get; set; }

        public DateTime SyncedAt { get; set; } = DateTime.UtcNow;
    }
}
