package com.NotificationSystem.service;



import com.NotificationSystem.entities.Resident;
import com.NotificationSystem.repositories.ResidentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class ResidentService {

    @Autowired
    private ResidentRepository residentRepository;

    // Get all residents
    public List<Resident> getAllResidents() {
        return residentRepository.findAll();
    }

    // Get resident by ID
    public Optional<Resident> getResidentById(Long id) {
        return residentRepository.findById(id);
    }

    // Create or update resident
    public Resident saveResident(Resident resident) {
        Long zoneId = resident.getZone() != null ? resident.getZone().getId() : null;
        String phone = resident.getPhoneNumber() == null ? null : resident.getPhoneNumber().trim();
        resident.setPhoneNumber(phone);

        if (zoneId != null && phone != null) {
            boolean duplicate = resident.getId() == null
                    ? residentRepository.existsByZone_IdAndPhoneNumber(zoneId, phone)
                    : residentRepository.existsByZone_IdAndPhoneNumberAndIdNot(zoneId, phone, resident.getId());
            if (duplicate) {
                log.warn("Resident rejected: phone {} already exists in zone {}", phone, zoneId);
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "A resident with this phone number already exists in this zone");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        if (resident.getId() == null) {
            resident.setCreatedAt(now);
        }
        resident.setUpdatedAt(now);
        Resident saved = residentRepository.save(resident);
        log.info("Resident {} saved in zone {}", saved.getId(), zoneId);
        return saved;
    }

    // Delete resident by ID
    public void deleteResidentById(Long id) {
        residentRepository.deleteById(id);
    }


    public List<Resident> updateResidents(List<Resident> residents) {
        return residentRepository.saveAll(residents);
    }
    public List<Resident>getResidentsByZoneId(Long zoneId) {
        return residentRepository.findByZone_Id(zoneId);
    }

}

