package com.NotificationSystem.service;

import com.NotificationSystem.entities.Notification;
import com.NotificationSystem.entities.Resident;
import com.NotificationSystem.entities.Schedule;
import com.NotificationSystem.repositories.NotificationRepository;
import com.NotificationSystem.repositories.ResidentRepository;
import com.NotificationSystem.repositories.ScheduleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class NotificationDispatchService {

    @Autowired
    private ScheduleRepository scheduleRepository;
    @Autowired
    private ResidentRepository residentRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private AfricaTalkingSmsService smsService;
    @Autowired
    private WhatsAppService whatsAppService;

    @Value("${whatsapp.mode:text}")
    private String whatsappMode;

    // Original "notify this zone" action
    public Map<String, Object> queueZoneNotifications(Long scheduleId, Notification.Channel channel) {
        return queueZoneNotifications(scheduleId, channel, Notification.NoticeType.SCHEDULED);
    }

    // Used when a schedule is rescheduled or cancelled. Only residents who were
    // already told about this schedule are contacted, and only on the channel(s) used.
    public Map<String, Object> notifyScheduleChange(Long scheduleId, Notification.NoticeType type) {
        Set<Notification.Channel> channels = notificationRepository.findBySchedule_Id(scheduleId).stream()
                .filter(n -> n.getStatus() != Notification.Status.FAILED)
                .map(Notification::getChannel)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, Object> result = new LinkedHashMap<>();
        if (channels.isEmpty()) {
            log.info("Dispatch: schedule {} changed ({}), residents were never notified, nothing to send",
                    scheduleId, type);
            result.put("queued", 0);
            result.put("duplicatesPrevented", 0);
            result.put("message", "Residents had not been notified yet, so no update was sent");
            return result;
        }

        int queued = 0;
        int duplicates = 0;
        for (Notification.Channel channel : channels) {
            Map<String, Object> r = queueZoneNotifications(scheduleId, channel, type);
            queued += (int) r.get("queued");
            duplicates += (int) r.get("duplicatesPrevented");
        }
        result.put("queued", queued);
        result.put("duplicatesPrevented", duplicates);
        result.put("message", type + " notice queued for " + queued + " resident(s)");
        return result;
    }

    public Map<String, Object> queueZoneNotifications(Long scheduleId,
                                                      Notification.Channel channel,
                                                      Notification.NoticeType type) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new RuntimeException("Schedule not found"));

        if (type == Notification.NoticeType.SCHEDULED
                && (schedule.getStatus() == Schedule.Status.CANCELLED
                || schedule.getStatus() == Schedule.Status.COMPLETED)) {
            log.warn("Dispatch: refused to notify schedule {}, it is {}", scheduleId, schedule.getStatus());
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot send reminders: this schedule is " + schedule.getStatus());
        }

        LocalDate noticeDate = schedule.getCollectionDate();
        List<Resident> residents = residentRepository.findByZone_Id(schedule.getZone().getId());

        // Same schedule + channel + type + date = the same notice
        Map<Long, Notification> sameNotice = notificationRepository.findBySchedule_Id(scheduleId).stream()
                .filter(n -> n.getChannel() == channel
                        && n.getNoticeType() == type
                        && noticeDate.equals(n.getNoticeDate()))
                .collect(Collectors.toMap(n -> n.getResident().getId(), n -> n, (a, b) -> a));

        String zoneName = schedule.getZone().getName();
        LocalDateTime now = LocalDateTime.now();
        List<Notification> toSave = new ArrayList<>();
        int duplicatesPrevented = 0;

        for (Resident resident : residents) {
            Notification prev = sameNotice.get(resident.getId());

            if (prev != null && prev.getStatus() != Notification.Status.FAILED) {
                duplicatesPrevented++;
                log.info("Dispatch: skipped resident {} on schedule {} ({}, {}), already notified",
                        resident.getId(), scheduleId, channel, type);
                continue;
            }

            String name = resident.getName() == null ? "" : resident.getName().trim();
            String firstName = name.isEmpty() ? "resident" : name.split("\\s+")[0];
            String text = buildMessage(firstName, zoneName, type, noticeDate);

            // A failed notice is reused, not duplicated, so the unique index is never violated
            Notification n = prev != null ? prev : new Notification();
            n.setSchedule(schedule);
            n.setResident(resident);
            n.setPhoneNumber(resident.getPhoneNumber());
            n.setMessage(text);
            n.setChannel(channel);
            n.setNoticeType(type);
            n.setNoticeDate(noticeDate);
            n.setStatus(Notification.Status.PENDING);
            n.setErrorCode(null);
            n.setErrorDetail(null);
            if (prev == null) n.setCreatedAt(now);
            n.setUpdatedAt(now);
            toSave.add(n);
        }

        List<Notification> saved = notificationRepository.saveAll(toSave);

        log.info("Dispatch: schedule {} via {} ({}): {} residents in zone, {} queued, {} skipped",
                scheduleId, channel, type, residents.size(), saved.size(), duplicatesPrevented);

        dispatchAsync(saved);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("queued", saved.size());
        result.put("duplicatesPrevented", duplicatesPrevented);
        result.put("message", duplicatesPrevented > 0
                ? "Duplicate prevented: " + duplicatesPrevented
                + " resident(s) already notified for this schedule"
                : "All residents queued");
        return result;
    }

    private String buildMessage(String firstName, String zoneName,
                                Notification.NoticeType type, LocalDate date) {
        return switch (type) {
            case SCHEDULED -> "Hello " + firstName + ", waste collection for " + zoneName
                    + " zone is scheduled on " + date + ". Please have your waste ready.";
            case UPDATED -> "Hello " + firstName + ", the waste collection for " + zoneName
                    + " zone has been moved to " + date + ". Please have your waste ready then.";
            case CANCELLED -> "Hello " + firstName + ", the waste collection for " + zoneName
                    + " zone on " + date + " has been cancelled. We will notify you of the new date.";
        };
    }

    // Only needed when whatsapp.mode=template. Create these templates in Meta first.
    private String templateName(Notification.NoticeType type) {
        return switch (type) {
            case SCHEDULED -> "collection_reminder";
            case UPDATED -> "collection_updated";
            case CANCELLED -> "collection_cancelled";
        };
    }

    @Async
    public void dispatchAsync(List<Notification> notifications) {
        int sent = 0;
        int failed = 0;

        for (Notification n : notifications) {
            log.info("Dispatch: notification {} to resident {} via {} (thread {})",
                    n.getId(), n.getResident().getId(), n.getChannel(),
                    Thread.currentThread().getName());
            try {
                if (n.getChannel() == Notification.Channel.SMS) {
                    String response = smsService.sendSms(n.getPhoneNumber(), n.getMessage());
                    n.setWhatsappMessageId(extractSmsMessageId(response));
                } else {
                    String response;
                    if ("template".equalsIgnoreCase(whatsappMode)) {
                        response = whatsAppService.sendTemplateMessage(
                                n.getPhoneNumber(),
                                templateName(n.getNoticeType()),
                                "en",
                                n.getNoticeDate().toString()
                        );
                    } else {
                        response = whatsAppService.sendMessage(n.getPhoneNumber(), n.getMessage());
                    }
                    n.setWhatsappMessageId(extractWhatsAppMessageId(response));
                }
                n.setStatus(Notification.Status.SENT);
                n.setSentAt(LocalDateTime.now());
                sent++;
            } catch (Exception e) {
                String code = (e instanceof MessagingException me) ? me.getCode() : null;
                String msg = e.getMessage();

                if (e instanceof MessagingException) {
                    log.warn("Dispatch: notification {} FAILED [{}]: {}", n.getId(), code, msg);
                } else {
                    log.error("Dispatch: notification {} FAILED with an unexpected error", n.getId(), e);
                }

                n.setStatus(Notification.Status.FAILED);
                n.setErrorCode(code);
                n.setErrorDetail(msg != null && msg.length() > 250 ? msg.substring(0, 250) : msg);
                failed++;
            }
            n.setUpdatedAt(LocalDateTime.now());
            notificationRepository.save(n);

            try {
                Thread.sleep(250);
            } catch (InterruptedException ignored) {
            }
        }

        log.info("Dispatch: batch finished: {} sent, {} failed, {} total",
                sent, failed, notifications.size());
    }

    private String extractWhatsAppMessageId(String json) {
        try {
            return new ObjectMapper().readTree(json)
                    .path("messages").path(0).path("id").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractSmsMessageId(String json) {
        try {
            return new ObjectMapper().readTree(json)
                    .path("SMSMessageData").path("Recipients").path(0).path("messageId").asText(null);
        } catch (Exception e) {
            return null;
        }
    }
}