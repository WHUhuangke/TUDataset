package com.github.evolution;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 记录通过差异分析识别出的节点变更集合。
 * 用于在合并阶段强制将特定节点视为“已修改”。
 */
public class DiffChangeSet {

    private final Set<String> changedV1NodeIds = new HashSet<>();
    private final Set<String> changedV2NodeIds = new HashSet<>();

    public void addV1Node(String nodeId) {
        if (nodeId != null && !nodeId.isBlank()) {
            changedV1NodeIds.add(nodeId);
        }
    }

    public void addV2Node(String nodeId) {
        if (nodeId != null && !nodeId.isBlank()) {
            changedV2NodeIds.add(nodeId);
        }
    }

    public boolean isChanged(String v1NodeId, String v2NodeId) {
        return (v1NodeId != null && changedV1NodeIds.contains(v1NodeId)) ||
               (v2NodeId != null && changedV2NodeIds.contains(v2NodeId));
    }

    public Set<String> getChangedV1NodeIds() {
        return Collections.unmodifiableSet(changedV1NodeIds);
    }

    public Set<String> getChangedV2NodeIds() {
        return Collections.unmodifiableSet(changedV2NodeIds);
    }

    public boolean isEmpty() {
        return changedV1NodeIds.isEmpty() && changedV2NodeIds.isEmpty();
    }
}
