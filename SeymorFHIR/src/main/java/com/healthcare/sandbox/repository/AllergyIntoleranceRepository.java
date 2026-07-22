package com.healthcare.sandbox.repository;

import com.healthcare.sandbox.model.AllergyIntolerance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AllergyIntoleranceRepository extends JpaRepository<AllergyIntolerance, Long> {
    List<AllergyIntolerance> findByPatientId(Long patientId);
    List<AllergyIntolerance> findByPatientIdAndClinicalStatus(Long patientId, String clinicalStatus);
}
