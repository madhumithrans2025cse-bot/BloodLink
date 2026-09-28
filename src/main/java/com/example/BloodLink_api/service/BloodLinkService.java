package com.example.BloodLink_api.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.example.BloodLink_api.model.BloodLinkModel;

public interface BloodLinkService {

    // 1. Register donor with blood group, city, and last donation date
    BloodLinkModel registerDonor(BloodLinkModel donor);
    BloodLinkModel createBloodLink(BloodLinkModel donor);

    // CRUD
    List<BloodLinkModel> getAllDonors();
    Optional<BloodLinkModel> getDonorById(Long id);
    BloodLinkModel updateDonor(Long id, BloodLinkModel updatedDonor);
    boolean deleteDonor(Long id);

    // 2. Search donors by blood group and city (with optional filter for available only)
    List<BloodLinkModel> searchDonors(String bloodGroup, String city, Boolean onlyAvailable);

    // 3. Mark a donor unavailable for cooldown (minimum 90 days) after donation
    BloodLinkModel markDonated(Long donorId, LocalDate donationDate);

    // 4. Auto re-enable donor availability after cooldown passes
    void autoReEnableDonors();

    // 5. View total donors registered per blood group
    Map<String, Long> getDonorCountPerBloodGroup();
}
