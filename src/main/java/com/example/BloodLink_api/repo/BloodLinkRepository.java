package com.example.BloodLink_api.repo;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.example.BloodLink_api.model.BloodLinkModel;

@Repository
public interface BloodLinkRepository extends JpaRepository<BloodLinkModel, Long> {

    // Search by blood group and city (case-insensitive)
    List<BloodLinkModel> findByBloodGroupIgnoreCaseAndCityIgnoreCase(String bloodGroup, String city);

    // Search available donors by blood group and city
    List<BloodLinkModel> findByBloodGroupIgnoreCaseAndCityIgnoreCaseAndAvailableTrue(String bloodGroup, String city);

    // Search by blood group only
    List<BloodLinkModel> findByBloodGroupIgnoreCase(String bloodGroup);

    // Search by city only
    List<BloodLinkModel> findByCityIgnoreCase(String city);

    // Find unavailable donors whose cooldown has elapsed (for auto re-enable)
    List<BloodLinkModel> findByAvailableFalseAndLastDonationDateLessThanEqual(LocalDate cutoffDate);

    // View total donors registered per blood group
    @Query("SELECT b.bloodGroup, COUNT(b) FROM BloodLinkModel b GROUP BY b.bloodGroup")
    List<Object[]> countDonorsPerBloodGroup();
}
