package com.github.git;

import com.github.logging.GraphLogger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Git 服务 - 提供 Git 仓库操作功能
 * 
 * <p>封装 JGit API，提供版本检出、提交信息查询等功能。
 * 支持临时工作空间管理，避免污染原始仓库。
 * 
 * <p><b>使用方式：</b>
 * <pre>{@code
 * GitService gitService = new GitService("/path/to/repo");
 * try {
 *     gitService.open();
 *     gitService.checkout("commit-hash");
 *     // ... 执行其他操作
 * } finally {
 *     gitService.close();
 * }
 * }</pre>
 * 
 * @author TUGraph Team
 * @since 2.0.0
 */
public class GitService implements AutoCloseable {
    
    private static final GraphLogger logger = GraphLogger.getInstance();
    
    private final String repoPath;
    private Repository repository;
    private Git git;
    private String originalBranch;
    private String originalCommit;
    
    /**
     * 构造函数
     * 
     * @param repoPath Git 仓库路径
     */
    public GitService(String repoPath) {
        this.repoPath = repoPath;
    }
    
    /**
     * 打开 Git 仓库
     * 
     * @throws GitException 如果仓库不存在或无法打开
     */
    public void open() throws GitException {
        try {
            File repoDir = new File(repoPath);
            if (!repoDir.exists()) {
                throw new GitException("仓库路径不存在: " + repoPath);
            }
            
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            repository = builder
                    .setGitDir(new File(repoDir, ".git"))
                    .readEnvironment()
                    .findGitDir()
                    .build();
            
            if (!repository.getDirectory().exists()) {
                throw new GitException("不是有效的 Git 仓库: " + repoPath);
            }
            
            git = new Git(repository);
            
            // 记录当前状态
            originalBranch = repository.getBranch();
            originalCommit = repository.resolve("HEAD").getName();
            
            logger.info("Git 仓库已打开: " + repoPath);
            logger.debug("当前分支: " + originalBranch);
            logger.debug("当前提交: " + originalCommit);
            
        } catch (IOException e) {
            throw new GitException("无法打开 Git 仓库: " + repoPath, e);
        }
    }
    
    /**
     * 检出指定提交
     * 
     * @param commitRef 提交引用（hash、分支名、标签名、HEAD~1 等）
     * @throws GitException 如果检出失败
     */
    public void checkout(String commitRef) throws GitException {
        try {
            // 清理可能存在的临时文件（如 Spoon 生成的文件）
            cleanupTemporaryFiles();
            
            // 检查是否有未提交的更改
            if (hasUncommittedChanges()) {
                throw new GitException(
                    "仓库有未提交的更改，无法检出。请先提交或暂存更改。\n" +
                    "Repository has uncommitted changes. Please commit or stash them first."
                );
            }
            
            // 解析提交引用
            ObjectId commitId = repository.resolve(commitRef);
            if (commitId == null) {
                throw new GitException("无法解析提交引用: " + commitRef);
            }
            
            logger.info("检出提交: " + commitRef + " (" + commitId.getName() + ")");
            
            // 执行检出
            git.checkout()
                    .setName(commitId.getName())
                    .setForced(false)
                    .call();
            
            logger.info("✓ 检出成功");
            
        } catch (GitAPIException | IOException e) {
            throw new GitException("检出失败: " + commitRef, e);
        }
    }
    
