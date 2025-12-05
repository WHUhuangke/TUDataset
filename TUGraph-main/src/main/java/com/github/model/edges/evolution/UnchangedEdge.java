package com.github.model.edges.evolution;

import com.github.model.EvolutionEdge;

/**
 * 表示两个版本之间未发生变化的演化边。
 */
public class UnchangedEdge extends EvolutionEdge {

    public UnchangedEdge(String fromNodeId, String toNodeId, String fromVersion, String toVersion) {
        super(fromNodeId, toNodeId, fromVersion, toVersion);
        setDescription("No change detected between versions");
        setConfidence(1.0);
    }

    @Override
    public String getEdgeType() {
        return "UNCHANGED";
    }
}
