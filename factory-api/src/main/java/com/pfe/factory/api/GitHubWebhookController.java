package com.pfe.factory.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.factory.audit.AuditService;
import com.pfe.factory.config.FactoryProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HexFormat;

/**
 * Webhook GitHub LOCAL : vérifie la signature HMAC-SHA256 et trace l'événement dans audit_log.
 * Aucun appel sortant, aucun secret stocké, aucune fusion automatique.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
public class GitHubWebhookController {
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final String secret;

    public GitHubWebhookController(AuditService audit, ObjectMapper mapper, FactoryProperties props) {
        this.audit = audit;
        this.mapper = mapper;
        this.secret = props.github().webhookSecret();
    }

    @PostMapping("/github")
    public ResponseEntity<Map<String, Object>> receive(
            @RequestHeader(value = "X-GitHub-Event", required = false) String event,
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String delivery,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody byte[] body) {

        if (secret == null || secret.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "secret webhook non configuré"));
        }
        if (signature == null || !signature.startsWith("sha256=")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "signature manquante"));
        }
        String expected = "sha256=" + hmacHex(secret, body);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "signature invalide"));
        }

        String eventName = event == null ? "unknown" : event;
        String deliveryId = delivery == null ? "unknown" : delivery;
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("event", eventName);
        try {
            JsonNode payload = mapper.readTree(body);
            JsonNode pr = payload.path("pull_request");
            if (pr.isObject()) {
                metadata.put("action", payload.path("action").asText());
                metadata.put("number", pr.path("number").asInt());
                metadata.put("draft", pr.path("draft").asBoolean());
                metadata.put("url", pr.path("html_url").asText());
                metadata.put("head", pr.path("head").path("ref").asText());
            }
            metadata.put("repository", payload.path("repository").path("full_name").asText());
        } catch (Exception ignored) {
            metadata.put("payload", "illisible");
        }
        audit.append(null, "github-webhook", "GITHUB_WEBHOOK", deliveryId, metadata);
        return ResponseEntity.ok(Map.of("recorded", true, "event", eventName));
    }

    private static String hmacHex(String key, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC impossible", e);
        }
    }
}
