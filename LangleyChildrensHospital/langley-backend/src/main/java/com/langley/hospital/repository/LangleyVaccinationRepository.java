package com.langley.hospital.repository;

import com.langley.hospital.model.LangleyVaccination;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LangleyVaccinationRepository extends JpaRepository<LangleyVaccination, Long> {
    List<LangleyVaccination> findByPatientId(Long patientId);
}
