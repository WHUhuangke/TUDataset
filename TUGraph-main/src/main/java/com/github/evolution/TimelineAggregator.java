package com.github.evolution;

import com.github.model.KnowledgeGraph;
import com.github.model.Node;
import com.github.model.VersionStatus;
import com.github.model.EvolutionEdge;
import com.github.model.Edge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 将多个演化图谱合并为一个时间线图谱。
 */
public class TimelineAggregator {

    private final KnowledgeGraph aggregatedGraph = new KnowledgeGraph();
    private final Map<String, Integer> versionOrder = new HashMap<>();
    private final Map<String, String> commitToLabel = new HashMap<>();
    private final Map<String, String> edgeSignatureIndex = new HashMap<>();

    public TimelineAggregator(List<TimelineVersion> timeline) {
        for (int i = 0; i < timeline.size(); i++) {
            TimelineVersion version = timeline.get(i);
            versionOrder.put(version.getLabel(), version.getOrderIndex());
            commitToLabel.put(version.getCommitId(), version.getLabel());
            commitToLabel.put(version.getLabel(), version.getLabel());
        }
        if (!timeline.isEmpty()) {
            aggregatedGraph.setFromVersion(timeline.get(0).getLabel());
            aggregatedGraph.setToVersion(timeline.get(timeline.size() - 1).getLabel());
        }
    }

    public void addGraph(KnowledgeGraph graph) {
        if (graph == null) {
            return;
        }

        graph.getAllNodes().forEach(this::mergeNode);
        graph.getAllEdges().forEach(this::mergeStructuralEdge);
        graph.getAllEvolutionEdges().forEach(edge -> {
            normalizeEvolutionEdge(edge);
            mergeEvolutionEdge(edge);
        });

    }

    public KnowledgeGraph getAggregatedGraph() {
        return aggregatedGraph;
    }

    private void mergeNode(Node incoming) {
        Node existing = aggregatedGraph.getNode(incoming.getId());
        if (existing == null) {
            normalizeNodeVersions(incoming);
            aggregatedGraph.addNode(incoming);
            return;
        }

        normalizeNodeVersions(incoming);
        incoming.getVersions().forEach(version -> existing.addVersion(normalizeVersion(version)));

        VersionStatus mergedStatus = chooseStatus(existing.getVersionStatus(), incoming.getVersionStatus());
        existing.setVersionStatus(mergedStatus);

        String firstVersion = pickExtreme(
                normalizeVersion(existing.getFirstVersion()),
                normalizeVersion(incoming.getFirstVersion()),
                true);
        if (firstVersion != null) {
            existing.setFirstVersion(firstVersion);
        }

        String lastVersion = pickExtreme(
                normalizeVersion(existing.getLastVersion()),
                normalizeVersion(incoming.getLastVersion()),
                false);
        if (lastVersion != null) {
            existing.setLastVersion(lastVersion);
        }
    }

    private void mergeStructuralEdge(Edge edge) {
        String signature = buildEdgeSignature(edge);
        String existingId = edgeSignatureIndex.get(signature);
        if (existingId != null) {
            return;
        }

        aggregatedGraph.addEdge(edge);
        edgeSignatureIndex.put(signature, edge.getId());
    }

    private void mergeEvolutionEdge(EvolutionEdge edge) {
        EvolutionEdge existing = aggregatedGraph.getEvolutionEdge(edge.getId());
        if (existing == null) {
            aggregatedGraph.addEvolutionEdge(edge);
            return;
        }

        // 如果两条边的 ID 一致，说明是同一演化事件；保留最高置信度并合并描述
        if (edge.getConfidence() > existing.getConfidence()) {
            existing.setConfidence(edge.getConfidence());
            existing.setDescription(edge.getDescription());
        }
    }

    private VersionStatus chooseStatus(VersionStatus current, VersionStatus candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null) {
            return candidate;
        }

        int currentPriority = statusPriority(current);
        int candidatePriority = statusPriority(candidate);
        return candidatePriority > currentPriority ? candidate : current;
    }

    private int statusPriority(VersionStatus status) {
        if (status == null) {
            return 0;
        }
        switch (status) {
            case ADDED:
            case DELETED:
                return 3;
            case MODIFIED:
                return 2;
            case UNCHANGED:
            default:
                return 1;
        }
    }

    private String pickExtreme(String current, String candidate, boolean pickFirst) {
        if (candidate == null || candidate.isBlank()) {
            return current;
        }
        if (current == null || current.isBlank()) {
            return candidate;
        }

        int currentOrder = versionOrder.getOrDefault(current, pickFirst ? Integer.MAX_VALUE : Integer.MIN_VALUE);
        int candidateOrder = versionOrder.getOrDefault(candidate, pickFirst ? Integer.MAX_VALUE : Integer.MIN_VALUE);

        if (pickFirst) {
            return candidateOrder < currentOrder ? candidate : current;
        } else {
            return candidateOrder > currentOrder ? candidate : current;
        }
    }

    private void normalizeNodeVersions(Node node) {
        Set<String> normalized = node.getVersions().stream()
                .map(this::normalizeVersion)
                .collect(Collectors.toSet());
        node.replaceVersions(normalized);

        String first = normalizeVersion(node.getFirstVersion());
        if (first != null) {
            node.setFirstVersion(first);
        }
        String last = normalizeVersion(node.getLastVersion());
        if (last != null) {
            node.setLastVersion(last);
        }
    }

    private String normalizeVersion(String version) {
        if (version == null) {
            return null;
        }
        return commitToLabel.getOrDefault(version, version);
    }

    private String buildEdgeSignature(Edge edge) {
        return edge.getSourceId() + "|" + edge.getEdgeType() + "|" + edge.getTargetId();
    }

    private void normalizeEvolutionEdge(EvolutionEdge edge) {
        String normalizedFrom = normalizeVersion(edge.getFromVersion());
        String normalizedTo = normalizeVersion(edge.getToVersion());

        if (normalizedFrom != null && !normalizedFrom.equals(edge.getFromVersion())) {
            edge.setFromVersion(normalizedFrom);
        }
        if (normalizedTo != null && !normalizedTo.equals(edge.getToVersion())) {
            edge.setToVersion(normalizedTo);
        }
    }
}
