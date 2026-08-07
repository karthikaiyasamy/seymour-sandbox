package com.healthcare.sandbox.repository;

import com.healthcare.sandbox.model.PatientMatchReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PatientMatchReviewRepository extends JpaRepository<PatientMatchReview, Long> {
    List<PatientMatchReview> findByStatus(String status);
}
