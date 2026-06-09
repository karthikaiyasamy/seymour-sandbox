package com.langley.hospital.repository;

import com.langley.hospital.model.LangleyPatient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LangleyPatientRepository extends JpaRepository<LangleyPatient, Long> {
    Optional<LangleyPatient> findBySeymourPatientId(String seymourPatientId);
    Optional<LangleyPatient> findByMrn(String mrn);
}
