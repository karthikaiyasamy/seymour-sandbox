package com.terryfox.hospital.repository;

import com.terryfox.hospital.model.OncologyConditionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OncologyConditionRepository extends JpaRepository<OncologyConditionEntity, Long> {
    List<OncologyConditionEntity> findByPatientId(Long patientId);
    List<OncologyConditionEntity> findByPatientPhn(String phn);
    List<OncologyConditionEntity> findByClinicalStatus(String clinicalStatus);
}
