package com.pfe.factory.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.factory.util.Hashes;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Journal append-only avec chaîne de hachage. Les métadonnées ne contiennent jamais de texte brut. */
@Service
public class AuditService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AuditService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void append(UUID runId, String actor, String action, String resource, Object metadata) {
        try {
            String prev = jdbc.query(
                    "select hash from audit_log order by id desc limit 1",
                    rs -> rs.next() ? rs.getString(1) : null);
            String json = mapper.writeValueAsString(metadata == null ? Map.of() : metadata);
            Instant now = Instant.now();
            String hash = Hashes.sha256((prev == null ? "" : prev) + action + resource + json + now);
            jdbc.update("insert into audit_log(run_id,actor,action,resource,metadata,prev_hash,hash,created_at) "
                            + "values(?,?,?,?,?::jsonb,?,?,?)",
                    runId, actor, action, resource, json, prev, hash, Timestamp.from(now));
        } catch (Exception e) {
            throw new IllegalStateException("écriture audit impossible", e);
        }
    }
}
