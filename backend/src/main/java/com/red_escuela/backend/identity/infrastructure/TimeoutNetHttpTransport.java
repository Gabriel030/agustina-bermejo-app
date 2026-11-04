package com.red_escuela.backend.identity.infrastructure;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class TimeoutNetHttpTransport extends HttpTransport {

    private static final List<String> SUPPORTED_METHODS = List.of("GET", "POST", "HEAD", "DELETE", "PUT", "PATCH");

    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    TimeoutNetHttpTransport(Duration connectTimeout, Duration readTimeout) {
        this.connectTimeoutMillis = Math.toIntExact(connectTimeout.toMillis());
        this.readTimeoutMillis = Math.toIntExact(readTimeout.toMillis());
    }

    @Override
    public boolean supportsMethod(String method) {
        return SUPPORTED_METHODS.contains(method);
    }

    @Override
    protected LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(connectTimeoutMillis);
        connection.setReadTimeout(readTimeoutMillis);
        return new BoundedRequest(connection);
    }

    private static final class BoundedRequest extends LowLevelHttpRequest {

        private final HttpURLConnection connection;

        private BoundedRequest(HttpURLConnection connection) {
            this.connection = connection;
        }

        @Override
        public void addHeader(String name, String value) {
            connection.addRequestProperty(name, value);
        }

        @Override
        public void setTimeout(int connectTimeout, int readTimeout) {
        }

        @Override
        public LowLevelHttpResponse execute() throws IOException {
            if (getStreamingContent() != null) {
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(getContentLength());
                try (OutputStream out = connection.getOutputStream()) {
                    getStreamingContent().writeTo(out);
                }
            }
            connection.connect();
            return new BoundedResponse(connection);
        }
    }

    private static final class BoundedResponse extends LowLevelHttpResponse {

        private final HttpURLConnection connection;
        private final int statusCode;
        private final List<String> headerNames = new ArrayList<>();
        private final List<String> headerValues = new ArrayList<>();

        private BoundedResponse(HttpURLConnection connection) throws IOException {
            this.connection = connection;
            this.statusCode = connection.getResponseCode();
            for (Map.Entry<String, List<String>> header : connection.getHeaderFields().entrySet()) {
                if (header.getKey() == null) {
                    continue;
                }
                for (String value : header.getValue()) {
                    headerNames.add(header.getKey());
                    headerValues.add(value);
                }
            }
        }

        @Override
        public InputStream getContent() throws IOException {
            return statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
        }

        @Override
        public String getContentEncoding() {
            return connection.getContentEncoding();
        }

        @Override
        public long getContentLength() {
            return connection.getContentLengthLong();
        }

        @Override
        public String getContentType() {
            return connection.getContentType();
        }

        @Override
        public String getStatusLine() {
            return connection.getHeaderField(0);
        }

        @Override
        public int getStatusCode() {
            return statusCode;
        }

        @Override
        public String getReasonPhrase() throws IOException {
            return connection.getResponseMessage();
        }

        @Override
        public int getHeaderCount() {
            return headerNames.size();
        }

        @Override
        public String getHeaderName(int index) {
            return headerNames.get(index);
        }

        @Override
        public String getHeaderValue(int index) {
            return headerValues.get(index);
        }

        @Override
        public void disconnect() {
            connection.disconnect();
        }
    }
}
