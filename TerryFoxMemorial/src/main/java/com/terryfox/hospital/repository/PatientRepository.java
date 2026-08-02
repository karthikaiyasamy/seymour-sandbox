package com.terryfox.hospital.repository;

import com.terryfox.hospital.model.PatientEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PatientRepository extends JpaRepository<PatientEntity, Long> {
    Optional<PatientEntity> findByPhn(String phn);
    Optional<PatientEntity> findByMrn(String mrn);
    List<PatientEntity> findByFamilyNameIgnoreCase(String familyName);
}
