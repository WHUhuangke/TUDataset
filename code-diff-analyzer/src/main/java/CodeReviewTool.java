import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import spoon.Launcher;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtTypeMember;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Java代码审查工具 - 分析指定commit的diff并识别受影响的函数和类
 * 支持命令行直接使用
 */
public class CodeReviewTool {

    private String repoPath;
    private Git git;

    public CodeReviewTool(String repositoryPath) throws Exception {
        this.repoPath = repositoryPath;
        this.git = Git.open(new File(repoPath));
    }

    /**
     * 分析指定commit的代码变更 - 修复commit ID解析
     */
    public List<FileChangeAnalysis> analyzeCommitChanges(String commitHash) throws Exception {
        List<FileChangeAnalysis> results = new ArrayList<>();

        // 获取commit对象 - 修复commit ID解析
        Repository repository = git.getRepository();
        ObjectId commitId;

        try {
            // 首先尝试直接解析（完整hash）
            commitId = ObjectId.fromString(commitHash);
        } catch (IllegalArgumentException e) {
            // 如果是短hash或引用，使用resolve方法
            commitId = repository.resolve(commitHash);
            if (commitId == null) {
                throw new IllegalArgumentException("无法解析commit: " + commitHash +
                        "。请使用完整commit hash、短hash或有效的引用（如HEAD~1, branch-name）");
            }
        }

        RevCommit commit;
        try (RevWalk revWalk = new RevWalk(repository)) {
            commit = revWalk.parseCommit(commitId);
        }

        // 获取父commit（通常第一个父commit）
        if (commit.getParentCount() == 0) {
            System.out.println("这是初始commit，没有父commit可比较");
            return results;
        }

        RevCommit parentCommit;
        try (RevWalk revWalk = new RevWalk(repository)) {
            parentCommit = revWalk.parseCommit(commit.getParent(0).getId());
        }

        // 获取diff条目
        List<DiffEntry> diffs = getDiffEntries(parentCommit, commit);

        System.out.println("找到 " + diffs.size() + " 个文件变更");

        for (DiffEntry diff : diffs) {
            String filePath = diff.getNewPath();
            if (filePath.endsWith(".java")) {
                System.out.println("分析Java文件: " + filePath);
                FileChangeAnalysis analysis = analyzeJavaFileDiff(diff, parentCommit, commit);
                if (analysis != null) {
                    results.add(analysis);
                }
            } else {
                // System.out.println("跳过非Java文件: " + filePath);
            }
        }

        return results;
    }

    /**
     * 获取两个commit之间的diff条目
     */
    private List<DiffEntry> getDiffEntries(RevCommit oldCommit, RevCommit newCommit) throws Exception {
        try (ObjectReader reader = git.getRepository().newObjectReader()) {
            CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
            oldTreeIter.reset(reader, oldCommit.getTree().getId());

            CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
            newTreeIter.reset(reader, newCommit.getTree().getId());

            return git.diff()
                    .setOldTree(oldTreeIter)
                    .setNewTree(newTreeIter)
                    .call();
        }
    }

    /**
     * 分析Java文件的diff
     */
    private FileChangeAnalysis analyzeJavaFileDiff(DiffEntry diff, RevCommit oldCommit, RevCommit newCommit)
            throws Exception {
        String filePath = diff.getNewPath();

        // 获取具体的编辑列表
        EditList edits = getEditList(diff);

        if (edits.isEmpty()) {
            System.out.println("  文件 " + filePath + " 没有实际代码变更");
            return null;
        }

        FileChangeAnalysis analysis = new FileChangeAnalysis(filePath);

        // 分析新旧版本的影响范围
        analyzeAffectedElements(analysis, edits, oldCommit, newCommit, filePath);

        return analysis;
    }

