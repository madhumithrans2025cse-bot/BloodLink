package com.example.BloodLink_api.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.BloodLink_api.model.BloodLinkModel;
import com.example.BloodLink_api.service.BloodLinkService;

@RestController
@RequestMapping("/BloodLink")
public class BloodLinkController {

    private final BloodLinkService bloodLinkService;

    public BloodLinkController(BloodLinkService bloodLinkService) {
        this.bloodLinkService = bloodLinkService;
    }

    // Health / Welcome endpoint
    // GET http://localhost:8080/BloodLink
    @GetMapping({"", "/"})
    public ResponseEntity<String> welcome() {
        return ResponseEntity.ok("BloodLink API is running! Access /BloodLink/donors, /BloodLink/search, or /BloodLink/count-by-blood-group.");
    }

    // 1. REGISTER DONOR (with blood group, city, and last donation date)
    // POST http://localhost:8080/BloodLink/register
    @PostMapping("/register")
    public ResponseEntity<BloodLinkModel> registerDonor(@RequestBody BloodLinkModel donor) {
        BloodLinkModel created = bloodLinkService.registerDonor(donor);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    // Legacy/compatibility endpoint
    // POST http://localhost:8080/BloodLink/createBloodLink
    @PostMapping("/createBloodLink")
    public ResponseEntity<BloodLinkModel> createBloodLink(@RequestBody BloodLinkModel donor) {
        BloodLinkModel created = bloodLinkService.createBloodLink(donor);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    // READ ALL DONORS
    // GET http://localhost:8080/BloodLink/donors
    @GetMapping("/donors")
    public ResponseEntity<List<BloodLinkModel>> getAllDonors() {
        return ResponseEntity.ok(bloodLinkService.getAllDonors());
    }

    // READ DONOR BY ID
    // GET http://localhost:8080/BloodLink/donors/{id}
    @GetMapping("/donors/{id}")
    public ResponseEntity<BloodLinkModel> getDonorById(@PathVariable Long id) {
        Optional<BloodLinkModel> donor = bloodLinkService.getDonorById(id);
        return donor.map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    // UPDATE DONOR
    // PUT http://localhost:8080/BloodLink/donors/{id}
    @PutMapping("/donors/{id}")
    public ResponseEntity<BloodLinkModel> updateDonor(@PathVariable Long id, @RequestBody BloodLinkModel donor) {
        BloodLinkModel updated = bloodLinkService.updateDonor(id, donor);
        if (updated != null) {
            return ResponseEntity.ok(updated);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    // DELETE DONOR
    // DELETE http://localhost:8080/BloodLink/donors/{id}
    @DeleteMapping("/donors/{id}")
    public ResponseEntity<String> deleteDonor(@PathVariable Long id) {
        boolean deleted = bloodLinkService.deleteDonor(id);
        if (deleted) {
            return ResponseEntity.ok("Donor with ID " + id + " has been successfully removed.");
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Donor not found with ID " + id);
    }

    // 2. SEARCH DONORS BY BLOOD GROUP AND CITY
    // GET http://localhost:8080/BloodLink/search?bloodGroup=O+&city=Chennai&onlyAvailable=true
    @GetMapping("/search")
    public ResponseEntity<List<BloodLinkModel>> searchDonors(
            @RequestParam(required = false) String bloodGroup,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String location,
            @RequestParam(required = false, defaultValue = "false") Boolean onlyAvailable) {
        String targetCity = (city != null && !city.isBlank()) ? city : location;
        List<BloodLinkModel> donors = bloodLinkService.searchDonors(bloodGroup, targetCity, onlyAvailable);
        return ResponseEntity.ok(donors);
    }

    // 3. MARK DONOR UNAVAILABLE AFTER DONATION (90-day cooldown)
    // POST http://localhost:8080/BloodLink/donors/{id}/donate?date=2026-09-28
    @PostMapping("/donors/{id}/donate")
    public ResponseEntity<?> markDonated(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        try {
            BloodLinkModel updated = bloodLinkService.markDonated(id, date);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    // 4. MANUAL TRIGGER TO AUTO RE-ENABLE DONORS (also automated every hour)
    // POST http://localhost:8080/BloodLink/donors/auto-refresh-availability
    @PostMapping("/donors/auto-refresh-availability")
    public ResponseEntity<String> autoReEnableDonors() {
        bloodLinkService.autoReEnableDonors();
        return ResponseEntity.ok("Availability refreshed: eligible donors whose 90-day cooldown has passed are now marked available.");
    }

    // 5. VIEW TOTAL DONORS REGISTERED PER BLOOD GROUP
    // GET http://localhost:8080/BloodLink/count-by-blood-group
    @GetMapping("/count-by-blood-group")
    public ResponseEntity<Map<String, Long>> getDonorCountPerBloodGroup() {
        return ResponseEntity.ok(bloodLinkService.getDonorCountPerBloodGroup());
    }
}
