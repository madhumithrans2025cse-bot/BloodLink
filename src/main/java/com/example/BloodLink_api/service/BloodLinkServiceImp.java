package com.example.BloodLink_api.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
 
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.BloodLink_api.model.BloodLinkModel;
import com.example.BloodLink_api.repo.BloodLinkRepository;

@Service
public class BloodLinkServiceImp implements BloodLinkService {

    public static final int COOLDOWN_DAYS = 90;

    private final BloodLinkRepository bloodLinkRepo;

    public BloodLinkServiceImp(BloodLinkRepository bloodLinkRepo) {
        this.bloodLinkRepo = bloodLinkRepo;
    }

    // Helper to evaluate and sync availability based on the 90-day cooldown
    private void refreshDonorAvailability(BloodLinkModel donor) {
        if (donor.getLastDonationDate() != null) {
            long daysPassed = ChronoUnit.DAYS.between(donor.getLastDonationDate(), LocalDate.now());
            if (daysPassed >= COOLDOWN_DAYS) {
                if (!donor.isAvailable()) {
                    donor.setAvailable(true);
                    bloodLinkRepo.save(donor);
                }
            } else {
                if (donor.isAvailable()) {
                    donor.setAvailable(false);
                    bloodLinkRepo.save(donor);
                }
            }
        }
    }

    // 1. REGISTER DONOR (with blood group, city, and last donation date)
    @Override
    public BloodLinkModel registerDonor(BloodLinkModel donor) {
        if (donor.getLastDonationDate() != null) {
            long daysPassed = ChronoUnit.DAYS.between(donor.getLastDonationDate(), LocalDate.now());
            donor.setAvailable(daysPassed >= COOLDOWN_DAYS);
        } else {
            donor.setAvailable(true);
        }
        return bloodLinkRepo.save(donor);
    }

    @Override
    public BloodLinkModel createBloodLink(BloodLinkModel donor) {
        return registerDonor(donor);
    }

    // READ ALL DONORS
    @Override
    public List<BloodLinkModel> getAllDonors() {
        List<BloodLinkModel> list = bloodLinkRepo.findAll();
        list.forEach(this::refreshDonorAvailability);
        return list;
    }

    // READ BY ID
    @Override
    public Optional<BloodLinkModel> getDonorById(Long id) {
        Optional<BloodLinkModel> donorOpt = bloodLinkRepo.findById(id);
        donorOpt.ifPresent(this::refreshDonorAvailability);
        return donorOpt;
    }

    // UPDATE DONOR
    @Override
    public BloodLinkModel updateDonor(Long id, BloodLinkModel updatedDonor) {
        return bloodLinkRepo.findById(id).map(existingDonor -> {
            existingDonor.setName(updatedDonor.getName());
            existingDonor.setBloodGroup(updatedDonor.getBloodGroup());
            existingDonor.setCity(updatedDonor.getCity());
            existingDonor.setPhoneNumber(updatedDonor.getPhoneNumber());
            existingDonor.setEmail(updatedDonor.getEmail());
            existingDonor.setAge(updatedDonor.getAge());
            existingDonor.setGender(updatedDonor.getGender());
            existingDonor.setLastDonationDate(updatedDonor.getLastDonationDate());

            if (updatedDonor.getLastDonationDate() != null) {
                long days = ChronoUnit.DAYS.between(updatedDonor.getLastDonationDate(), LocalDate.now());
                existingDonor.setAvailable(days >= COOLDOWN_DAYS);
            } else {
                existingDonor.setAvailable(updatedDonor.isAvailable());
            }

            return bloodLinkRepo.save(existingDonor);
        }).orElse(null);
    }

    // DELETE DONOR
    @Override
    public boolean deleteDonor(Long id) {
        if (bloodLinkRepo.existsById(id)) {
            bloodLinkRepo.deleteById(id);
            return true;
        }
        return false;
    }

    // 2. SEARCH DONORS BY BLOOD GROUP AND CITY
    @Override
    public List<BloodLinkModel> searchDonors(String bloodGroup, String city, Boolean onlyAvailable) {
        autoReEnableDonors();

        boolean hasGroup = bloodGroup != null && !bloodGroup.trim().isEmpty();
        boolean hasCity = city != null && !city.trim().isEmpty();
        boolean filterAvailable = onlyAvailable != null && onlyAvailable;

        List<BloodLinkModel> results;
        if (hasGroup && hasCity) {
            results = filterAvailable
                    ? bloodLinkRepo.findByBloodGroupIgnoreCaseAndCityIgnoreCaseAndAvailableTrue(bloodGroup.trim(), city.trim())
                    : bloodLinkRepo.findByBloodGroupIgnoreCaseAndCityIgnoreCase(bloodGroup.trim(), city.trim());
        } else if (hasGroup) {
            results = bloodLinkRepo.findByBloodGroupIgnoreCase(bloodGroup.trim());
            if (filterAvailable) {
                results = results.stream().filter(BloodLinkModel::isAvailable).toList();
            }
        } else if (hasCity) {
            results = bloodLinkRepo.findByCityIgnoreCase(city.trim());
            if (filterAvailable) {
                results = results.stream().filter(BloodLinkModel::isAvailable).toList();
            }
        } else {
            results = bloodLinkRepo.findAll();
            if (filterAvailable) {
                results = results.stream().filter(BloodLinkModel::isAvailable).toList();
            }
        }

        results.forEach(this::refreshDonorAvailability);
        return results;
    }

    // 3. MARK DONOR UNAVAILABLE AFTER DONATION (90-day cooldown)
    @Override
    public BloodLinkModel markDonated(Long donorId, LocalDate donationDate) {
        BloodLinkModel donor = bloodLinkRepo.findById(donorId)
                .orElseThrow(() -> new RuntimeException("Donor not found with ID: " + donorId));

        LocalDate date = (donationDate != null) ? donationDate : LocalDate.now();
        donor.setLastDonationDate(date);

        long daysPassed = ChronoUnit.DAYS.between(date, LocalDate.now());
        donor.setAvailable(daysPassed >= COOLDOWN_DAYS);

        return bloodLinkRepo.save(donor);
    }

    // 4. AUTO RE-ENABLE DONOR AVAILABILITY AFTER 90 DAYS (Runs every hour automatically)
    @Override
    @Scheduled(cron = "0 0 * * * ?")
    public void autoReEnableDonors() {
        LocalDate cutoffDate = LocalDate.now().minusDays(COOLDOWN_DAYS);
        List<BloodLinkModel> eligibleDonors = bloodLinkRepo.findByAvailableFalseAndLastDonationDateLessThanEqual(cutoffDate);
        for (BloodLinkModel donor : eligibleDonors) {
            donor.setAvailable(true);
            bloodLinkRepo.save(donor);
        }
    }

    // 5. VIEW TOTAL DONORS REGISTERED PER BLOOD GROUP
    @Override
    public Map<String, Long> getDonorCountPerBloodGroup() {
        List<Object[]> queryResults = bloodLinkRepo.countDonorsPerBloodGroup();
        Map<String, Long> countMap = new LinkedHashMap<>();

        // Standard blood group keys
        String[] groups = {"A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"};
        for (String bg : groups) {
            countMap.put(bg, 0L);
        }

        for (Object[] row : queryResults) {
            if (row[0] != null && row[1] != null) {
                String bg = row[0].toString().trim().toUpperCase();
                Long count = ((Number) row[1]).longValue();
                countMap.put(bg, count);
            }
        }
        return countMap;
    }
}
