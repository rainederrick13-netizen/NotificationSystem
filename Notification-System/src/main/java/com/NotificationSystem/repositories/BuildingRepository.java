package com.NotificationSystem.repositories;

import com.NotificationSystem.entities.Building;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BuildingRepository extends JpaRepository<Building, Long> {

    List<Building> findByZone_IdOrderByNameAsc(Long zoneId);
}