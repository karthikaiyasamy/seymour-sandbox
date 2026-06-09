package com.healthcare.sandbox.repository;

import com.healthcare.sandbox.model.AdtEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdtEventRepository extends JpaRepository<AdtEvent, Long> {

    List<AdtEvent> findByPatientIdOrderByEventDatetimeDesc(Long patientId);

    List<AdtEvent> findByVisitNumberOrderByEventDatetimeAsc(String visitNumber);

    Optional<AdtEvent> findTopByPatientIdOrderByEventDatetimeDesc(Long patientId);

    List<AdtEvent> findByEventTypeOrderByEventDatetimeDesc(String eventType);

    List<AdtEvent> findByFacilityOrderByEventDatetimeDesc(String facility);
}
