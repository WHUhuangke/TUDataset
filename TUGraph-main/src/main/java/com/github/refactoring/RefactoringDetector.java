package com.github.refactoring;

import com.github.git.GitException;
import com.github.logging.GraphLogger;
import gr.uom.java.xmi.diff.CodeRange;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.refactoringminer.api.GitHistoryRefactoringMiner;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringHandler;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 重构检测器 - 封装 RefactoringMiner API
 * 
 * <p>使用 RefactoringMiner 检测两个提交之间的重构操作。
 * 支持 40+ 种重构类型，包括：
 * <ul>
 *   <li>RENAME_CLASS, RENAME_METHOD, RENAME_ATTRIBUTE</li>
 *   <li>MOVE_CLASS, MOVE_METHOD, MOVE_ATTRIBUTE</li>
 *   <li>EXTRACT_METHOD, EXTRACT_CLASS, EXTRACT_INTERFACE</li>
 *   <li>INLINE_METHOD, INLINE_VARIABLE</li>
 *   <li>CHANGE_PARAMETER_TYPE, CHANGE_RETURN_TYPE</li>
 *   <li>PULL_UP_METHOD, PUSH_DOWN_METHOD</li>
 *   <li>等等...</li>
 * </ul>
 * 
 * @author TUGraph Team
 * @since 2.0.0
 */
public class RefactoringDetector {
    
    private static final GraphLogger logger = GraphLogger.getInstance();
    
    /**
     * 检测两个提交之间的重构操作
     * 
     * @param repoPath Git 仓库路径
     * @param commitHash1 源提交 hash
     * @param commitHash2 目标提交 hash
     * @return 重构信息列表
     * @throws GitException 如果检测失败
     */
    public List<RefactoringInfo> detectRefactorings(
            String repoPath, 
            String commitHash1, 
            String commitHash2) throws GitException {
        
        logger.info("开始检测重构操作...");
        logger.info("  源提交: " + commitHash1);
        logger.info("  目标提交: " + commitHash2);
        
        List<RefactoringInfo> refactoringInfos = new ArrayList<>();
        Repository repository = null;
        
        try {
            // 打开 Git 仓库
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            repository = builder
                    .setGitDir(new File(repoPath, ".git"))
                    .readEnvironment()
                    .findGitDir()
                    .build();
            
            GitHistoryRefactoringMiner miner = new GitHistoryRefactoringMinerImpl();
            
            // 使用 RefactoringHandler 收集重构信息
            miner.detectBetweenCommits(
                    repository,
                    commitHash1,
                    commitHash2,
                    new RefactoringHandler() {
                        @Override
                        public void handle(String commitId, List<Refactoring> refactorings) {
                            logger.info("在提交 " + commitId + " 中检测到 " + refactorings.size() + " 个重构");
                            
                            for (Refactoring refactoring : refactorings) {
                                RefactoringInfo info = convertToRefactoringInfo(refactoring);
                                refactoringInfos.add(info);
                                
                                logger.debug("  - " + refactoring.getName() + ": " + refactoring.toString());
                            }
                        }
                        
                        @Override
                        public void handleException(String commit, Exception e) {
                            logger.error("处理提交 " + commit + " 时出错: " + e.getMessage(), e);
                        }
                    }
            );
            
            logger.info("✓ 重构检测完成，共检测到 " + refactoringInfos.size() + " 个重构");
            
        } catch (Exception e) {
            throw new GitException("检测重构失败", e);
        } finally {
            // 关闭 Repository 资源
            if (repository != null) {
                repository.close();
            }
        }
        
        return refactoringInfos;
    }
    
    /**
     * 将 RefactoringMiner 的 Refactoring 对象转换为自定义的 RefactoringInfo
     * 
     * @param refactoring RefactoringMiner 的 Refactoring 对象
     * @return RefactoringInfo 对象
     */
    private RefactoringInfo convertToRefactoringInfo(Refactoring refactoring) {
        RefactoringInfo info = new RefactoringInfo();
        
        // 设置重构类型
        info.setType(refactoring.getRefactoringType().name());
        
        // 设置描述
        info.setDescription(refactoring.toString());
        
        // 提取左侧（before）代码位置
        List<CodeRange> leftSide = refactoring.leftSide();
        for (CodeRange codeRange : leftSide) {
            RefactoringInfo.CodeLocation location = new RefactoringInfo.CodeLocation();
            location.setFilePath(codeRange.getFilePath());
            // CodeRange 没有 getClassName()，从 codeElement 中提取
            location.setCodeElement(codeRange.getCodeElement());
            location.setStartLine(codeRange.getStartLine());
            location.setEndLine(codeRange.getEndLine());
            info.addLeftSideLocation(location);
        }
        
        // 提取右侧（after）代码位置
        List<CodeRange> rightSide = refactoring.rightSide();
        for (CodeRange codeRange : rightSide) {
            RefactoringInfo.CodeLocation location = new RefactoringInfo.CodeLocation();
            location.setFilePath(codeRange.getFilePath());
            // CodeRange 没有 getClassName()，从 codeElement 中提取
            location.setCodeElement(codeRange.getCodeElement());
            location.setStartLine(codeRange.getStartLine());
            location.setEndLine(codeRange.getEndLine());
            info.addRightSideLocation(location);
        }
        
        return info;
    }
    
    /**
     * 检测单个提交中的重构（与父提交比较）
     * 
     * @param repoPath Git 仓库路径
     * @param commitHash 提交 hash
     * @return 重构信息列表
     * @throws GitException 如果检测失败
     */
    public List<RefactoringInfo> detectRefactoringsInCommit(
            String repoPath,
            String commitHash) throws GitException {
        
        logger.info("检测单个提交中的重构: " + commitHash);
        
        List<RefactoringInfo> refactoringInfos = new ArrayList<>();
        Repository repository = null;
        
        try {
            // 打开 Git 仓库
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            repository = builder
                    .setGitDir(new File(repoPath, ".git"))
                    .readEnvironment()
                    .findGitDir()
                    .build();
            
            GitHistoryRefactoringMiner miner = new GitHistoryRefactoringMinerImpl();
            
            miner.detectAtCommit(
                    repository,
                    commitHash,
                    new RefactoringHandler() {
                        @Override
                        public void handle(String commitId, List<Refactoring> refactorings) {
                            logger.info("检测到 " + refactorings.size() + " 个重构");
                            
                            for (Refactoring refactoring : refactorings) {
                                RefactoringInfo info = convertToRefactoringInfo(refactoring);
                                refactoringInfos.add(info);
                            }
                        }
                        
                        @Override
                        public void handleException(String commit, Exception e) {
                            logger.error("处理提交时出错: " + e.getMessage(), e);
                        }
                    }
            );
            
        } catch (Exception e) {
            throw new GitException("检测重构失败", e);
        } finally {
            // 关闭 Repository 资源
            if (repository != null) {
                repository.close();
            }
        }
        
        return refactoringInfos;
    }
}
