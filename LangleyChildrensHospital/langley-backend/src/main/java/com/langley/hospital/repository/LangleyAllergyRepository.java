package com.langley.hospital.repository;

import com.langley.hospital.model.LangleyAllergy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LangleyAllergyRepository extends JpaRepository<LangleyAllergy, Long> {
    List<LangleyAllergy> findByPatientId(Long patientId);
}
