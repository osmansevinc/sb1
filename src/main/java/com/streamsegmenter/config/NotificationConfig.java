package com.streamsegmenter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "notification")
public class NotificationConfig {
    private Email email = new Email();
    private Sms sms = new Sms();
    private List<String> notifyBeforeMinutes;
    private boolean enabled;

    @Data
    public static class Email {
        private boolean enabled;
        private String host;
        private int port;
        private String username;
        private String password;
        private String from;
        private List<String> to;
        private String subject;
        private String template;
    }

    @Data
    public static class Sms {
        private boolean enabled;
        private String apiKey;
        private String apiSecret;
        private String from;
        private List<String> to;
        private String template;
    }
}
