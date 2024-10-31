package com.streamsegmenter.service;

import com.streamsegmenter.config.NotificationConfig;
import com.streamsegmenter.model.ScheduledStream;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Properties;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationConfig config;
    private final StreamSchedulerService schedulerService;
    private final RestTemplate restTemplate = new RestTemplate();
    private static final String TELEGRAM_API_URL = "https://api.telegram.org/bot%s/sendMessage";

    private JavaMailSender createMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(config.getEmail().getHost());
        mailSender.setPort(config.getEmail().getPort());
        mailSender.setUsername(config.getEmail().getUsername());
        mailSender.setPassword(config.getEmail().getPassword());

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        return mailSender;
    }

    @Scheduled(fixedRate = 60000) // Her dakika kontrol et
    public void checkAndNotify() {
        if (!config.isEnabled()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        schedulerService.getAllScheduledStreams().forEach(stream -> {
            if (stream.isProcessed()) {
                return;
            }

            long minutesUntilStart = ChronoUnit.MINUTES.between(now, stream.getStartTime());
            if (config.getNotifyBeforeMinutes().contains(String.valueOf(minutesUntilStart))) {
                sendNotifications(stream, minutesUntilStart);
            }
        });
    }

    private void sendNotifications(ScheduledStream stream, long minutesUntilStart) {
        if (config.getEmail().isEnabled()) {
            sendEmail(stream, minutesUntilStart);
        }
        if (config.getSms().isEnabled()) {
            sendSms(stream, minutesUntilStart);
        }
        if (config.getTelegram().isEnabled()) {
            sendTelegram(stream, minutesUntilStart);
        }
    }

    private void sendEmail(ScheduledStream stream, long minutesUntilStart) {
        try {
            JavaMailSender mailSender = createMailSender();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(config.getEmail().getFrom());
            helper.setTo(config.getEmail().getTo().toArray(new String[0]));
            helper.setSubject(config.getEmail().getSubject());

            String content = String.format(config.getEmail().getTemplate(),
                    stream.getStreamUrl(),
                    minutesUntilStart,
                    stream.getVideoQuality());
            helper.setText(content, true);

            mailSender.send(message);
            log.info("Email notification sent for stream: {}", stream.getId());
        } catch (Exception e) {
            log.error("Failed to send email notification", e);
        }
    }

    private void sendTelegram(ScheduledStream stream, long minutesUntilStart) {
        try {
            String message = String.format(config.getTelegram().getTemplate(),
                    stream.getStreamUrl(),
                    minutesUntilStart,
                    stream.getVideoQuality());

            String apiUrl = String.format(TELEGRAM_API_URL, config.getTelegram().getBotToken());

            for (String chatId : config.getTelegram().getChatIds()) {
                try {
                    restTemplate.postForObject(apiUrl,
                            new TelegramMessage(chatId, message),
                            TelegramResponse.class);
                    log.info("Telegram notification sent to chat {}", chatId);
                } catch (Exception e) {
                    log.error("Failed to send Telegram notification to chat {}: {}",
                            chatId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to send Telegram notifications", e);
        }
    }

    private void sendSms(ScheduledStream stream, long minutesUntilStart) {
        try {
            String content = String.format(config.getSms().getTemplate(),
                    stream.getStreamUrl(),
                    minutesUntilStart,
                    stream.getVideoQuality());

            log.info("SMS notification would be sent: {}", content);
        } catch (Exception e) {
            log.error("Failed to send SMS notification", e);
        }
    }

    private record TelegramMessage(String chat_id, String text) {}
    private record TelegramResponse(boolean ok, String description) {}
}
