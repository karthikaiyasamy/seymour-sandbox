using Microsoft.EntityFrameworkCore;
using LangleyGeneralGateway.Models;

namespace LangleyGeneralGateway.Data
{
    public class LangleyGeneralDbContext : DbContext
    {
        public LangleyGeneralDbContext(DbContextOptions<LangleyGeneralDbContext> options)
            : base(options)
        {
        }

        public DbSet<GeneralPatient> Patients { get; set; }
        public DbSet<GeneralObservation> Observations { get; set; }
        public DbSet<GeneralAllergy> Allergies { get; set; }

        protected override void OnModelCreating(ModelBuilder modelBuilder)
        {
            base.OnModelCreating(modelBuilder);

            // Add index or constraints on MRN
            modelBuilder.Entity<GeneralPatient>()
                .HasIndex(p => p.Mrn)
                .IsUnique();
        }
    }
}
