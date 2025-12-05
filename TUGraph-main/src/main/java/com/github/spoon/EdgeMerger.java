package com.github.spoon;

import com.github.logging.GraphLogger;
import com.github.model.Edge;
import com.github.model.edges.CallsEdge;
import com.github.model.edges.ReadsEdge;
import com.github.model.edges.WritesEdge;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 边合并器
 * 负责合并重复的边关系，将多次调用/读取/写入的信息聚合到一条边中
 * 
 * 合并策略：
 * - 按 (sourceId, targetId, edgeType) 分组
 * - 保留所有位置信息
 * - 统计调用/访问次数
 * - 生成语义摘要
 */
public class EdgeMerger {
    
    private static final GraphLogger logger = GraphLogger.getInstance();
    
    /**
     * 合并边列表
     * @param edges 原始边列表
     * @return 合并后的边列表
     */
    public <T extends Edge> List<T> mergeEdges(List<T> edges) {
        if (edges == null || edges.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 按 EdgeKey 分组
        Map<EdgeKey, List<T>> grouped = edges.stream()
            .collect(Collectors.groupingBy(
                edge -> new EdgeKey(
                    edge.getSourceId(),
                    edge.getTargetId(),
                    edge.getEdgeType()
                )
            ));
        
        // 对每组边进行合并
        List<T> mergedEdges = grouped.values().stream()
            .map(this::mergeEdgeGroup)
            .collect(Collectors.toList());
        
        // 记录合并统计
        if (edges.size() > mergedEdges.size()) {
            int reduction = edges.size() - mergedEdges.size();
            double reductionPercent = (double) reduction / edges.size() * 100;
            logger.debug(String.format(
                "Merged %d edges into %d (reduced %d, %.1f%%)",
                edges.size(), mergedEdges.size(), reduction, reductionPercent
            ));
        }
        
        return mergedEdges;
    }
    
    /**
     * 合并同一组的边
     */
    @SuppressWarnings("unchecked")
    private <T extends Edge> T mergeEdgeGroup(List<T> edgeGroup) {
        if (edgeGroup.size() == 1) {
            // 单条边，设置默认计数为1
            T edge = edgeGroup.get(0);
            setDefaultCountProperties(edge);
            return edge;
        }
        
        // 多条边，根据类型进行合并
        T firstEdge = edgeGroup.get(0);
        
        if (firstEdge instanceof CallsEdge) {
            return (T) mergeCallsEdges((List<CallsEdge>) edgeGroup);
        } else if (firstEdge instanceof ReadsEdge) {
            return (T) mergeReadsEdges((List<ReadsEdge>) edgeGroup);
        } else if (firstEdge instanceof WritesEdge) {
            return (T) mergeWritesEdges((List<WritesEdge>) edgeGroup);
        }
        
        // 其他边类型不需要合并（如 Extends, Implements 等）
        return firstEdge;
    }
    
    /**
     * 为单条边设置默认计数属性
     */
    private void setDefaultCountProperties(Edge edge) {
        if (edge instanceof CallsEdge) {
            CallsEdge ce = (CallsEdge) edge;
            ce.setProperty("callCount", 1);
            ce.setProperty("callLocations", Arrays.asList(ce.getLineNumber()));
            ce.setProperty("firstCallLine", ce.getLineNumber());
            ce.setProperty("lastCallLine", ce.getLineNumber());
        } else if (edge instanceof ReadsEdge) {
            ReadsEdge re = (ReadsEdge) edge;
            re.setProperty("readCount", 1);
            re.setProperty("readLocations", Arrays.asList(re.getLineNumber()));
        } else if (edge instanceof WritesEdge) {
            WritesEdge we = (WritesEdge) edge;
            we.setProperty("writeCount", 1);
            we.setProperty("writeLocations", Arrays.asList(we.getLineNumber()));
        }
    }
    
    /**
     * 合并 CALLS 边
     */
    private CallsEdge mergeCallsEdges(List<CallsEdge> edges) {
        // 使用第一条边作为基础
        CallsEdge merged = edges.get(0);
        
        // 收集所有调用位置（排序并去重）
        List<Integer> allLocations = edges.stream()
            .map(CallsEdge::getLineNumber)
            .distinct()
            .sorted()
            .collect(Collectors.toList());
        
        // 设置聚合属性
        merged.setProperty("callCount", edges.size());
        merged.setProperty("callLocations", allLocations);
        merged.setProperty("firstCallLine", allLocations.get(0));
        merged.setProperty("lastCallLine", allLocations.get(allLocations.size() - 1));
        
        // 收集所有调用类型（可能有 DIRECT, VIRTUAL, STATIC 等）
        Set<String> callTypes = edges.stream()
            .map(e -> (String) e.getProperty("callType"))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (!callTypes.isEmpty()) {
            merged.setProperty("callTypes", new ArrayList<>(callTypes));
        }
        
        // 生成描述
        String description = generateCallDescription(edges.size(), allLocations);
        merged.setDescription(description);
        
        // 生成语义摘要
        String summary = generateCallSemanticSummary(edges.size());
        merged.setProperty("semanticSummary", summary);
        
        // 如果是高频调用，标记为热点
        if (edges.size() >= 10) {
            merged.setProperty("isHotspot", true);
        }
        
        return merged;
    }
    
    /**
     * 合并 READS 边
     */
    private ReadsEdge mergeReadsEdges(List<ReadsEdge> edges) {
        ReadsEdge merged = edges.get(0);
        
        // 收集所有读取位置
        List<Integer> allLocations = edges.stream()
            .map(ReadsEdge::getLineNumber)
            .distinct()
            .sorted()
            .collect(Collectors.toList());
        
        // 设置聚合属性
        merged.setProperty("readCount", edges.size());
        merged.setProperty("readLocations", allLocations);
        merged.setProperty("firstReadLine", allLocations.get(0));
        merged.setProperty("lastReadLine", allLocations.get(allLocations.size() - 1));
        
        // 收集访问类型
        Set<String> accessTypes = edges.stream()
            .map(e -> (String) e.getProperty("accessType"))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (!accessTypes.isEmpty()) {
            merged.setProperty("accessTypes", new ArrayList<>(accessTypes));
        }
        
        // 生成描述
        String description = String.format(
            "Field read %d time%s at line%s %s",
            edges.size(),
            edges.size() > 1 ? "s" : "",
            edges.size() > 1 ? "s" : "",
            formatLocations(allLocations)
        );
        merged.setDescription(description);
        
        // 生成语义摘要
        String summary = edges.size() > 5 ? 
            "High frequency field access" : 
            "Normal field access";
        merged.setProperty("semanticSummary", summary);
        
        return merged;
    }
    
    /**
     * 合并 WRITES 边
     */
    private WritesEdge mergeWritesEdges(List<WritesEdge> edges) {
        WritesEdge merged = edges.get(0);
        
        // 收集所有写入位置
        List<Integer> allLocations = edges.stream()
            .map(WritesEdge::getLineNumber)
            .distinct()
            .sorted()
            .collect(Collectors.toList());
        
        // 设置聚合属性
        merged.setProperty("writeCount", edges.size());
        merged.setProperty("writeLocations", allLocations);
        merged.setProperty("firstWriteLine", allLocations.get(0));
        merged.setProperty("lastWriteLine", allLocations.get(allLocations.size() - 1));
        
        // 收集访问类型
        Set<String> accessTypes = edges.stream()
            .map(e -> (String) e.getProperty("accessType"))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (!accessTypes.isEmpty()) {
            merged.setProperty("accessTypes", new ArrayList<>(accessTypes));
        }
        
        // 生成描述
        String description = String.format(
            "Field written %d time%s at line%s %s",
            edges.size(),
            edges.size() > 1 ? "s" : "",
            edges.size() > 1 ? "s" : "",
            formatLocations(allLocations)
        );
        merged.setDescription(description);
        
        // 生成语义摘要
        String summary = edges.size() > 5 ? 
            "High frequency field modification" : 
            "Normal field modification";
        merged.setProperty("semanticSummary", summary);
        
        return merged;
    }
    
    /**
     * 生成调用描述
     */
    private String generateCallDescription(int count, List<Integer> locations) {
        if (count == 1) {
            return String.format("Called once at line %d", locations.get(0));
        } else if (count <= 3) {
            return String.format("Called %d times at lines %s", 
                count, formatLocations(locations));
        } else {
            return String.format("Called %d times at lines %s, ...", 
                count, formatLocations(locations.subList(0, Math.min(3, locations.size()))));
        }
    }
    
    /**
     * 生成调用语义摘要
     */
    private String generateCallSemanticSummary(int count) {
        if (count == 1) {
            return "Single call relationship";
        } else if (count <= 3) {
            return "Low frequency call";
        } else if (count <= 10) {
            return "Medium frequency call";
        } else if (count <= 20) {
            return "High frequency call";
        } else {
            return "Very high frequency call (potential hotspot)";
        }
    }
    
    /**
     * 格式化位置列表
     */
    private String formatLocations(List<Integer> locations) {
        if (locations.size() <= 5) {
            return locations.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(", "));
        } else {
            // 只显示前3个和后2个
            List<String> parts = new ArrayList<>();
            parts.addAll(locations.subList(0, 3).stream()
                .map(String::valueOf)
                .collect(Collectors.toList()));
            parts.add("...");
            parts.addAll(locations.subList(locations.size() - 2, locations.size()).stream()
                .map(String::valueOf)
                .collect(Collectors.toList()));
            return String.join(", ", parts);
        }
    }
    
    /**
     * 边的唯一标识键
     */
    private static class EdgeKey {
        private final String sourceId;
        private final String targetId;
        private final String edgeType;
        
        public EdgeKey(String sourceId, String targetId, String edgeType) {
            this.sourceId = sourceId;
            this.targetId = targetId;
            this.edgeType = edgeType;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof EdgeKey)) return false;
            EdgeKey edgeKey = (EdgeKey) o;
            return Objects.equals(sourceId, edgeKey.sourceId) &&
                   Objects.equals(targetId, edgeKey.targetId) &&
                   Objects.equals(edgeType, edgeKey.edgeType);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(sourceId, targetId, edgeType);
        }
    }
}
