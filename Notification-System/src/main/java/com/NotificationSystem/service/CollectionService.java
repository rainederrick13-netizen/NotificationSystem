package com.NotificationSystem.service;

import com.NotificationSystem.entities.Collection;
import com.NotificationSystem.entities.Resident;
import com.NotificationSystem.entities.Schedule;
import com.NotificationSystem.repositories.CollectionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class CollectionService {

    @Autowired
    private CollectionRepository collectionRepository;

    @Autowired
    private ResidentService residentService;

    @Transactional
    public void markScheduleCollected(Schedule schedule) {
        Long zoneId = schedule.getZone().getId();
        List<Resident> residents = residentService.getResidentsByZoneId(zoneId);
        log.info("Marking collections for schedule {} in zone {}: {} residents found",
                schedule.getId(), zoneId, residents.size());

        if (residents.isEmpty()) {
            log.warn("Zone {} has no residents, no collections created for schedule {}",
                    zoneId, schedule.getId());
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        int created = 0;
        int updated = 0;

        for (Resident resident : residents) {
            Optional<Collection> existing = collectionRepository
                    .findFirstByScheduleIdAndResidentId(schedule.getId(), resident.getId());

            Collection collection = existing.orElseGet(() -> Collection.builder()
                    .scheduleId(schedule.getId())
                    .residentId(resident.getId())
                    .build());

            if (existing.isPresent()) {
                updated++;
                log.debug("Updating collection {} for resident {} (schedule {})",
                        collection.getId(), resident.getId(), schedule.getId());
            } else {
                created++;
                log.debug("Creating collection for resident {} (schedule {})",
                        resident.getId(), schedule.getId());
            }

            collection.setStatus(Collection.Status.COLLECTED);
            collection.setCollectedAt(now);
            collectionRepository.save(collection);
        }

        log.info("Schedule {} done: {} collections created, {} updated",
                schedule.getId(), created, updated);
    }

    // Get all collections
    public List<Collection> getAllCollections() {
        List<Collection> collections = collectionRepository.findAll();
        log.debug("Fetched {} collections", collections.size());
        return collections;
    }

    // Get collection by ID
    public Optional<Collection> getCollectionById(Long id) {
        Optional<Collection> collection = collectionRepository.findById(id);
        if (collection.isEmpty()) {
            log.warn("Collection {} not found", id);
        }
        return collection;
    }

    // Create or update collection
    public Collection saveCollection(Collection collection) {
        boolean isNew = collection.getId() == null;
        log.info("Saving collection: id={}, new={}, scheduleId={}, residentId={}, status={}",
                collection.getId(), isNew, collection.getScheduleId(),
                collection.getResidentId(), collection.getStatus());

        Collection saved = collectionRepository.save(collection);
        log.info("Collection {} saved", saved.getId());
        return saved;
    }

    // Delete collection by ID
    public void deleteCollection(Long id) {
        log.info("Deleting collection {}", id);
        collectionRepository.deleteById(id);
        log.info("Collection {} deleted", id);
    }

    // Get collections by status
    public List<Collection> getCollectionsByStatus(Collection.Status status) {
        List<Collection> collections = collectionRepository.findByStatus(status);
        log.debug("Fetched {} collections with status {}", collections.size(), status);
        return collections;
    }

    public List<Collection> getCollectionsByResidentId(Long residentId) {
        List<Collection> collections = collectionRepository.findByResidentId(residentId);
        log.debug("Fetched {} collections for resident {}", collections.size(), residentId);
        return collections;
    }

    public List<Collection> getCollectionsByScheduleId(Long scheduleId) {
        List<Collection> collections = collectionRepository.findByScheduleId(scheduleId);
        log.debug("Fetched {} collections for schedule {}", collections.size(), scheduleId);
        return collections;
    }
}