package com.healthcare.sandbox.repository;

import com.healthcare.sandbox.model.Encounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EncounterRepository extends JpaRepository<Encounter, Long> {

    List<Encounter> findByPatientIdOrderByEncounterDatetimeDesc(Long patientId);

    List<Encounter> findByVisitNumberOrderByEncounterDatetimeAsc(String visitNumber);

    List<Encounter> findByPatientIdAndEncounterType(Long patientId, String encounterType);
}