    /**
     * 获取详细的编辑列表
     */
    private EditList getEditList(DiffEntry diff) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DiffFormatter diffFormatter = new DiffFormatter(out)) {
            diffFormatter.setRepository(git.getRepository());
            FileHeader fileHeader = diffFormatter.toFileHeader(diff);
            return fileHeader.toEditList();
        }
    }

    /**
     * 分析受影响的代码元素
     */
    private void analyzeAffectedElements(FileChangeAnalysis analysis, EditList edits,
            RevCommit oldCommit, RevCommit newCommit,
            String filePath) throws Exception {
        // 提取文件内容到临时文件进行分析
        Path tempOldFile = extractFileContent(oldCommit, filePath, "old");
        Path tempNewFile = extractFileContent(newCommit, filePath, "new");

        try {
            // 使用Spoon分析新旧版本
            if (tempOldFile != null && Files.exists(tempOldFile)) {
                analyzeFileWithSpoon(analysis, tempOldFile.toFile(), edits, true);
            }
            if (tempNewFile != null && Files.exists(tempNewFile)) {
                analyzeFileWithSpoon(analysis, tempNewFile.toFile(), edits, false);
            }
        } finally {
            // 清理临时文件
            if (tempOldFile != null)
                Files.deleteIfExists(tempOldFile);
            if (tempNewFile != null)
                Files.deleteIfExists(tempNewFile);
        }
    }

    /**
     * 提取特定commit的文件内容到临时文件
     */
    private Path extractFileContent(RevCommit commit, String filePath, String suffix) throws Exception {
        Repository repository = git.getRepository();

        try (TreeWalk treeWalk = new TreeWalk(repository)) {
            treeWalk.addTree(commit.getTree());
            treeWalk.setRecursive(true);
            treeWalk.setFilter(PathFilter.create(filePath));

            if (!treeWalk.next()) {
                return null; // 文件不存在
            }

            // 提取文件内容
            byte[] fileContent = repository.open(treeWalk.getObjectId(0)).getBytes();
            Path tempFile = Files.createTempFile("code_review_" + suffix, ".java");
            try (FileOutputStream fos = new FileOutputStream(tempFile.toFile())) {
                fos.write(fileContent);
            }
            return tempFile;
        }
    }

    /**
     * 使用Spoon分析文件并匹配受影响的范围
     */
    private void analyzeFileWithSpoon(FileChangeAnalysis analysis, File javaFile,
            EditList edits, boolean isOldVersion) {
        try {
            Launcher spoon = new Launcher();
            spoon.addInputResource(javaFile.getAbsolutePath());
            spoon.getEnvironment().setNoClasspath(true);
            spoon.getEnvironment().setAutoImports(true);
            spoon.buildModel();

            // 获取所有类
            List<CtType<?>> types = spoon.getModel().getElements(new TypeFilter<>(CtType.class));

            for (CtType<?> type : types) {
                if (type.getPosition().isValidPosition()) {
                    int startLine = type.getPosition().getLine();
                    int endLine = type.getPosition().getEndLine();

                    // 检查该类是否受到diff影响
                    if (isRangeAffectedByEdits(edits, startLine, endLine)) {
                        String versionLabel = isOldVersion ? "OLD" : "NEW";
                        analysis.addAffectedElement(versionLabel, "CLASS", type.getQualifiedName(),
                                startLine, endLine);
                    }

                    // 检查类中的方法
                    analyzeMethodsInType(analysis, type, edits, isOldVersion);
                    // 检查类中的构造方法
                    analyzeConstructorsInType(analysis, type, edits, isOldVersion);
                }
            }

        } catch (Exception e) {
            System.err.println("Spoon分析文件失败: " + javaFile.getName() + ", 错误: " + e.getMessage());
        }
    }

    /**
     * 分析类型中的构造方法
     */
    private void analyzeConstructorsInType(FileChangeAnalysis analysis, CtType<?> type,
            EditList edits, boolean isOldVersion) {
        // 获取类型中的所有成员并过滤出构造方法
        for (CtTypeMember member : type.getTypeMembers()) {
            if (member instanceof CtConstructor) {
                CtConstructor<?> constructor = (CtConstructor<?>) member;

                if (constructor.getPosition().isValidPosition()) {
                    int startLine = constructor.getPosition().getLine();
                    int endLine = constructor.getPosition().getEndLine();
                    System.err.println("constructor: " + constructor.getSignature() + startLine + " " + endLine);

                    if (isRangeAffectedByEdits(edits, startLine, endLine)) {
                        String versionLabel = isOldVersion ? "OLD" : "NEW";
                        analysis.addAffectedElement(versionLabel, "CONSTRUCTOR",
                                constructor.getSignature(),
                                startLine, endLine);
                        // System.out.println("=====constructor " + constructor.getSignature() + " is
                        // affected");
                    }
                }
            }
        }
    }

    /**
     * 分析类型中的方法
     */
    private void analyzeMethodsInType(FileChangeAnalysis analysis, CtType<?> type,
            EditList edits, boolean isOldVersion) {
        for (CtMethod<?> method : type.getMethods()) {
            if (method.getPosition().isValidPosition()) {
                int startLine = method.getPosition().getLine();
                int endLine = method.getPosition().getEndLine();
                // System.err.println("method: " + method.getSignature() + startLine + " "
                // +endLine);
                if (isRangeAffectedByEdits(edits, startLine, endLine)) {
                    String versionLabel = isOldVersion ? "OLD" : "NEW";
                    analysis.addAffectedElement(versionLabel, "METHOD",
                            type.getQualifiedName() + "." + method.getSignature(),
                            startLine, endLine);
                    // System.out.println("=====method " + method.getSignature() + " is affected");

                }
            }
        }
    }

    /**
     * 检查行号范围是否受到编辑影响
     */
    private boolean isRangeAffectedByEdits(EditList edits, int startLine, int endLine) {
        for (Edit edit : edits) {
            int editStart = edit.getBeginB() + 1; // 转换为1-based行号
            int editEnd = editStart + (edit.getLengthB() >= 1 ? edit.getLengthB() : 1) - 1;
            // System.out.println("editStart: " + editStart + " editEnd: " + editEnd +
            // "startLine: " + startLine + " endLine: " + endLine);
            // 检查范围是否有交集
            if (rangesOverlap(startLine, endLine, editStart, editEnd)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查两个范围是否有交集
     */
    private boolean rangesOverlap(int start1, int end1, int start2, int end2) {
        return Math.max(start1, start2) <= Math.min(end1, end2);
    }

    /**
     * 关闭资源
     */
    public void close() {
        if (git != null) {
            git.close();
        }
    }

    public List<String> getAllCommitHashes() {
        List<String> commitHashes = new ArrayList<>();
        try (RevWalk walk = new RevWalk(git.getRepository())) {
            Iterable<RevCommit> commits = git.log().all().call();
            for (RevCommit commit : commits) {
                commitHashes.add(commit.getName());
            }
        } catch (Exception e) {
            System.err.println("获取commit列表失败: " + e.getMessage());
        }
        return commitHashes;
    }

    /**
     * 命令行使用接口
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("用法: java CodeReviewTool <repo_list_file>");
            System.err.println("repo_list_file: 包含仓库路径列表的txt文件，每行一个路径");
            System.exit(1);
        }

        String repoListFile = args[0];

        try {
            System.out.println("开始批量分析代码变更...");
            System.out.println("仓库列表文件: " + repoListFile);
            System.out.println("==================================================");

            List<String> repoPaths = Files.readAllLines(Paths.get(repoListFile));
            // System.out.println("找到 " + repoPaths.size() + " 个仓库");

            int totalRepos = repoPaths.size();
            int processedRepos = 0;

            for (String repoPath : repoPaths) {
                if (repoPath.trim().isEmpty())
                    continue;

                processedRepos++;
                System.out.println("\n🔄 处理仓库 (" + processedRepos + "/" + totalRepos + "): " + repoPath);
                System.out.println("--------------------------------------------------------------------");

                try {
                    CodeReviewTool tool = new CodeReviewTool(repoPath.trim());

                    List<String> commitHashes = tool.getAllCommitHashes();
                    System.out.println("找到 " + commitHashes.size() + " 个commit");

                    List<Map<String, Object>> repoResults = new ArrayList<>();

                    int processedCommits = 0;
                    for (String commitHash : commitHashes) {
                        processedCommits++;
                        System.out.print("分析commit " + processedCommits + "/" + commitHashes.size() + ": "
                                + commitHash.substring(0, 8) + "... ");

                        try {
                            List<FileChangeAnalysis> results = tool.analyzeCommitChanges(commitHash);

                            Map<String, Object> commitResult = new HashMap<>();
                            commitResult.put("commit_id", commitHash);

                            Map<String, List<Map<String, Object>>> changedMethods = new HashMap<>();
                            Map<String, List<Map<String, Object>>> changedClasses = new HashMap<>();
                            Map<String, List<Map<String, Object>>> changedConstructors = new HashMap<>();
                            for (FileChangeAnalysis result : results) {
                                String filePath = result.getFilePath();

                                for (AffectedElement element : result.getAffectedElements()) {

                                    Map<String, Object> elementInfo = new HashMap<>();
                                    elementInfo.put("element_name", element.getElementName());
                                    elementInfo.put("element_type", element.getElementType());
                                    elementInfo.put("start_line", element.getStartLine());
                                    elementInfo.put("end_line", element.getEndLine());
                                    elementInfo.put("version", element.getVersion());

                                    if ("METHOD".equals(element.getElementType())) {
                                        changedMethods.computeIfAbsent(filePath, k -> new ArrayList<>())
                                                .add(elementInfo);
                                    } else if ("CLASS".equals(element.getElementType())) {
                                        changedClasses.computeIfAbsent(filePath, k -> new ArrayList<>())
                                                .add(elementInfo);
                                    } else if ("CONSTRUCTOR".equals(element.getElementType())) {
                                        changedConstructors.computeIfAbsent(filePath, k -> new ArrayList<>())
                                                .add(elementInfo);
                                    }

                                }
                            }

                            commitResult.put("changed_methods", changedMethods);
                            commitResult.put("changed_classes", changedClasses);
                            commitResult.put("changed_constructors", changedConstructors);
                            repoResults.add(commitResult);

                            System.out.println("✅ 完成");

                        } catch (Exception e) {
                            System.out.println("❌ 失败: " + e.getMessage());
                        }
                    }

                    String repoName = new File(repoPath.trim()).getName();
                    String outputFileName = "/home/hk/ai4se/bin/code-diff-analyzer/code_changes/" + repoName
                            + "_code_changes.json";

                    ObjectMapper mapper = new ObjectMapper();
                    mapper.writerWithDefaultPrettyPrinter().writeValue(new File(outputFileName), repoResults);

                    System.out.println("📁 结果已保存到: " + outputFileName);
                    tool.close();

                } catch (Exception e) {
                    System.err.println("处理仓库失败: " + repoPath + " - " + e.getMessage());
                }
            }

            System.out.println("\n" + "====================================================================");
            System.out.println("批量分析完成！共处理 " + processedRepos + " 个仓库");

        } catch (Exception e) {
            System.err.println("分析过程中发生错误: " + e.getMessage());
            System.err.println("请检查:");
            System.err.println("  1. 仓库列表文件路径是否正确");
            System.err.println("  2. 文件格式是否正确（每行一个仓库路径）");
            System.err.println("  3. 仓库路径是否有效");
            System.exit(1);
        }
    }

}

// FileChangeAnalysis 和 AffectedElement 类保持不变...

/**
 * 文件变更分析结果
 */
class FileChangeAnalysis {
    private String filePath;
    private List<AffectedElement> affectedElements;

    public FileChangeAnalysis(String filePath) {
        this.filePath = filePath;
        this.affectedElements = new ArrayList<>();
    }

    public void addAffectedElement(String version, String elementType,
            String elementName, int startLine, int endLine) {
        affectedElements.add(new AffectedElement(version, elementType, elementName, startLine, endLine));
    }

    // Getter方法
    public String getFilePath() {
        return filePath;
    }

    public List<AffectedElement> getAffectedElements() {
        return affectedElements;
    }
}

/**
 * 受影响的代码元素
 */
class AffectedElement {
    private String version;
    private String elementType;
    private String elementName;
    private int startLine;
    private int endLine;

    public AffectedElement(String version, String elementType, String elementName,
            int startLine, int endLine) {
        this.version = version;
        this.elementType = elementType;
        this.elementName = elementName;
        this.startLine = startLine;
        this.endLine = endLine;
    }

    // Getter方法
    public String getVersion() {
        return version;
    }

    public String getElementType() {
        return elementType;
    }

    public String getElementName() {
        return elementName;
    }

    public int getStartLine() {
        return startLine;
    }

    public int getEndLine() {
        return endLine;
    }
}
