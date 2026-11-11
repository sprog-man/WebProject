package com.xiaoliu.webporduce.auth;

public record CsrfResponse(
        String token,
        String headerName,
        String parameterName
) {
}
