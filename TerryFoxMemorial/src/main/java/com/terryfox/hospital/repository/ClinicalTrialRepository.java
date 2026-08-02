package com.terryfox.hospital.repository;

import com.terryfox.hospital.model.ClinicalTrialEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClinicalTrialRepository extends JpaRepository<ClinicalTrialEntity, Long> {
    List<ClinicalTrialEntity> findByPatientId(Long patientId);
    List<ClinicalTrialEntity> findByPatientPhn(String phn);
    List<ClinicalTrialEntity> findByNctId(String nctId);
}
