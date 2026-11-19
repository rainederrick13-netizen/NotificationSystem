package com.NotificationSystem.controller;

import com.NotificationSystem.entities.Building;
import com.NotificationSystem.service.BuildingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/buildings")
public class BuildingController {

    @Autowired
    private BuildingService buildingService;

    @GetMapping
    public ResponseEntity<List<Building>> getAllBuildings() {
        return ResponseEntity.ok(buildingService.getAllBuildings());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Building> getBuildingById(@PathVariable Long id) {
        return buildingService.getBuildingById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Used by the resident form dropdown and the zone drawer
    @GetMapping("/zone/{zoneId}")
    public ResponseEntity<List<Building>> getBuildingsByZone(@PathVariable Long zoneId) {
        return ResponseEntity.ok(buildingService.getBuildingsByZoneId(zoneId));
    }

    @PostMapping
    public ResponseEntity<Building> createBuilding(@RequestBody Building building) {
        return ResponseEntity.ok(buildingService.saveBuilding(building));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Building> updateBuilding(
            @PathVariable Long id,
            @RequestBody Building updated) {

        Optional<Building> existing = buildingService.getBuildingById(id);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        updated.setId(id);
        updated.setCreatedAt(existing.get().getCreatedAt());
        return ResponseEntity.ok(buildingService.saveBuilding(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBuilding(@PathVariable Long id) {
        if (buildingService.getBuildingById(id).isPresent()) {
            buildingService.deleteBuilding(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}