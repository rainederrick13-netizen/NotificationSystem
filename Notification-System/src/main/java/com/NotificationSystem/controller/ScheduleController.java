package com.NotificationSystem.controller;

import com.NotificationSystem.entities.Notification;
import com.NotificationSystem.entities.Schedule;
import com.NotificationSystem.service.NotificationDispatchService;
import com.NotificationSystem.service.ScheduleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/schedules")
public class ScheduleController {

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private NotificationDispatchService notificationDispatchService;

    @GetMapping
    public ResponseEntity<List<Schedule>> getAllSchedules() {
        return ResponseEntity.ok(scheduleService.getAllSchedules());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Schedule> getScheduleById(@PathVariable Long id) {
        Optional<Schedule> schedule = scheduleService.getScheduleById(id);

        return schedule.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/zone/{zoneId}")
    public ResponseEntity<List<Schedule>> getSchedulesByZone(@PathVariable Long zoneId) {
        return ResponseEntity.ok(scheduleService.getSchedulesByZoneId(zoneId));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<Schedule>> getSchedulesByStatus(@PathVariable Schedule.Status status) {
        return ResponseEntity.ok(scheduleService.getSchedulesByStatus(status));
    }

    @PostMapping
    public ResponseEntity<Schedule> createSchedule(@RequestBody Schedule schedule) {
        Schedule saved = scheduleService.saveSchedule(schedule);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Schedule> updateSchedule(
            @PathVariable Long id,
            @RequestBody Schedule updatedSchedule) {

        Optional<Schedule> existing = scheduleService.getScheduleById(id);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // Copy the old values first: saving updates the same loaded object
        LocalDate oldDate = existing.get().getCollectionDate();
        Schedule.Status oldStatus = existing.get().getStatus();

        updatedSchedule.setId(id);
        Schedule saved = scheduleService.saveSchedule(updatedSchedule);

        Notification.NoticeType notice = null;
        if (saved.getStatus() == Schedule.Status.CANCELLED && oldStatus != Schedule.Status.CANCELLED) {
            notice = Notification.NoticeType.CANCELLED;
        } else if (saved.getStatus() != Schedule.Status.CANCELLED
                && oldDate != null && !oldDate.equals(saved.getCollectionDate())) {
            notice = Notification.NoticeType.UPDATED;
        }

        if (notice != null) {
            try {
                notificationDispatchService.notifyScheduleChange(saved.getId(), notice);
            } catch (Exception e) {
                // The schedule is already saved; a failed notice must not undo it
                log.warn("Could not send {} notice for schedule {}: {}", notice, id, e.getMessage());
            }
        }
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSchedule(@PathVariable Long id) {
        if (scheduleService.getScheduleById(id).isPresent()) {
            scheduleService.deleteSchedule(id);
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/notify")
    public ResponseEntity<?> notifyZone(
            @PathVariable Long id,
            @RequestParam("channel") Notification.Channel channel) {

        Map<String, Object> result = notificationDispatchService.queueZoneNotifications(id, channel);
        return ResponseEntity.ok(result);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatusException(ResponseStatusException ex) {
        HttpStatusCode code = ex.getStatusCode();
        HttpStatus status = HttpStatus.resolve(code.value());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", code.value());
        body.put("error", status != null ? status.getReasonPhrase() : "Error");
        body.put("message", ex.getReason() != null ? ex.getReason() : "Request failed");

        return ResponseEntity.status(code).body(body);
    }
}