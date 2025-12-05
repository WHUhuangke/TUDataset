package com.github.evolution;

import com.github.git.GitService;
import com.github.git.GitException;
import com.github.logging.GraphLogger;
import com.github.model.KnowledgeGraph;
import com.github.refactoring.RefactoringDetector;
import com.github.refactoring.RefactoringInfo;
import com.github.spoon.ProjectAnalyzer;
import com.github.evolution.matcher.NodeMatchingStrategy;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.util.List;

/**
 * 演化分析器 - 核心协调器
 * 
 * <p>负责协调整个版本演化分析工作流：
 * <ol>
 *   <li>自动获取指定 commit 的父提交作为 V1</li>
 *   <li>使用 GitService 切换到 V1 commit</li>
 *   <li>使用 ProjectAnalyzer 解析 V1 代码，生成 KnowledgeGraph</li>
 *   <li>使用 GitService 切换到 V2 commit（指定的 commit）</li>
 *   <li>使用 ProjectAnalyzer 解析 V2 代码，生成 KnowledgeGraph</li>
 *   <li>使用 RefactoringDetector 检测 V1→V2 的重构操作</li>
 *   <li>使用 NodeMatchingStrategy 匹配 V1 和 V2 的节点</li>
 *   <li>使用 GraphMerger 合并两个图，标记演化状态</li>
 *   <li>恢复 Git 仓库到原始状态</li>
 * </ol>
 * 
 * <p><b>使用示例：</b>
 * <pre>{@code
 * EvolutionAnalyzer analyzer = new EvolutionAnalyzer(
 *     "/path/to/repo",
 *     "/path/to/project",
 *     "commit-hash-v2",  // 只需指定目标 commit
 *     logger
 * );
 * 
 * KnowledgeGraph mergedGraph = analyzer.analyze();
 * }</pre>
 * 
 * @author TUGraph Team
 * @since 2.0.0
 */
public class EvolutionAnalyzer {
    
    private final String repoPath;      // Git 仓库路径
    private final String projectPath;   // 项目路径（用于 Spoon 分析）
    private final String v2Commit;      // V2 版本的 commit hash（用户指定）
    private final String v1Commit;      // V1 版本的 commit hash（自动获取 parent）
    private final GraphLogger logger;
    private String v1LabelOverride;
    private String v2LabelOverride;
    
    // 组件
    private GitService gitService;
    private RefactoringDetector refactoringDetector;
    private NodeMatchingStrategy matchingStrategy;
    private GraphMerger graphMerger;
    
    // 分析结果
    private KnowledgeGraph v1Graph;
    private KnowledgeGraph v2Graph;
    private List<RefactoringInfo> refactorings;
    private NodeMapping nodeMapping;
    private KnowledgeGraph mergedGraph;
    
    /**
     * 构造演化分析器（自动获取父commit作为V1）
     * 
     * @param repoPath Git 仓库路径
     * @param projectPath 项目路径（通常等于 repoPath，但也可能是子目录）
     * @param commit 目标版本的 commit hash（自动获取其父提交作为 V1）
     * @param logger 日志记录器
     */
    public EvolutionAnalyzer(
            String repoPath,
            String projectPath,
            String commit,
            GraphLogger logger) {
        
        this.repoPath = repoPath;
        this.projectPath = projectPath;
        this.v2Commit = commit;
        this.logger = logger;
        
        // 自动获取父提交作为 V1
        this.v1Commit = getParentCommit(repoPath, commit);
        logger.info("自动检测到父提交作为 V1: " + this.v1Commit);
        
        // 初始化组件
        initializeComponents();
    }
    
    /**
     * 构造演化分析器（显式指定两个commit）
     * 
     * @param repoPath Git 仓库路径
     * @param projectPath 项目路径（通常等于 repoPath，但也可能是子目录）
     * @param v1Commit 旧版本的 commit hash
     * @param v2Commit 新版本的 commit hash
     * @param logger 日志记录器
     */
    public EvolutionAnalyzer(
            String repoPath,
            String projectPath,
            String v1Commit,
            String v2Commit,
            GraphLogger logger) {
        
        this.repoPath = repoPath;
        this.projectPath = projectPath;
        this.v1Commit = v1Commit;
        this.v2Commit = v2Commit;
        this.logger = logger;
        
        logger.info("使用显式指定的提交对: V1=" + v1Commit + ", V2=" + v2Commit);
        
        // 初始化组件
        initializeComponents();
    }
    
