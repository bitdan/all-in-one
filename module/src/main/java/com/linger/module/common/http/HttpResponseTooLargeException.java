package com.linger.module.common.http;

public class HttpResponseTooLargeException extends HttpRequestException {

    public HttpResponseTooLargeException(int limit) {
        super("HTTP response body exceeds configured limit of " + limit + " bytes");
    }
}
