package com.pfe.factory.ports;

import com.pfe.factory.domain.Model;
import java.util.List;
import java.util.UUID;

public interface ScmClient {
    Model.PrRef createDraftPr(UUID runId, String branch, String title, String body,
                              List<Model.GeneratedFile> files);
}
