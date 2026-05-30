package com.myworld.core.exception;

import lombok.*;
import java.time.OffsetDateTime;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorResponse {
    private int status;
    private String error;
    private String message;
    private OffsetDateTime timestamp;
    private Map<String, String> fieldErrors;
    // FIX: added for request tracing — correlate logs with API error responses
    private String requestId;
}
