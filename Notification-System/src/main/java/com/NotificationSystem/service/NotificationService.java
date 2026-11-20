package com.NotificationSystem.service;

import com.NotificationSystem.entities.Notification;
import com.NotificationSystem.entities.Schedule;
import com.NotificationSystem.repositories.NotificationRepository;
import com.NotificationSystem.repositories.ScheduleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    // Get all notifications
    public List<Notification> getAllNotifications() {
        List<Notification> all = notificationRepository.findAll();
        log.debug("Fetched {} notifications", all.size());
        return all;
    }

    // Get notification by ID
    public Optional<Notification> getNotificationById(Long id) {
        Optional<Notification> n = notificationRepository.findById(id);
        if (n.isEmpty()) {
            log.warn("Notification {} not found", id);
        }
        return n;
    }

    // Create or update notification
    public Notification saveNotification(Notification notification) {
        boolean isNew = notification.getId() == null;
        Long scheduleId = notification.getSchedule() != null ? notification.getSchedule().getId() : null;
        Long residentId = notification.getResident() != null ? notification.getResident().getId() : null;

        log.info("Saving notification: id={}, new={}, scheduleId={}, residentId={}, status={}",
                notification.getId(), isNew, scheduleId, residentId, notification.getStatus());

        if (scheduleId == null || residentId == null) {
            log.warn("Notification rejected: schedule or resident missing");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Schedule and resident are required");
        }

        LocalDateTime now = LocalDateTime.now();

        if (isNew) {
            notification.setCreatedAt(now);
            if (notification.getChannel() == null) {
                notification.setChannel(Notification.Channel.SMS);
            }
        } else {
            // The edit form does not send every column, so keep what is already stored
            Notification existing = notificationRepository.findById(notification.getId()).orElse(null);
            if (existing != null) {
                if (notification.getChannel() == null) notification.setChannel(existing.getChannel());
                if (notification.getNoticeType() == null) notification.setNoticeType(existing.getNoticeType());
                if (notification.getWhatsappMessageId() == null)
                    notification.setWhatsappMessageId(existing.getWhatsappMessageId());
                if (notification.getErrorCode() == null) notification.setErrorCode(existing.getErrorCode());
                if (notification.getErrorDetail() == null) notification.setErrorDetail(existing.getErrorDetail());

                boolean sameSchedule = existing.getSchedule() != null
                        && Objects.equals(existing.getSchedule().getId(), scheduleId);
                if (notification.getNoticeDate() == null && sameSchedule) {
                    notification.setNoticeDate(existing.getNoticeDate());
                }
            }
        }

        if (notification.getNoticeType() == null) {
            notification.setNoticeType(Notification.NoticeType.SCHEDULED);
        }

        // The date this notice announces comes from the schedule
        if (notification.getNoticeDate() == null) {
            LocalDate date = scheduleRepository.findById(scheduleId)
                    .map(Schedule::getCollectionDate)
                    .orElseThrow(() -> {
                        log.warn("Notification rejected: schedule {} not found", scheduleId);
                        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Schedule " + scheduleId + " not found");
                    });
            notification.setNoticeDate(date);
            log.debug("Notice date for schedule {} set to {}", scheduleId, date);
        }

        notification.setUpdatedAt(now);

        try {
            Notification saved = notificationRepository.save(notification);
            log.info("Notification {} saved ({}, {}, {})",
                    saved.getId(), saved.getChannel(), saved.getNoticeType(), saved.getStatus());
            return saved;
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate notification blocked by unique index: schedule {}, resident {}, {}, {}, {}",
                    scheduleId, residentId, notification.getChannel(),
                    notification.getNoticeType(), notification.getNoticeDate());
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This resident already has this notification for that schedule and channel");
        }
    }

    // Delete notification by ID
    public void deleteNotification(Long id) {
        log.info("Delete requested for notification {}", id);
        notificationRepository.deleteById(id);
        log.info("Notification {} deleted", id);
    }

    // Get notifications by status
    public List<Notification> getNotificationsByStatus(Notification.Status status) {
        return notificationRepository.findByStatus(status);
    }

    // Get notifications by resident
    public List<Notification> getNotificationsByResidentId(Long residentId) {
        return notificationRepository.findByResident_Id(residentId);
    }

    // Get notifications by schedule
    public List<Notification> getNotificationsByScheduleId(Long scheduleId) {
        return notificationRepository.findBySchedule_Id(scheduleId);
    }
}