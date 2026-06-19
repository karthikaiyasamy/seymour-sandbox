package com.langley.hospital.repository;

import com.langley.hospital.model.LangleyLabResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LangleyLabResultRepository extends JpaRepository<LangleyLabResult, Long> {
    List<LangleyLabResult> findByPatientId(Long patientId);
}
