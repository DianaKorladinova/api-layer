/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.integration.proxy;

import org.apache.http.client.utils.URIBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;
import org.zowe.apiml.util.TestWithStartedInstances;
import org.zowe.apiml.util.categories.TestsNotMeantForZowe;
import org.zowe.apiml.util.categories.WebsocketTest;
import org.zowe.apiml.util.config.ConfigReader;
import org.zowe.apiml.util.config.GatewayServiceConfiguration;
import org.zowe.apiml.util.http.HttpClientUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.apache.tomcat.websocket.Constants.SSL_CONTEXT_PROPERTY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestsNotMeantForZowe
@WebsocketTest
class WebSocketProxyTest implements TestWithStartedInstances {
    private final GatewayServiceConfiguration serviceConfiguration = ConfigReader.environmentConfiguration().getGatewayServiceConfiguration();

    private static final int WAIT_TIMEOUT_MS = 10000;
    private static final String UPPERCASE_URL = "/ws/v1/discoverableclient/uppercase";
    private static final String HEADER_URL = "/ws/v1/discoverableclient/header";

    private static final WebSocketHttpHeaders VALID_AUTH_HEADERS = new WebSocketHttpHeaders();
    private static final WebSocketHttpHeaders INVALID_AUTH_HEADERS = new WebSocketHttpHeaders();
    private static final String validToken = "apimlAuthenticationToken=tokenValue";


    @BeforeAll
    static void setup() {
        String plainCred = "user:pass";
        String base64cred = Base64.getEncoder().encodeToString(plainCred.getBytes());
        VALID_AUTH_HEADERS.add("Authorization", "Basic " + base64cred);

        String invalidPlainCred = "user:invalidPass";
        String invalidBase64cred = Base64.getEncoder().encodeToString(invalidPlainCred.getBytes());
        INVALID_AUTH_HEADERS.add("Authorization", "Basic " + invalidBase64cred);

    }

    private TextWebSocketHandler appendResponseHandler(StringBuilder target, int countToNotify) {
        final AtomicInteger counter = new AtomicInteger(countToNotify);
        return new TextWebSocketHandler() {
            @Override
            public void handleTextMessage(WebSocketSession session, TextMessage message) {
                synchronized (target) {
                    target.append(message.getPayload());
                    if (counter.decrementAndGet() == 0) {
                        target.notify();
                    }
                }
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
                synchronized (target) {
                    target.append(status.toString());
                    if (counter.decrementAndGet() == 0) {
                        target.notify();
                    }
                }
            }
        };
    }

    private String discoverableClientGatewayUrl(String gatewayUrl) throws URISyntaxException {
        String scheme = serviceConfiguration.getScheme().equals("http") ? "ws" : "wss";
        String host = serviceConfiguration.getHost();
        int port = serviceConfiguration.getPort();

        return new URIBuilder().setScheme(scheme).setHost(host).setPort(port).setPath(gatewayUrl).build().toString();
    }

    private WebSocketSession appendingWebSocketSession(String url, WebSocketHttpHeaders headers, StringBuilder response, int countToNotify)
        throws Exception {
        StandardWebSocketClient client = new StandardWebSocketClient();
        client.getUserProperties().put(SSL_CONTEXT_PROPERTY, HttpClientUtils.ignoreSslContext());
        URI uri = UriComponentsBuilder.fromUriString(url).build().encode().toUri();
        return client.doHandshake(appendResponseHandler(response, countToNotify), headers, uri).get(30000, TimeUnit.MILLISECONDS);
    }

    private WebSocketSession appendingWebSocketSession(String url, StringBuilder response, int countToNotify)
        throws Exception {
        return appendingWebSocketSession(url, null, response, countToNotify);
    }


