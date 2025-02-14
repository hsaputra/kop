/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.streamnative.pulsar.handlers.kop;

import static com.google.common.base.Preconditions.checkState;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import org.apache.kafka.common.security.auth.SecurityProtocol;

/**
 * The endpoint that KoP binds.
 */
public class EndPoint {

    public static final String END_POINT_SEPARATOR = ",";
    private static final String PROTOCOL_MAP_SEPARATOR = ",";
    private static final String PROTOCOL_SEPARATOR = ":";
    private static final String REGEX = "^(.*)://\\[?([0-9a-zA-Z\\-%._:]*)\\]?:(-?[0-9]+)";
    private static final Pattern PATTERN = Pattern.compile(REGEX);

    @Getter
    private final String originalListener;
    @Getter
    private final String listenerName;
    @Getter
    private final SecurityProtocol securityProtocol;
    @Getter
    private final String hostname;
    @Getter
    private final int port;
    @Getter
    private final boolean multiListener;

    public EndPoint(final String listener, final Map<String, SecurityProtocol> protocolMap) {
        this.originalListener = listener;
        final String errorMessage = "listener '" + listener + "' is invalid";
        final Matcher matcher = matcherListener(listener, errorMessage);

        this.listenerName = matcher.group(1);
        if (protocolMap == null || protocolMap.isEmpty()) {
            multiListener = false;
            this.securityProtocol = SecurityProtocol.forName(matcher.group(1));
        } else {
            multiListener = true;
            this.securityProtocol = protocolMap.get(this.listenerName);
            if (this.securityProtocol == null) {
                throw new IllegalStateException(this.listenerName + " is not set in kafkaProtocolMap");
            }
        }

        final String originalHostname = matcher.group(2);
        if (originalHostname.isEmpty()) {
            try {
                this.hostname = InetAddress.getLocalHost().getCanonicalHostName();
            } catch (UnknownHostException e) {
                throw new IllegalStateException("hostname is empty and localhost is unknown: " + e.getMessage());
            }
        } else {
            this.hostname = originalHostname;
        }
        this.port = Integer.parseInt(matcher.group(3));
        checkState(port >= 0 && port <= 65535, errorMessage + ": port " + port + " is invalid");
    }

    public InetSocketAddress getInetAddress() {
        return new InetSocketAddress(hostname, port);
    }

    public static Map<String, EndPoint> parseListeners(final String listeners) {
        return parseListeners(listeners, null);
    }

    public static Map<String, EndPoint> parseListeners(final String listeners, final String kafkaProtocolMap) {
        final Map<String, EndPoint> endPointMap = new HashMap<>();
        final Map<String, SecurityProtocol> protocolMap = parseProtocolMap(kafkaProtocolMap);
        for (String listener : listeners.split(END_POINT_SEPARATOR)) {
            final EndPoint endPoint = new EndPoint(listener, protocolMap);
            if (endPointMap.containsKey(endPoint.listenerName)) {
                throw new IllegalStateException(
                        listeners + " has multiple listeners whose listenerName is " + endPoint.listenerName);
            } else {
                endPointMap.put(endPoint.listenerName, endPoint);
            }
        }
        return endPointMap;
    }

    public static EndPoint getPlainTextEndPoint(final String listeners) {
        for (String listener : listeners.split(END_POINT_SEPARATOR)) {
            if (listener.startsWith(SecurityProtocol.PLAINTEXT.name())
                    || listener.startsWith(SecurityProtocol.SASL_PLAINTEXT.name())) {
                return new EndPoint(listener, null);
            }
        }
        throw new IllegalStateException(listeners + " has no plain text endpoint");
    }

    public static EndPoint getSslEndPoint(final String listeners) {
        for (String listener : listeners.split(END_POINT_SEPARATOR)) {
            if (listener.startsWith(SecurityProtocol.SSL.name())
                    || listener.startsWith(SecurityProtocol.SASL_SSL.name())) {
                return new EndPoint(listener, null);
            }
        }
        throw new IllegalStateException(listeners + " has no ssl endpoint");
    }

    public static Map<String, SecurityProtocol> parseProtocolMap(final String kafkaProtocolMap) {

        final Map<String, SecurityProtocol> protocolMap = new HashMap<>();
        if (kafkaProtocolMap == null) {
            return protocolMap;
        }

        for (String protocolSet : kafkaProtocolMap.split(PROTOCOL_MAP_SEPARATOR)) {
            String[] protocol = protocolSet.split(PROTOCOL_SEPARATOR);
            if (protocol.length != 2) {
                throw new IllegalStateException(
                        "wrong format for kafkaProtocolMap " + kafkaProtocolMap);
            }
            if (protocolMap.containsKey(protocol[0])) {
                throw new IllegalStateException(
                        kafkaProtocolMap + " has multiple listeners whose listenerName is " + protocol[0]);
            }
            protocolMap.put(protocol[0], SecurityProtocol.forName(protocol[1]));
        }
        return protocolMap;
    }

    public static Matcher matcherListener(String listener, String errorMessage) {
        final Matcher matcher = PATTERN.matcher(listener);
        checkState(matcher.find(), errorMessage);
        checkState(matcher.groupCount() == 3, errorMessage);
        return matcher;
    }

}
