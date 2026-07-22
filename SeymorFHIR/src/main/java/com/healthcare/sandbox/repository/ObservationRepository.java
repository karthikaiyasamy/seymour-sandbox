package com.healthcare.sandbox.repository;

import com.healthcare.sandbox.model.Observation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ObservationRepository extends JpaRepository<Observation, Long> {
    List<Observation> findByPatientId(Long patientId);
    List<Observation> findByPatientIdAndCategory(Long patientId, String category);
    List<Observation> findByPatientIdAndCode(Long patientId, String code);
}
