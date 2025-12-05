package com.github.git;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 简单的提交历史检索工具，按线性父提交关系回溯。
 */
public class CommitHistoryFetcher {

    /**
     * 收集目标提交及其按第一父提交回溯的若干历史提交。
     *
     * @param repoPath   Git 仓库根目录
     * @param headCommit 起始提交（通常为目标提交）
     * @param depth      回溯深度（不包含起始提交）
     * @return 时间顺序排列的提交列表（最早的在前）
     * @throws IOException 读取仓库失败时抛出
     */
    public List<CommitInfo> collectLinearHistory(String repoPath, String headCommit, int depth) throws IOException {
        if (headCommit == null || headCommit.isBlank()) {
            throw new IllegalArgumentException("headCommit must not be null or empty");
        }

        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        try (Repository repository = builder
                .setGitDir(new File(repoPath, ".git"))
                .readEnvironment()
                .findGitDir()
                .build();
             RevWalk revWalk = new RevWalk(repository)) {

            ObjectId headId = repository.resolve(headCommit);
            if (headId == null) {
                throw new IllegalArgumentException("无法解析提交: " + headCommit);
            }

            List<CommitInfo> commits = new ArrayList<>();
            RevCommit current = revWalk.parseCommit(headId);
            commits.add(toCommitInfo(current));

            for (int i = 0; i < depth; i++) {
                if (current.getParentCount() == 0) {
                    break;
                }
                current = revWalk.parseCommit(current.getParent(0));
                commits.add(toCommitInfo(current));
            }

            Collections.reverse(commits);
            return commits;
        }
    }

    private CommitInfo toCommitInfo(RevCommit commit) {
        String fullId = commit.getName();
        String shortId = fullId != null && fullId.length() > 7 ? fullId.substring(0, 7) : fullId;
        String message = commit.getShortMessage();
        String author = commit.getAuthorIdent() != null ? commit.getAuthorIdent().getName() : "";
        long commitTime = commit.getCommitTime();
        return new CommitInfo(fullId, shortId, message, author, commitTime);
    }
    
    /**
     * 获取单个commit的信息
     * 
     * @param repoPath Git仓库根目录
     * @param commitId commit hash
     * @return commit信息
     * @throws IOException 读取仓库失败时抛出
     */
    public CommitInfo getCommitInfo(String repoPath, String commitId) throws IOException {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        try (Repository repository = builder
                .setGitDir(new File(repoPath, ".git"))
                .readEnvironment()
                .findGitDir()
                .build();
             RevWalk revWalk = new RevWalk(repository)) {

            ObjectId id = repository.resolve(commitId);
            if (id == null) {
                throw new IllegalArgumentException("无法解析提交: " + commitId);
            }

            RevCommit commit = revWalk.parseCommit(id);
            return toCommitInfo(commit);
        }
    }

    public static class CommitInfo {
        private final String commitId;
        private final String shortId;
        private final String message;
        private final String author;
        private final long commitTime;

        public CommitInfo(String commitId, String shortId, String message, String author, long commitTime) {
            this.commitId = commitId;
            this.shortId = shortId;
            this.message = message;
            this.author = author;
            this.commitTime = commitTime;
        }

        public String getCommitId() {
            return commitId;
        }

        public String getShortId() {
            return shortId;
        }

        public String getMessage() {
            return message;
        }

        public String getAuthor() {
            return author;
        }

        public long getCommitTime() {
            return commitTime;
        }

        @Override
        public String toString() {
            return shortId + " - " + message;
        }
    }
}
