package com.NotificationSystem.service;

import com.NotificationSystem.entities.Building;
import com.NotificationSystem.entities.Resident;
import com.NotificationSystem.repositories.BuildingRepository;
import com.NotificationSystem.repositories.ResidentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class BuildingService {

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private ResidentRepository residentRepository;

    public List<Building> getAllBuildings() {
        return buildingRepository.findAll();
    }

    public Optional<Building> getBuildingById(Long id) {
        return buildingRepository.findById(id);
    }

    public List<Building> getBuildingsByZoneId(Long zoneId) {
        return buildingRepository.findByZone_IdOrderByNameAsc(zoneId);
    }

    public Building saveBuilding(Building building) {
        building.setUpdatedAt(LocalDateTime.now());
        if (building.getId() == null) {
            building.setCreatedAt(LocalDateTime.now());
        }
        return buildingRepository.save(building);
    }

    // Residents are kept; they just lose their building (and unit number stays as typed)
    @Transactional
    public void deleteBuilding(Long id) {
        List<Resident> residents = residentRepository.findByBuilding_Id(id);
        residents.forEach(r -> r.setBuilding(null));
        residentRepository.saveAll(residents);
        buildingRepository.deleteById(id);
    }
}