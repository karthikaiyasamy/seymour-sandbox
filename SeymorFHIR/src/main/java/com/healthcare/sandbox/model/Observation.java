package com.healthcare.sandbox.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "observations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Observation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "status", nullable = false)
    private String status; // registered | preliminary | final | amended

    @Column(name = "category")
    private String category; // vital-signs | laboratory | survey | imaging

    @Column(name = "code", nullable = false)
    private String code; // LOINC code, e.g. "4548-4" (HbA1c), "8867-4" (Heart rate), "2339-0" (Glucose)

    @Column(name = "code_system")
    private String codeSystem; // http://loinc.org

    @Column(name = "code_display", nullable = false)
    private String codeDisplay; // e.g. "Hemoglobin A1c/Hemoglobin.total in Blood", "Heart rate"

    @Column(name = "value_quantity")
    private Double valueQuantity;

    @Column(name = "value_unit")
    private String valueUnit; // e.g. "%", "beats/min", "mmol/L", "mg/dL", "degC"

    @Column(name = "value_string")
    private String valueString; // Alternative for textual lab interpretations

    @Column(name = "interpretation")
    private String interpretation; // N (Normal), H (High), L (Low), HH (Critically High)

    @Column(name = "effective_date_time")
    private LocalDateTime effectiveDateTime;

    @Column(name = "issued")
    private LocalDateTime issued;

    @PrePersist
    public void prePersist() {
        if (this.effectiveDateTime == null) {
            this.effectiveDateTime = LocalDateTime.now();
        }
        if (this.issued == null) {
            this.issued = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = "final";
        }
        if (this.codeSystem == null) {
            this.codeSystem = "http://loinc.org";
        }
    }
}
