package com.terryfox.hospital.repository;

import com.terryfox.hospital.model.GenomicReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GenomicReportRepository extends JpaRepository<GenomicReportEntity, Long> {
    List<GenomicReportEntity> findByPatientId(Long patientId);
    List<GenomicReportEntity> findByPatientPhn(String phn);
}
