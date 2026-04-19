package com.minipaas.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.InetAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Creates or updates Cloudflare {@code A} records so app hostnames resolve to the ingress IP.
 * Cloudflare proxied (orange cloud) only allows public routable targets; Tailscale
 * ({@code 100.64.0.0/10}) and RFC1918 addresses are forced to {@code proxied: false} (DNS only).
 */
@Service
@Slf4j
public class CloudflareDnsService {

    private static final String API_BASE = "https://api.cloudflare.com/client/v4";

    private final ObjectMapper objectMapper = new ObjectMapper();
    // private final RestTemplate restTemplate = new RestTemplate();
    private final RestTemplate restTemplate = new RestTemplate(new JdkClientHttpRequestFactory());

    @Value("${app.cloudflare.enabled:false}")
    private boolean enabled;

    @Value("${app.cloudflare.api-token:}")
    private String apiToken;

    @Value("${app.cloudflare.zone-id:}")
    private String zoneId;

    @Value("${app.cloudflare.proxied:true}")
    private boolean proxied;

    public boolean isEnabled() {
        return enabled && apiToken != null && !apiToken.isBlank()
                && zoneId != null && !zoneId.isBlank();
    }

    /** Ensures an {@code A} record for {@code fqdn} points to {@code ipv4}. */
    public void upsertARecord(String fqdn, String ipv4) {
        if (!isEnabled()) {
            return;
        }
        if (fqdn == null || fqdn.isBlank() || ipv4 == null || ipv4.isBlank()) {
            log.warn("Cloudflare DNS skipped: empty fqdn or IP");
            return;
        }

        String recordId = findARecordId(fqdn);
        HttpHeaders headers = authHeaders();
        boolean effectiveProxied = proxied && isEligibleForCloudflareProxy(ipv4);
        if (proxied && !effectiveProxied) {
            log.info(
                    "Cloudflare: IP {} is private/CGNAT (e.g. Tailscale) — using DNS-only (proxied=false). "
                            + "For orange cloud use a public ingress IP; for public Internet use an IP reachable from clients.",
                    ipv4);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("type", "A");
        body.put("name", fqdn);
        body.put("content", ipv4);
        body.put("ttl", 1);
        body.put("proxied", effectiveProxied);

        HttpEntity<String> entity;
        try {
            entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        } catch (Exception e) {
            throw new RuntimeException("Cloudflare: failed to build JSON body", e);
        }

        String url = recordId == null
                ? API_BASE + "/zones/" + zoneId + "/dns_records"
                : API_BASE + "/zones/" + zoneId + "/dns_records/" + recordId;

        HttpMethod method = recordId == null ? HttpMethod.POST : HttpMethod.PATCH;
        ResponseEntity<String> response = restTemplate.exchange(url, method, entity, String.class);
        assertCloudflareOk(response.getBody(), method + " dns_records");
        log.info("Cloudflare DNS {} → {} ({})", recordId == null ? "created" : "updated", fqdn, ipv4);
    }

    private String findARecordId(String fqdn) {
        HttpHeaders headers = authHeaders();
        URI listUri = UriComponentsBuilder
                .fromUriString(API_BASE + "/zones/" + zoneId + "/dns_records")
                .queryParam("type", "A")
                .queryParam("name", fqdn)
                .build()
                .toUri();

        HttpEntity<Void> getEntity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(listUri, HttpMethod.GET, getEntity, String.class);
        assertCloudflareOk(response.getBody(), "GET dns_records");

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode result = root.path("result");
            if (result.isArray() && !result.isEmpty()) {
                return result.get(0).path("id").asText(null);
            }
        } catch (Exception e) {
            log.warn("Cloudflare: could not parse DNS list: {}", e.getMessage());
        }
        return null;
    }

    private void assertCloudflareOk(String jsonBody, String action) {
        if (jsonBody == null) {
            throw new RuntimeException("Cloudflare " + action + ": empty body");
        }
        try {
            JsonNode root = objectMapper.readTree(jsonBody);
            if (!root.path("success").asBoolean(false)) {
                String msg = root.path("errors").toString();
                throw new RuntimeException("Cloudflare " + action + " failed: " + msg);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Cloudflare " + action + ": invalid response", e);
        }
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /**
     * Cloudflare returns 9003 if {@code proxied} is true for non-public targets (RFC1918, CGNAT / Tailscale {@code 100.64.0.0/10}, etc.).
     */
    static boolean isEligibleForCloudflareProxy(String ipv4) {
        if (ipv4 == null || ipv4.isBlank()) {
            return false;
        }
        try {
            byte[] addr = InetAddress.getByName(ipv4.trim()).getAddress();
            if (addr.length != 4) {
                return false;
            }
            int a = addr[0] & 0xff;
            int b = addr[1] & 0xff;
            if (a == 10) {
                return false;
            }
            if (a == 172 && b >= 16 && b <= 31) {
                return false;
            }
            if (a == 192 && b == 168) {
                return false;
            }
            if (a == 127) {
                return false;
            }
            if (a == 169 && b == 254) {
                return false;
            }
            // RFC 6598 shared / CGNAT space — includes Tailscale 100.x
            if (a == 100 && b >= 64 && b <= 127) {
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("Cloudflare: could not parse IPv4 '{}': {}", ipv4, e.getMessage());
            return false;
        }
    }
}