    @Nested
    class WhenRoutingSession {
        @Nested
        class Authentication {
            @Nested
            class WhenValid {
                @Nested
                class ReturnSuccess {
                    @Test
                    void message() throws Exception {
                        final StringBuilder response = new StringBuilder();

                        WebSocketSession session = appendingWebSocketSession(discoverableClientGatewayUrl(UPPERCASE_URL), VALID_AUTH_HEADERS, response, 1);

                        session.sendMessage(new TextMessage("hello world!"));
                        synchronized (response) {
                            response.wait(WAIT_TIMEOUT_MS);
                        }

                        assertEquals("HELLO WORLD!", response.toString());
                        session.close();
                    }

                    @Test
                    void headers() throws Exception {
                        final StringBuilder response = new StringBuilder();
                        VALID_AUTH_HEADERS.add("X-Test", "value");
                        VALID_AUTH_HEADERS.add("Cookie", validToken);
                        WebSocketSession session = appendingWebSocketSession(discoverableClientGatewayUrl(HEADER_URL), VALID_AUTH_HEADERS, response, 1);

                        session.sendMessage(new TextMessage("gimme those headers"));
                        synchronized (response) {
                            response.wait(WAIT_TIMEOUT_MS);
                        }

                        assertTrue(response.toString().contains("x-test:\"value\""));
                        assertTrue(response.toString().contains(validToken));
                        session.sendMessage(new TextMessage("bye"));
                        session.close();
                    }
                }

                @Nested
                class ReturnError {
                    @Test
                    void whenPathIsNotCorrect() throws Exception {
                        final StringBuilder response = new StringBuilder();
                        appendingWebSocketSession(discoverableClientGatewayUrl(UPPERCASE_URL + "bad"), VALID_AUTH_HEADERS, response, 1);

                        synchronized (response) {
                            response.wait(WAIT_TIMEOUT_MS);
                        }

                        System.out.println("Response: " + response.toString());
                        assertEquals(0, response.toString().indexOf("CloseStatus[code=1003,"));
                    }

                    @Test
                    void whenServiceIsNotCorrect() throws Exception {
                        final StringBuilder response = new StringBuilder();
                        appendingWebSocketSession(discoverableClientGatewayUrl("/ws/v1/wrong-service/uppercase"), VALID_AUTH_HEADERS, response, 1);

                        synchronized (response) {
                            response.wait(WAIT_TIMEOUT_MS);
                        }

                        assertEquals("CloseStatus[code=1003, reason=Requested service wrong-service is not known by the gateway]",
                            response.toString());
                    }

                    @Test
                    void whenUrlFormatIsNotCorrect() throws Exception {
                        final StringBuilder response = new StringBuilder();
                        appendingWebSocketSession(discoverableClientGatewayUrl("/ws/wrong"), response, 1);

                        synchronized (response) {
                            response.wait(WAIT_TIMEOUT_MS);
                        }

                        assertEquals("CloseStatus[code=1003, reason=Invalid URL format]", response.toString());
                    }
                }
            }

            @Nested
            class WhenInvalid {
                @Test
                void returnError() throws Exception {
                    final StringBuilder response = new StringBuilder();

                    WebSocketSession session = appendingWebSocketSession(discoverableClientGatewayUrl(UPPERCASE_URL), INVALID_AUTH_HEADERS, response, 1);

                    session.sendMessage(new TextMessage("hello world!"));
                    synchronized (response) {
                        response.wait(WAIT_TIMEOUT_MS);
                    }

                    assertEquals("CloseStatus[code=1003, reason=Invalid login credentials]", response.toString());
                    session.close();
                }
            }
        }

    }

    @Nested
    class WhenClosingSession {
        @Test
        void getCorrectResponse() throws Exception {
            final StringBuilder response = new StringBuilder();
            WebSocketSession session = appendingWebSocketSession(discoverableClientGatewayUrl(UPPERCASE_URL), VALID_AUTH_HEADERS, response, 2);

            session.sendMessage(new TextMessage("bye"));
            synchronized (response) {
                response.wait(WAIT_TIMEOUT_MS);
            }

            assertEquals("BYECloseStatus[code=1000, reason=null]", response.toString());
        }
    }


}