    /**
     * 获取指定 commit 的父提交
     * 
     * @param repoPath Git 仓库路径
     * @param commitHash commit hash
     * @return 父提交的 hash
     */
    private String getParentCommit(String repoPath, String commitHash) {
        Repository repository = null;
        RevWalk revWalk = null;
        
        try {
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            repository = builder
                    .setGitDir(new File(repoPath, ".git"))
                    .readEnvironment()
                    .findGitDir()
                    .build();
            
            revWalk = new RevWalk(repository);
            ObjectId commitId = repository.resolve(commitHash);
            RevCommit commit = revWalk.parseCommit(commitId);
            
            if (commit.getParentCount() == 0) {
                throw new IllegalArgumentException(
                    "指定的 commit 没有父提交（可能是初始提交）: " + commitHash
                );
            }
            
            RevCommit parent = commit.getParent(0);  // 获取第一个父提交
            return parent.getName();
            
        } catch (Exception e) {
            throw new RuntimeException("无法获取父提交: " + commitHash, e);
        } finally {
            if (revWalk != null) {
                revWalk.close();
            }
            if (repository != null) {
                repository.close();
            }
        }
    }
    
    /**
     * 初始化各个组件
     */
    private void initializeComponents() {
        logger.info("初始化演化分析组件...");
        
        this.refactoringDetector = new RefactoringDetector();
        this.matchingStrategy = new NodeMatchingStrategy();
        this.graphMerger = new GraphMerger(logger);
        
        logger.info("组件初始化完成");
    }

    /**
     * 覆盖默认的版本标签，便于在时间线中使用 V0/V-1 等相对标签。
     */
    public void overrideVersionLabels(String baseVersionLabel, String targetVersionLabel) {
        this.v1LabelOverride = baseVersionLabel;
        this.v2LabelOverride = targetVersionLabel;
    }
    
    /**
     * 执行完整的演化分析
     * 
     * @return 合并后的演化知识图谱
     * @throws GitException Git 操作失败
     * @throws RuntimeException 分析过程失败
     */
    public KnowledgeGraph analyze() throws GitException {
        logger.info("========================================");
        logger.info("开始版本演化分析");
        logger.info(String.format("Base Version: %s [%s]", resolveLabel(v1Commit), shortId(v1Commit)));
        logger.info(String.format("Target Version: %s [%s]", resolveLabel(v2Commit), shortId(v2Commit)));
        logger.info("========================================");

        // 创建 GitService 并手动管理生命周期
        this.gitService = new GitService(repoPath);

        try {
            // 打开 Git 仓库
            gitService.open();

            // Step 1: 分析 V1 版本
            v1Graph = analyzeVersion(v1Commit);
            
            // Step 2: 分析 V2 版本
            v2Graph = analyzeVersion(v2Commit);
            
            // Step 3: 检测重构
            refactorings = detectRefactorings();
            
            // Step 4: 节点映射
            nodeMapping = matchNodes();
            
            // Step 5: 合并图
            mergedGraph = mergeGraphs();
            
            // Step 6: 恢复 Git 状态
            logger.info("演化分析完成，恢复 Git 状态...");
            gitService.restoreOriginalState();
            
        } catch (Exception e) {
            logger.error("演化分析失败", e);
            throw new RuntimeException("Evolution analysis failed", e);
        } finally {
            // 确保 GitService 被正确关闭
            if (gitService != null) {
                gitService.close();
            }
        }
        
        logger.info("========================================");
        logger.info("✓ 演化分析成功完成");
        logger.info("========================================");
        
        return mergedGraph;
    }
    
    /**
     * 分析指定版本
     * 
     * @param commitHash commit hash
     * @param versionLabel 版本标签（用于日志）
     * @return 该版本的知识图谱
     * @throws GitException Git 操作失败
     */
    private KnowledgeGraph analyzeVersion(String commitHash) throws GitException {
        logger.info("========================================");
        logger.info(String.format("分析提交: %s [%s]", resolveLabel(commitHash), shortId(commitHash)));
        logger.info("========================================");
        
        // 1. Checkout 到指定 commit
        logger.info("切换到目标提交...");
        gitService.checkout(commitHash);
        logger.info("✓ 成功切换到 " + commitHash);
        
        // 2. 使用 Spoon 分析
        logger.info("开始解析提交 " + shortId(commitHash) + " 的代码...");
        ProjectAnalyzer analyzer = new ProjectAnalyzer(projectPath);
        KnowledgeGraph graph = analyzer.analyze();
        
        // 3. 设置版本信息
        String label = resolveLabel(commitHash);
        graph.setFromVersion(label);
        graph.setToVersion(label);
        
        logger.info("✓ 提交 " + shortId(commitHash) + " 解析完成");
        logger.info("  节点数: " + graph.getAllNodes().size());
        logger.info("  边数: " + graph.getAllEdges().size());
        
        return graph;
    }
    
