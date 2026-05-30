package com.myworld.modules.fraud.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Service
public class IpIntelligenceService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.ipinfo.token:}")
    private String ipinfoToken;

    private final Cache<String, Boolean> vpnProxyCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(6))
            .maximumSize(1000)
            .build();

    public boolean isVpnOrProxy(String ip) {
        if (ipinfoToken == null || ipinfoToken.isBlank()) {
            return false;
        }

        return vpnProxyCache.get(ip, this::fetchFromIpinfo);
    }

    private boolean fetchFromIpinfo(String ip) {
        try {
            String url = "https://ipinfo.io/" + ip + "/json?token=" + ipinfoToken;
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> privacy = (Map<String, Object>) response.get("privacy");
                if (privacy != null) {
                    Boolean isVpn = (Boolean) privacy.get("vpn");
                    Boolean isProxy = (Boolean) privacy.get("proxy");
                    return (Boolean.TRUE.equals(isVpn)) || (Boolean.TRUE.equals(isProxy));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to check IP intelligence for {}: {}", ip, e.getMessage());
        }
        return false;
    }
}
