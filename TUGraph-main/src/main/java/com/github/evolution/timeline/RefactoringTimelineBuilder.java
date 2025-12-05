package com.github.evolution.timeline;

import com.github.logging.GraphLogger;
import com.github.refactoring.RefactoringDetector;
import com.github.refactoring.RefactoringInfo;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.util.*;

/**
 * 基于RefactoringMiner的智能时间线构建器（简化版）
 * 
 * <p>核心特点：
 * <ul>
 *   <li>✅ 动态追踪更新 - 处理重命名、移动等重构，保证不漏掉相关commit</li>
 *   <li>✅ 性能优化 - 支持批量分析模式，避免重复调用</li>
 *   <li>✅ 简洁有效 - 使用简单的并集合并策略，降低复杂度</li>
 *   <li>✅ 直接匹配 - 基于方法签名的直接匹配，保持高效</li>
 * </ul>
 * 
 * <p><b>工作原理：</b>
 * <ol>
 *   <li>分析目标commit，提取变更的方法</li>
 *   <li>向前遍历历史，使用RefactoringMiner检测重构</li>
 *   <li>动态更新追踪列表（处理RENAME/MOVE/EXTRACT/INLINE等）</li>
 *   <li>只保留涉及追踪方法的commit</li>
 * </ol>
 * 
 * <p><b>使用示例：</b>
 * <pre>{@code
 * RefactoringTimelineBuilder builder = new RefactoringTimelineBuilder(repoPath);
 * builder.setMaxDepth(50);
 * builder.setMaxDays(180);
 * 
 * TimelineResult result = builder.buildTimeline("commit-hash");
 * List<String> timeline = result.getCommits();
 * }</pre>
 * 
 * @author TUGraph Team
 * @since 2.2.0
 */
public class RefactoringTimelineBuilder {
    
    private final String repoPath;
    private final RefactoringDetector refactoringDetector;
    private final GraphLogger logger;
    
    // 配置参数
    private int maxDepth = 50;
    private int maxDays = 180;
    
    public RefactoringTimelineBuilder(String repoPath) {
        this.repoPath = repoPath;
        this.refactoringDetector = new RefactoringDetector();
        this.logger = GraphLogger.getInstance();
    }
    
    /**
     * 构建时间线（主入口）
     * 
     * @param targetCommit 目标commit hash
     * @return 时间线结果
     * @throws Exception 如果构建失败
     */
    public TimelineResult buildTimeline(String targetCommit) throws Exception {
        logger.info("========================================");
        logger.info("构建重构驱动的时间线");
        logger.info("目标Commit: " + targetCommit);
        logger.info("最大深度: " + maxDepth + ", 时间窗口: " + maxDays + "天");
        logger.info("========================================");
        
        long startTime = System.currentTimeMillis();
        
        TimelineResult result = buildTimelineIncremental(targetCommit);
        
        long elapsed = System.currentTimeMillis() - startTime;
        logger.info("✓ 时间线构建完成: " + result.getCommits().size() + " 个commits");
        logger.info("  耗时: " + String.format("%.2f", elapsed / 1000.0) + " 秒");
        logger.info("========================================");
        
        return result;
    }
    
