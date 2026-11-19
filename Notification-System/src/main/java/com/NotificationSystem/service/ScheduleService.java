package com.NotificationSystem.service;

import com.NotificationSystem.entities.Schedule;
import com.NotificationSystem.entities.Truck;
import com.NotificationSystem.repositories.NotificationRepository;
import com.NotificationSystem.repositories.ScheduleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
public class ScheduleService {

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private CollectionService collectionService;

    @Autowired
    private NotificationRepository notificationRepository;

    public List<Schedule> getAllSchedules() {
        List<Schedule> schedules = scheduleRepository.findAll();
        log.debug("Fetched {} schedules", schedules.size());
        return schedules;
    }

    public Optional<Schedule> getScheduleById(Long id) {
        Optional<Schedule> schedule = scheduleRepository.findById(id);
        if (schedule.isEmpty()) {
            log.warn("Schedule {} not found", id);
        }
        return schedule;
    }

    // Create or update schedule
    @Transactional
    public Schedule saveSchedule(Schedule schedule) {
        boolean isNew = schedule.getId() == null;
        log.info("Saving schedule: id={}, new={}, zoneId={}, truckId={}, date={}, status={}",
                schedule.getId(), isNew,
                schedule.getZone() != null ? schedule.getZone().getId() : null,
                schedule.getTruck() != null ? schedule.getTruck().getId() : null,
                schedule.getCollectionDate(), schedule.getStatus());

        checkTruckAvailability(schedule);

        Schedule.Status previousStatus = null;
        if (!isNew) {
            previousStatus = scheduleRepository.findById(schedule.getId())
                    .map(Schedule::getStatus).orElse(null);
            log.debug("Schedule {} previous status: {}", schedule.getId(), previousStatus);
        }

        LocalDateTime now = LocalDateTime.now();
        if (isNew) {
            schedule.setCreatedAt(now);
        }
        schedule.setUpdatedAt(now);
        Schedule saved = scheduleRepository.save(schedule);
        log.info("Schedule {} saved with status {}", saved.getId(), saved.getStatus());

        if (saved.getStatus() == Schedule.Status.COMPLETED
                && previousStatus != Schedule.Status.COMPLETED) {
            log.info("Schedule {} changed {} -> COMPLETED, marking collections",
                    saved.getId(), previousStatus);
            collectionService.markScheduleCollected(saved);
        } else if (saved.getStatus() == Schedule.Status.COMPLETED) {
            log.debug("Schedule {} was already COMPLETED, skipping collection update",
                    saved.getId());
        }
        return saved;
    }

    private void checkTruckAvailability(Schedule schedule) {
        Truck truck = schedule.getTruck();
        if (truck == null || truck.getId() == null) {
            log.debug("No truck assigned to schedule {}, skipping availability check",
                    schedule.getId());
            return;
        }
        if (schedule.getStatus() == Schedule.Status.CANCELLED) {
            log.debug("Schedule {} is CANCELLED, skipping availability check",
                    schedule.getId());
            return;
        }

        boolean clash = scheduleRepository
                .findByTruck_IdAndCollectionDate(truck.getId(), schedule.getCollectionDate())
                .stream()
                .anyMatch(s -> !Objects.equals(s.getId(), schedule.getId())
                        && s.getStatus() != Schedule.Status.CANCELLED);

        if (clash) {
            log.warn("Truck {} already booked on {}, rejecting schedule {}",
                    truck.getId(), schedule.getCollectionDate(), schedule.getId());
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Truck already booked on that date");
        }
        log.debug("Truck {} is available on {}", truck.getId(), schedule.getCollectionDate());
    }

    public void deleteSchedule(Long id) {
        log.info("Delete requested for schedule {}", id);
        if (notificationRepository.existsById(id)) {
            log.warn("Delete blocked for schedule {}: notifications exist", id);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This schedule already has notification, so it can't deleted. Cancel it instead");
        }
        scheduleRepository.deleteById(id);
        log.info("Schedule {} deleted", id);
    }

    public List<Schedule> getScheduleByStatus(Schedule.Status status) {
        return scheduleRepository.findByStatus(status);
    }

    public List<Schedule> getSchedulesByZoneId(Long zoneId) {
        return scheduleRepository.findByZone_Id(zoneId);
    }

    public List<Schedule> getSchedulesByStatus(Schedule.Status status) {
        return scheduleRepository.findByStatus(status);
    }

    public List<Schedule> getSchedulesByZoneIdAndStatus(Long zoneId, Schedule.Status status) {
        return scheduleRepository.findByZone_IdAndStatus(zoneId, status);
    }
}