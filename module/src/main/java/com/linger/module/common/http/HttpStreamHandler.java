package com.linger.module.common.http;

import java.io.IOException;

@FunctionalInterface
public interface HttpStreamHandler {

    void handle(HttpStreamResponse response) throws IOException;
}