    /**
     * 增量模式构建时间线
     */
    private TimelineResult buildTimelineIncremental(String targetCommit) throws Exception {
        // Step 1: 分析目标commit，获取变更的方法
        String parentCommit = getParentCommit(targetCommit);
        if (parentCommit == null) {
            throw new IllegalArgumentException("目标commit没有父commit: " + targetCommit);
        }
        
        logger.info("分析目标commit的变更...");
        List<RefactoringInfo> targetRefactorings = refactoringDetector.detectRefactorings(
            repoPath, parentCommit, targetCommit
        );
        
        Set<String> trackedMethods = extractMethods(targetRefactorings);
        logger.info("✓ 识别到 " + trackedMethods.size() + " 个变更的方法");
        
        if (trackedMethods.isEmpty()) {
            logger.warn("目标commit没有方法级别的变更，返回单节点时间线");
            return TimelineResult.single(targetCommit);
        }
        
        // 打印追踪的方法（前5个）
        int count = 0;
        for (String method : trackedMethods) {
            if (count++ < 5) {
                logger.debug("  追踪: " + method);
            }
        }
        if (trackedMethods.size() > 5) {
            logger.debug("  ... 还有 " + (trackedMethods.size() - 5) + " 个方法");
        }
        
        // Step 2: 向前遍历构建时间线
        List<CommitNode> timeline = new ArrayList<>();
        timeline.add(new CommitNode(
            targetCommit,
            getCommitInfo(targetCommit),
            new HashSet<>(trackedMethods),
            targetRefactorings
        ));
        
        // 获取目标commit的时间作为参照点
        CommitInfo targetCommitInfo = getCommitInfo(targetCommit);
        long targetCommitTime = targetCommitInfo.getCommitTime();
        
        Set<String> currentTracking = new HashSet<>(trackedMethods);
        String currentCommit = parentCommit;
        int depth = 0;
        int totalAnalyzed = 0;
        int relevantCount = 0;
        
        logger.info("");
        logger.info("开始向前回溯...");
        
        // 向前遍历
        while (depth < maxDepth && currentCommit != null) {
            String commitParent = getParentCommit(currentCommit);
            if (commitParent == null) {
                logger.info("到达仓库起点");
                break;
            }
            
            // 时间窗口检查（相对于目标commit）
            if (exceedsTimeWindow(currentCommit, targetCommitTime, maxDays)) {
                logger.info("超出时间窗口(" + maxDays + "天)，停止回溯");
                break;
            }
            
            totalAnalyzed++;
            
            // 检测重构：只检测 currentCommit 这个提交本身的重构
            List<RefactoringInfo> refactorings = refactoringDetector.detectRefactoringsInCommit(
                repoPath, currentCommit
            );
            
            // 检查相关性
            List<RefactoringInfo> matchedRefactorings = findMatchedRefactorings(
                refactorings, 
                currentTracking
            );
            
            if (!matchedRefactorings.isEmpty()) {
                // 相关！加入时间线
                timeline.add(new CommitNode(
                    currentCommit,
                    getCommitInfo(currentCommit),
                    new HashSet<>(currentTracking),
                    matchedRefactorings
                ));
                
                relevantCount++;
                
                // 动态更新追踪列表（核心！）
                Set<String> oldTracking = new HashSet<>(currentTracking);
                currentTracking = updateTracking(currentTracking, matchedRefactorings);
                
                String shortCommit = currentCommit.substring(0, Math.min(7, currentCommit.length()));
                logger.info(String.format("✓ %s 相关 (%d 重构, %d→%d 方法)", 
                    shortCommit, 
                    matchedRefactorings.size(),
                    oldTracking.size(),
                    currentTracking.size()
                ));
            }
            
            currentCommit = commitParent;
            depth++;
        }
        
        logger.info("");
        logger.info("回溯完成:");
        logger.info("  总共分析: " + totalAnalyzed + " 个commits");
        logger.info("  找到相关: " + relevantCount + " 个commits");
        logger.info("  过滤比例: " + String.format("%.1f%%", 
            (totalAnalyzed - relevantCount) * 100.0 / Math.max(1, totalAnalyzed)));
        
        // 反转时间线（变为时间正序）
        Collections.reverse(timeline);
        
        return new TimelineResult(timeline);
    }
    
    /**
     * 从重构信息中提取方法签名
     */
    private Set<String> extractMethods(List<RefactoringInfo> refactorings) {
        Set<String> methods = new HashSet<>();
        
        for (RefactoringInfo ref : refactorings) {
            // 从右侧（新版本/after）提取
            for (RefactoringInfo.CodeLocation loc : ref.getRightSideLocations()) {
                String element = loc.getCodeElement();
                if (isMethod(element)) {
                    methods.add(normalizeSignature(element));
                }
            }
        }
        
        return methods;
    }
    
    /**
     * 查找匹配的重构（涉及追踪方法的重构）
     */
    private List<RefactoringInfo> findMatchedRefactorings(
            List<RefactoringInfo> refactorings,
            Set<String> trackedMethods) {
        
        List<RefactoringInfo> matched = new ArrayList<>();
        
        for (RefactoringInfo ref : refactorings) {
            if (isRelevant(ref, trackedMethods)) {
                matched.add(ref);
            }
        }
        
        return matched;
    }
    
