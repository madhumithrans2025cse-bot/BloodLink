package com.example.BloodLink_api.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "bloodlink")
public class BloodLinkModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "blood_group", nullable = false)
    private String bloodGroup;

    @Column(name = "city", nullable = false)
    private String city;

    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @Column(name = "email")
    private String email;

    @Column(name = "age")
    private Integer age;

    @Column(name = "gender")
    private String gender;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Column(name = "last_donation_date")
    private LocalDate lastDonationDate;

    @Column(name = "available")
    private Boolean available = true;

    public BloodLinkModel() {
        this.available = true;
    }

    public BloodLinkModel(Long id, String name, String bloodGroup, String city, String phoneNumber, String email, Integer age, String gender, LocalDate lastDonationDate, Boolean available) {
        this.id = id;
        this.name = name;
        this.bloodGroup = bloodGroup;
        this.city = city;
        this.phoneNumber = phoneNumber;
        this.email = email;
        this.age = age;
        this.gender = gender;
        this.lastDonationDate = lastDonationDate;
        this.available = (available != null) ? available : true;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBloodGroup() {
        return bloodGroup;
    }

    public void setBloodGroup(String bloodGroup) {
        this.bloodGroup = bloodGroup;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    // Alias for location to maintain backward compatibility
    public String getLocation() {
        return city;
    }

    public void setLocation(String location) {
        this.city = location;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public LocalDate getLastDonationDate() {
        return lastDonationDate;
    }

    public void setLastDonationDate(LocalDate lastDonationDate) {
        this.lastDonationDate = lastDonationDate;
    }

    // Dynamic evaluation: unavailable if within 90 days from last donation
    public Boolean isAvailable() {
        if (lastDonationDate != null) {
            long daysPassed = ChronoUnit.DAYS.between(lastDonationDate, LocalDate.now());
            if (daysPassed < 90) {
                return false;
            }
        }
        return available != null ? available : true;
    }

    public Boolean getAvailable() {
        return isAvailable();
    }

    public void setAvailable(Boolean available) {
        this.available = (available != null) ? available : true;
    }
}
