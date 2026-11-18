package com.pfe.factory.ports;

import com.pfe.factory.domain.Model;
import java.util.List;
import java.util.UUID;

public interface CheckClient {
    Model.CheckResult applyAndCheck(UUID runId, List<Model.GeneratedFile> files);
}