    /**
     * 判断重构是否相关（直接匹配策略）
     */
    private boolean isRelevant(RefactoringInfo ref, Set<String> trackedMethods) {
        // 检查右侧（新版本）
        for (RefactoringInfo.CodeLocation loc : ref.getRightSideLocations()) {
            if (containsMethod(trackedMethods, loc.getCodeElement())) {
                return true;
            }
        }
        
        // 检查左侧（旧版本）
        for (RefactoringInfo.CodeLocation loc : ref.getLeftSideLocations()) {
            if (containsMethod(trackedMethods, loc.getCodeElement())) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 动态更新追踪列表（核心方法！）
     * 
     * 根据重构类型更新追踪的方法：
     * - RENAME/MOVE/CHANGE: 用旧签名替换新签名
     * - EXTRACT: 添加源方法
     * - INLINE: 添加被内联的方法
     */
    private Set<String> updateTracking(
            Set<String> currentTracking,
            List<RefactoringInfo> matchedRefactorings) {
        
        Set<String> updated = new HashSet<>(currentTracking);
        
        for (RefactoringInfo ref : matchedRefactorings) {
            String type = ref.getType();
            
            if (type.contains("RENAME") || type.contains("MOVE") || type.contains("CHANGE")) {
                // 重命名/移动/签名变更：用旧的替换新的
                updateForTransformation(updated, ref, type);
            }
            else if (type.contains("EXTRACT")) {
                // 提取方法：添加源方法
                addSourceMethod(updated, ref);
            }
            else if (type.contains("INLINE")) {
                // 内联方法：添加被内联的方法
                addInlinedMethod(updated, ref);
            }
            // 其他类型的重构不需要更新追踪列表
        }
        
        return updated;
    }
    
    /**
     * 处理变换类重构（RENAME/MOVE/CHANGE）
     */
    private void updateForTransformation(Set<String> tracking, RefactoringInfo ref, String type) {
        String newMethod = extractMethod(ref.getRightSideLocations());
        String oldMethod = extractMethod(ref.getLeftSideLocations());
        
        if (newMethod != null && oldMethod != null) {
            String newSig = normalizeSignature(newMethod);
            String oldSig = normalizeSignature(oldMethod);
            
            if (tracking.contains(newSig)) {
                tracking.remove(newSig);
                tracking.add(oldSig);
                
                logger.debug(String.format("  [%s] 追踪更新: %s ← %s", 
                    type, shortSignature(oldSig), shortSignature(newSig)));
            }
        }
    }
    
    /**
     * 添加提取方法的源方法
     */
    private void addSourceMethod(Set<String> tracking, RefactoringInfo ref) {
        String extractedMethod = extractMethod(ref.getRightSideLocations());
        String sourceMethod = extractMethod(ref.getLeftSideLocations());
        
        if (extractedMethod != null && sourceMethod != null) {
            String extractedSig = normalizeSignature(extractedMethod);
            String sourceSig = normalizeSignature(sourceMethod);
            
            if (tracking.contains(extractedSig)) {
                tracking.add(sourceSig);
                logger.debug("  [EXTRACT] 添加源方法: " + shortSignature(sourceSig));
            }
        }
    }
    
    /**
     * 添加被内联的方法
     */
    private void addInlinedMethod(Set<String> tracking, RefactoringInfo ref) {
        String targetMethod = extractMethod(ref.getRightSideLocations());
        String inlinedMethod = extractMethod(ref.getLeftSideLocations());
        
        if (targetMethod != null && inlinedMethod != null) {
            String targetSig = normalizeSignature(targetMethod);
            String inlinedSig = normalizeSignature(inlinedMethod);
            
            if (tracking.contains(targetSig)) {
                tracking.add(inlinedSig);
                logger.debug("  [INLINE] 添加被内联方法: " + shortSignature(inlinedSig));
            }
        }
    }
    
    // ==================== 工具方法 ====================
    
    /**
     * 判断是否是方法
     */
    private boolean isMethod(String codeElement) {
        if (codeElement == null) {
            return false;
        }
        return codeElement.contains("(") && codeElement.contains(")");
    }
    
    /**
     * 检查追踪集合中是否包含某个方法
     */
    private boolean containsMethod(Set<String> trackedMethods, String codeElement) {
        if (!isMethod(codeElement)) {
            return false;
        }
        
        String normalized = normalizeSignature(codeElement);
        return trackedMethods.contains(normalized);
    }
    
    /**
     * 标准化方法签名
     */
    private String normalizeSignature(String signature) {
        if (signature == null) {
            return "";
        }
        return signature.trim();
    }
    
    /**
     * 从CodeLocation列表中提取方法签名
     */
    private String extractMethod(List<RefactoringInfo.CodeLocation> locations) {
        for (RefactoringInfo.CodeLocation loc : locations) {
            String element = loc.getCodeElement();
            if (isMethod(element)) {
                return element;
            }
        }
        return null;
    }
    
    /**
     * 获取方法的简短签名（用于日志）
     */
    private String shortSignature(String signature) {
        if (signature == null) {
            return "";
        }
        
        // 只保留类名和方法名，去掉包名和参数
        int lastDot = signature.lastIndexOf('.');
        int paren = signature.indexOf('(');
        
        if (lastDot >= 0 && paren > lastDot) {
            return signature.substring(lastDot + 1, paren) + "(...)";
        } else if (paren >= 0) {
            return signature.substring(0, paren) + "(...)";
        }
        
        return signature;
    }
    
    // ==================== Git 操作 ====================
    
    /**
     * 获取commit的父commit
     */
    private String getParentCommit(String commitHash) throws Exception {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        try (Repository repository = builder
                .setGitDir(new File(repoPath, ".git"))
                .readEnvironment()
                .findGitDir()
                .build();
             RevWalk revWalk = new RevWalk(repository)) {
            
            ObjectId commitId = repository.resolve(commitHash);
            if (commitId == null) {
                return null;
            }
            
            RevCommit commit = revWalk.parseCommit(commitId);
            if (commit.getParentCount() == 0) {
                return null;
            }
            
            return commit.getParent(0).getName();
        }
    }
    
    /**
     * 获取commit信息
     */
    private CommitInfo getCommitInfo(String commitHash) throws Exception {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        try (Repository repository = builder
                .setGitDir(new File(repoPath, ".git"))
                .readEnvironment()
                .findGitDir()
                .build();
             RevWalk revWalk = new RevWalk(repository)) {
            
            ObjectId commitId = repository.resolve(commitHash);
            if (commitId == null) {
                return new CommitInfo(commitHash, commitHash.substring(0, 7), "", "", 0);
            }
            
            RevCommit commit = revWalk.parseCommit(commitId);
            String fullId = commit.getName();
            String shortId = fullId.substring(0, Math.min(7, fullId.length()));
            String message = commit.getShortMessage();
            String author = commit.getAuthorIdent() != null ? 
                commit.getAuthorIdent().getName() : "";
            long commitTime = commit.getCommitTime();
            
            return new CommitInfo(fullId, shortId, message, author, commitTime);
        }
    }
    
    /**
     * 检查是否超出时间窗口（相对于目标commit）
     * 
     * @param commitHash 要检查的commit
     * @param referenceTime 参照时间（目标commit的时间戳，秒）
     * @param maxDays 最大天数
     * @return true 如果超出时间窗口
     */
    private boolean exceedsTimeWindow(String commitHash, long referenceTime, int maxDays) throws Exception {
        if (maxDays <= 0) {
            return false;  // 无时间限制
        }
        
        CommitInfo info = getCommitInfo(commitHash);
        long commitTimeSeconds = info.getCommitTime();
        
        // 计算相对于目标commit的时间差（向前回溯，所以是 referenceTime - commitTime）
        long daysPassed = (referenceTime - commitTimeSeconds) / (24 * 3600);
        
        return daysPassed > maxDays;
    }
    
    // ==================== Getters/Setters ====================
    
    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }
    
    public void setMaxDays(int maxDays) {
        this.maxDays = maxDays;
    }
    

    
    // ==================== 内部类 ====================
    
    /**
     * Commit信息
     */
    public static class CommitInfo {
        private final String commitId;
        private final String shortId;
        private final String message;
        private final String author;
        private final long commitTime;
        
        public CommitInfo(String commitId, String shortId, String message, 
                         String author, long commitTime) {
            this.commitId = commitId;
            this.shortId = shortId;
            this.message = message;
            this.author = author;
            this.commitTime = commitTime;
        }
        
        public String getCommitId() { return commitId; }
        public String getShortId() { return shortId; }
        public String getMessage() { return message; }
        public String getAuthor() { return author; }
        public long getCommitTime() { return commitTime; }
    }
    
    /**
     * Commit节点（包含追踪信息）
     */
    public static class CommitNode {
        private final String commitId;
        private final CommitInfo info;
        private final Set<String> trackedMethods;
        private final List<RefactoringInfo> matchedRefactorings;
        
        public CommitNode(String commitId, CommitInfo info, 
                         Set<String> trackedMethods,
                         List<RefactoringInfo> matchedRefactorings) {
            this.commitId = commitId;
            this.info = info;
            this.trackedMethods = trackedMethods;
            this.matchedRefactorings = matchedRefactorings;
        }
        
        public String getCommitId() { return commitId; }
        public CommitInfo getInfo() { return info; }
        public Set<String> getTrackedMethods() { return trackedMethods; }
        public List<RefactoringInfo> getMatchedRefactorings() { return matchedRefactorings; }
    }
    
    /**
     * 时间线结果
     */
    public static class TimelineResult {
        private final List<CommitNode> nodes;
        
        public TimelineResult(List<CommitNode> nodes) {
            this.nodes = nodes;
        }
        
        public static TimelineResult single(String commitId) {
            List<CommitNode> nodes = new ArrayList<>();
            nodes.add(new CommitNode(
                commitId,
                new CommitInfo(commitId, commitId.substring(0, 7), "", "", 0),
                new HashSet<>(),
                new ArrayList<>()
            ));
            return new TimelineResult(nodes);
        }
        
        public List<String> getCommits() {
            List<String> commits = new ArrayList<>();
            for (CommitNode node : nodes) {
                commits.add(node.getCommitId());
            }
            return commits;
        }
        
        public List<CommitNode> getNodes() {
            return nodes;
        }
        
        public int size() {
            return nodes.size();
        }
    }
}
