package com.terryfox.hospital.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "terryfox_patients")
public class PatientEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String phn;

    @Column(nullable = false)
    private String mrn;

    @Column(nullable = false)
    private String givenName;

    @Column(nullable = false)
    private String familyName;

    private LocalDate birthDate;
    private String gender;
    private String addressLine;
    private String city;
    private String state;
    private String postalCode;
    private String phone;
    private String primaryOncologist;

    public PatientEntity() {}

    public PatientEntity(Long id, String phn, String mrn, String givenName, String familyName, LocalDate birthDate, String gender, String addressLine, String city, String state, String postalCode, String phone, String primaryOncologist) {
        this.id = id;
        this.phn = phn;
        this.mrn = mrn;
        this.givenName = givenName;
        this.familyName = familyName;
        this.birthDate = birthDate;
        this.gender = gender;
        this.addressLine = addressLine;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
        this.phone = phone;
        this.primaryOncologist = primaryOncologist;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPhn() { return phn; }
    public void setPhn(String phn) { this.phn = phn; }
    public String getMrn() { return mrn; }
    public void setMrn(String mrn) { this.mrn = mrn; }
    public String getGivenName() { return givenName; }
    public void setGivenName(String givenName) { this.givenName = givenName; }
    public String getFamilyName() { return familyName; }
    public void setFamilyName(String familyName) { this.familyName = familyName; }
    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getAddressLine() { return addressLine; }
    public void setAddressLine(String addressLine) { this.addressLine = addressLine; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPrimaryOncologist() { return primaryOncologist; }
    public void setPrimaryOncologist(String primaryOncologist) { this.primaryOncologist = primaryOncologist; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long id;
        private String phn;
        private String mrn;
        private String givenName;
        private String familyName;
        private LocalDate birthDate;
        private String gender;
        private String addressLine;
        private String city;
        private String state;
        private String postalCode;
        private String phone;
        private String primaryOncologist;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder phn(String phn) { this.phn = phn; return this; }
        public Builder mrn(String mrn) { this.mrn = mrn; return this; }
        public Builder givenName(String givenName) { this.givenName = givenName; return this; }
        public Builder familyName(String familyName) { this.familyName = familyName; return this; }
        public Builder birthDate(LocalDate birthDate) { this.birthDate = birthDate; return this; }
        public Builder gender(String gender) { this.gender = gender; return this; }
        public Builder addressLine(String addressLine) { this.addressLine = addressLine; return this; }
        public Builder city(String city) { this.city = city; return this; }
        public Builder state(String state) { this.state = state; return this; }
        public Builder postalCode(String postalCode) { this.postalCode = postalCode; return this; }
        public Builder phone(String phone) { this.phone = phone; return this; }
        public Builder primaryOncologist(String primaryOncologist) { this.primaryOncologist = primaryOncologist; return this; }

        public PatientEntity build() {
            return new PatientEntity(id, phn, mrn, givenName, familyName, birthDate, gender, addressLine, city, state, postalCode, phone, primaryOncologist);
        }
    }
}
