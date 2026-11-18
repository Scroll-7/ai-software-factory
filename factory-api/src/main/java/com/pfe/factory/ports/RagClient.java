package com.pfe.factory.ports;

import com.pfe.factory.domain.Model;
import java.util.List;

public interface RagClient {
    List<Model.Chunk> search(String query, String kind, int k);
}