    /**
     * 步骤 2: 检测重构
     * 
     * @return 重构信息列表
     * @throws GitException Git 操作失败
     */
    private List<RefactoringInfo> detectRefactorings() throws GitException {
        logger.info("========================================");
        logger.info("检测重构操作: " + v1Commit + " → " + v2Commit);
        logger.info("========================================");
        
        // 使用单 commit 模式，RefactoringMiner 会自动与父提交比较
        List<RefactoringInfo> refactorings = refactoringDetector.detectRefactoringsInCommit(
                repoPath,
                v2Commit
        );
        
        logger.info("✓ 检测到 " + refactorings.size() + " 个重构操作");
        
        // 打印前几个重构（避免输出过多）
        int displayCount = Math.min(20, refactorings.size());
        for (int i = 0; i < displayCount; i++) {
            RefactoringInfo refactoring = refactorings.get(i);
            logger.info("  [" + (i + 1) + "] " + refactoring.getType() + ": " + refactoring.getDescription());
        }
        if (refactorings.size() > displayCount) {
            logger.info("  ... 还有 " + (refactorings.size() - displayCount) + " 个重构");
        }
        
        return refactorings;
    }
    
    /**
     * 匹配两个版本的节点
     * 
     * @return 节点映射关系
     */
    private NodeMapping matchNodes() {
        logger.info("========================================");
        logger.info("匹配节点: " + shortId(v1Commit) + " → " + shortId(v2Commit));
        logger.info("========================================");
        
        NodeMapping mapping = matchingStrategy.match(v1Graph, v2Graph);
        
        logger.info("✓ 节点匹配完成");
        logger.info(mapping.getStatistics());
        
        return mapping;
    }
    
    /**
     * 合并两个图
     * 
     * @return 合并后的图
     */
    private KnowledgeGraph mergeGraphs() {
        logger.info("========================================");
        logger.info("合并图谱");
        logger.info("========================================");

        DiffChangeSet diffChangeSet = null;
        try {
            GitDiffChangeDetector diffDetector = new GitDiffChangeDetector(repoPath);
            diffChangeSet = diffDetector.detectChanges(v1Commit, v2Commit, v1Graph, v2Graph);
        } catch (GitException e) {
            logger.warn("Git diff 检测失败，跳过差异补边: " + e.getMessage());
        }

        KnowledgeGraph merged = graphMerger.mergeWithLabels(
                v1Graph,
                v2Graph,
                nodeMapping,
                refactorings,
                diffChangeSet,
                resolveLabel(v1Commit),
                resolveLabel(v2Commit)
        );
        merged.setFromVersion(resolveLabel(v1Commit));
        merged.setToVersion(resolveLabel(v2Commit));

        logger.info("✓ 图谱合并完成");

        return merged;
    }
    
    // ==================== Getters ====================
    
    public KnowledgeGraph getV1Graph() {
        return v1Graph;
    }
    
    public KnowledgeGraph getV2Graph() {
        return v2Graph;
    }
    
    public List<RefactoringInfo> getRefactorings() {
        return refactorings;
    }
    
    public NodeMapping getNodeMapping() {
        return nodeMapping;
    }
    
    public KnowledgeGraph getMergedGraph() {
        return mergedGraph;
    }

    private String shortId(String commitId) {
        if (commitId == null) {
            return "unknown";
        }
        return commitId.length() > 7 ? commitId.substring(0, 7) : commitId;
    }

    private String resolveLabel(String commitId) {
        if (commitId == null) {
            return "";
        }
        if (commitId.equals(v1Commit) && v1LabelOverride != null) {
            return v1LabelOverride;
        }
        if (commitId.equals(v2Commit) && v2LabelOverride != null) {
            return v2LabelOverride;
        }
        return commitId;
    }
}
