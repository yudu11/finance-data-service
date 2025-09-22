package com.example.financedataservice.config;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.WebRequest;

/**
 * Ensures whitelabel error page timestamps render in Singapore Time (SGT).
 */
@Component
public class CustomErrorAttributes extends DefaultErrorAttributes {

    private static final ZoneId SINGAPORE_ZONE = ZoneId.of("Asia/Singapore");
    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss z yyyy", Locale.ENGLISH);

    @Override
    public Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {
        Map<String, Object> errorAttributes = super.getErrorAttributes(webRequest, options);
        Object timestamp = errorAttributes.get("timestamp");
        ZonedDateTime zonedTimestamp = toZonedDateTime(timestamp);
        if (zonedTimestamp != null) {
            errorAttributes.put("timestamp", DISPLAY_FORMAT.format(zonedTimestamp.withZoneSameInstant(SINGAPORE_ZONE)));
        }
        return errorAttributes;
    }

    private ZonedDateTime toZonedDateTime(Object timestamp) {
        if (timestamp instanceof ZonedDateTime zonedDateTime) {
            return zonedDateTime;
        }
        if (timestamp instanceof Instant instant) {
            return instant.atZone(SINGAPORE_ZONE);
        }
        if (timestamp instanceof LocalDateTime localDateTime) {
            return localDateTime.atZone(SINGAPORE_ZONE);
        }
        if (timestamp instanceof Date date) {
            return date.toInstant().atZone(SINGAPORE_ZONE);
        }
        if (timestamp instanceof CharSequence sequence) {
            try {
                return ZonedDateTime.parse(sequence, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        .withZoneSameInstant(SINGAPORE_ZONE);
            } catch (Exception ex) {
                return null;
            }
        }
        return null;
    }
}