    /**
     * 获取提交信息
     * 
     * @param commitRef 提交引用
     * @return 提交信息字符串
     * @throws GitException 如果获取失败
     */
    public String getCommitInfo(String commitRef) throws GitException {
        try {
            ObjectId commitId = repository.resolve(commitRef);
            if (commitId == null) {
                throw new GitException("无法解析提交引用: " + commitRef);
            }
            
            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit commit = revWalk.parseCommit(commitId);
                
                StringBuilder info = new StringBuilder();
                info.append("Commit: ").append(commit.getName()).append("\n");
                info.append("Author: ").append(commit.getAuthorIdent().getName()).append("\n");
                info.append("Date: ").append(commit.getAuthorIdent().getWhen()).append("\n");
                info.append("Message: ").append(commit.getShortMessage()).append("\n");
                
                return info.toString();
            }
            
        } catch (IOException e) {
            throw new GitException("获取提交信息失败: " + commitRef, e);
        }
    }
    
    /**
     * 获取当前分支名
     * 
     * @return 分支名
     * @throws GitException 如果获取失败
     */
    public String getCurrentBranch() throws GitException {
        try {
            return repository.getBranch();
        } catch (IOException e) {
            throw new GitException("获取当前分支失败", e);
        }
    }
    
    /**
     * 获取当前提交 hash
     * 
     * @return 提交 hash
     * @throws GitException 如果获取失败
     */
    public String getCurrentCommit() throws GitException {
        try {
            ObjectId head = repository.resolve("HEAD");
            return head != null ? head.getName() : null;
        } catch (IOException e) {
            throw new GitException("获取当前提交失败", e);
        }
    }
    
    /**
     * 检查是否有未提交的更改
     * 
     * @return 是否有未提交的更改
     * @throws GitException 如果检查失败
     */
    public boolean hasUncommittedChanges() throws GitException {
        try {
            return !git.status().call().isClean();
        } catch (GitAPIException e) {
            throw new GitException("检查仓库状态失败", e);
        }
    }
    
    /**
     * 清理临时文件
     * 
     * <p>清理可能由分析工具（如 Spoon）生成的临时文件，避免这些文件
     * 被 Git 检测为未提交的更改，导致无法切换版本。
     * 
     * <p>当前会清理：
     * <ul>
     *   <li>spoon.classpath.tmp - Spoon MavenLauncher 生成的临时文件</li>
     *   <li>spooned/ - Spoon 输出目录（如果存在）</li>
     * </ul>
     */
    private void cleanupTemporaryFiles() {
        try {
            File repoDir = new File(repoPath);
            
            // 清理 spoon.classpath.tmp
            File spoonClasspathTmp = new File(repoDir, "spoon.classpath.tmp");
            if (spoonClasspathTmp.exists()) {
                if (spoonClasspathTmp.delete()) {
                    logger.debug("已清理临时文件: spoon.classpath.tmp");
                } else {
                    logger.warn("无法删除临时文件: spoon.classpath.tmp");
                }
            }
            
            // 清理 spooned 目录
            File spoonedDir = new File(repoDir, "spooned");
            if (spoonedDir.exists() && spoonedDir.isDirectory()) {
                deleteDirectory(spoonedDir);
                logger.debug("已清理临时目录: spooned/");
            }
            
            // 清理其他可能的 Spoon 临时文件
            File[] tmpFiles = repoDir.listFiles((dir, name) -> 
                name.startsWith("spoon") && (name.endsWith(".tmp") || name.endsWith(".temp"))
            );
            if (tmpFiles != null) {
                for (File tmpFile : tmpFiles) {
                    if (tmpFile.delete()) {
                        logger.debug("已清理临时文件: " + tmpFile.getName());
                    }
                }
            }
            
        } catch (Exception e) {
            // 清理失败不应该阻止主流程，只记录警告
            logger.warn("清理临时文件时出现问题: " + e.getMessage());
        }
    }
    
    /**
     * 递归删除目录
     * 
     * @param directory 要删除的目录
     * @throws IOException 如果删除失败
     */
    private void deleteDirectory(File directory) throws IOException {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        if (!file.delete()) {
                            logger.warn("无法删除文件: " + file.getAbsolutePath());
                        }
                    }
                }
            }
            if (!directory.delete()) {
                logger.warn("无法删除目录: " + directory.getAbsolutePath());
            }
        }
    }
    
    /**
     * 硬重置到指定提交
     * 
     * @param commitRef 提交引用
     * @throws GitException 如果重置失败
     */
    public void resetHard(String commitRef) throws GitException {
        try {
            ObjectId commitId = repository.resolve(commitRef);
            if (commitId == null) {
                throw new GitException("无法解析提交引用: " + commitRef);
            }
            
            logger.warn("硬重置到: " + commitRef);
            
            git.reset()
                    .setMode(ResetCommand.ResetType.HARD)
                    .setRef(commitId.getName())
                    .call();
            
            logger.info("✓ 重置成功");
            
        } catch (GitAPIException | IOException e) {
            throw new GitException("重置失败: " + commitRef, e);
        }
    }
    
    /**
     * 恢复到原始状态
     * 
     * @throws GitException 如果恢复失败
     */
    public void restoreOriginalState() throws GitException {
        if (originalCommit != null) {
            logger.info("恢复到原始状态: " + originalCommit);
            checkout(originalCommit);
        }
    }
    
    /**
     * 创建临时工作空间（未实现）
     * 
     * <p>TODO: 实现临时工作空间功能，用于避免污染原始仓库
     * 
     * @return 临时工作空间路径
     * @throws GitException 如果创建失败
     */
    public Path createTemporaryWorkspace() throws GitException {
        throw new UnsupportedOperationException("临时工作空间功能尚未实现");
    }
    
    /**
     * 获取仓库路径
     * 
     * @return 仓库路径
     */
    public String getRepoPath() {
        return repoPath;
    }
    
    /**
     * 获取仓库对象
     * 
     * @return Repository 对象
     */
    public Repository getRepository() {
        return repository;
    }
    
    /**
     * 获取 Git 对象
     * 
     * @return Git 对象
     */
    public Git getGit() {
        return git;
    }
    
    /**
     * 关闭 Git 仓库
     */
    @Override
    public void close() {
        if (git != null) {
            git.close();
            git = null;
        }
        if (repository != null) {
            repository.close();
            repository = null;
        }
        logger.info("Git 仓库已关闭");
    }
    
    /**
     * 清理临时文件（未实现）
     * 
     * @throws GitException 如果清理失败
     */
    public void cleanup() throws GitException {
        // TODO: 实现临时文件清理
        logger.debug("清理临时文件（当前未实现）");
    }
}
