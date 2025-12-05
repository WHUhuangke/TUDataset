package com.github.evolution;

import com.github.git.GitException;
import com.github.logging.GraphLogger;
import com.github.model.KnowledgeGraph;
import com.github.model.Node;
import com.github.refactoring.RefactoringInfo;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * 基于 Git diff 的变更检测器。用于发现 RefactoringMiner 未覆盖的源码改动。
 */
public class GitDiffChangeDetector {

    private static final GraphLogger logger = GraphLogger.getInstance();

    private final String repoPath;

    public GitDiffChangeDetector(String repoPath) {
        this.repoPath = repoPath;
    }

    public DiffChangeSet detectChanges(String fromCommit, String toCommit,
                                       KnowledgeGraph v1Graph,
                                       KnowledgeGraph v2Graph) throws GitException {
        DiffChangeSet changeSet = new DiffChangeSet();

        Repository repository = null;
        RevWalk revWalk = null;
        DiffFormatter diffFormatter = null;

        try {
            repository = new FileRepositoryBuilder()
                    .setGitDir(new File(repoPath, ".git"))
                    .readEnvironment()
                    .findGitDir()
                    .build();

            revWalk = new RevWalk(repository);

            AbstractTreeIterator oldTreeIter = prepareTreeParser(repository, revWalk, fromCommit);
            AbstractTreeIterator newTreeIter = prepareTreeParser(repository, revWalk, toCommit);

            diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
            diffFormatter.setRepository(repository);
            diffFormatter.setContext(0);

            List<DiffEntry> diffEntries = diffFormatter.scan(oldTreeIter, newTreeIter);

            NodeLocationMatcher v1Matcher = new NodeLocationMatcher(v1Graph);
            NodeLocationMatcher v2Matcher = new NodeLocationMatcher(v2Graph);

            for (DiffEntry entry : diffEntries) {
                handleDiffEntry(entry, diffFormatter, v1Matcher, v2Matcher, changeSet);
            }

            logger.info(String.format("Git diff 检测到 %d 个节点需要补充演化", 
                    changeSet.getChangedV1NodeIds().size() + changeSet.getChangedV2NodeIds().size()));

        } catch (IOException e) {
            throw new GitException("执行 git diff 失败", e);
        } finally {
            if (diffFormatter != null) {
                diffFormatter.close();
            }
            if (revWalk != null) {
                revWalk.close();
            }
            if (repository != null) {
                repository.close();
            }
        }

        return changeSet;
    }

    private void handleDiffEntry(DiffEntry entry,
                                 DiffFormatter diffFormatter,
                                 NodeLocationMatcher v1Matcher,
                                 NodeLocationMatcher v2Matcher,
                                 DiffChangeSet changeSet) throws IOException {

        String oldPath = entry.getOldPath();
        String newPath = entry.getNewPath();

        EditList edits = diffFormatter.toFileHeader(entry).toEditList();

        for (Edit edit : edits) {
            switch (edit.getType()) {
                case INSERT:
                    markNodes(v2Matcher, changeSet, true, newPath,
                            edit.getBeginB() + 1, Math.max(edit.getEndB(), edit.getBeginB()) + 1);
                    break;
                case DELETE:
                    markNodes(v1Matcher, changeSet, false, oldPath,
                            edit.getBeginA() + 1, Math.max(edit.getEndA(), edit.getBeginA()) + 1);
                    break;
                case REPLACE:
                    markNodes(v1Matcher, changeSet, false, oldPath,
                            edit.getBeginA() + 1, Math.max(edit.getEndA(), edit.getBeginA()) + 1);
                    markNodes(v2Matcher, changeSet, true, newPath,
                            edit.getBeginB() + 1, Math.max(edit.getEndB(), edit.getBeginB()) + 1);
                    break;
                case EMPTY:
                default:
                    break;
            }
        }
    }

    private void markNodes(NodeLocationMatcher matcher,
                           DiffChangeSet changeSet,
                           boolean isV2,
                           String filePath,
                           int startLine,
                           int endLine) {

        if (filePath == null || filePath.equals(DiffEntry.DEV_NULL)) {
            return;
        }

        RefactoringInfo.CodeLocation location = new RefactoringInfo.CodeLocation();
        location.setFilePath(filePath);
        location.setStartLine(startLine);
        location.setEndLine(endLine);

        List<Node> candidates = matcher.findNodesByLocation(location);
        if (candidates.isEmpty()) {
            return;
        }

        Node best = matcher.findBestMatch(candidates, location);
        if (best != null) {
            if (isV2) {
                changeSet.addV2Node(best.getId());
            } else {
                changeSet.addV1Node(best.getId());
            }
        } else {
            for (Node candidate : candidates) {
                if (isV2) {
                    changeSet.addV2Node(candidate.getId());
                } else {
                    changeSet.addV1Node(candidate.getId());
                }
            }
        }
    }

    private AbstractTreeIterator prepareTreeParser(Repository repository,
                                                   RevWalk revWalk,
                                                   String commitId) throws IOException {
        ObjectId objId = repository.resolve(commitId);
        if (objId == null) {
            throw new IOException("无法解析提交: " + commitId);
        }

        RevCommit commit = revWalk.parseCommit(objId);
        RevTree tree = commit.getTree();

        CanonicalTreeParser parser = new CanonicalTreeParser();
        try (var reader = repository.newObjectReader()) {
            parser.reset(reader, tree.getId());
        }

        return parser;
    }
}
