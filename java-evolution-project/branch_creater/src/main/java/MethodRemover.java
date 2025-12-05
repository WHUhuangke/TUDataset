import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MethodRemover {
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("用法: java MethodRemover <项目根目录> <方法签名1> <方法签名2> ...");
            System.out.println("示例: java MethodRemover /app/commons-csv " +
                    "org.apache.commons.csv.CSVParserTest.testParseFileNullFormat() " +
                    "org.apache.commons.csv.CSVParserTest.testParseWithDelimiterStringWithEscape()");
            return;
        }
        
        String projectRoot = args[0];
        List<String> methodSignatures = Arrays.asList(args).subList(1, args.length);
        
        System.out.println("项目根目录: " + projectRoot);
        System.out.println("要删除的方法:");
        for (String signature : methodSignatures) {
            System.out.println("  - " + signature);
        }
        
        removeMethodsFromProject(projectRoot, methodSignatures);
    }
    
    public static void removeMethodsFromProject(String projectRoot, List<String> methodSignatures) {
        try {
            List<Path> javaFiles = findJavaFiles(projectRoot);
            System.out.println("找到 " + javaFiles.size() + " 个Java文件");
            
            int totalRemoved = 0;
            for (Path javaFile : javaFiles) {
                int removed = removeMethodsFromFile(javaFile, methodSignatures);
                totalRemoved += removed;
            }
            
            System.out.println("完成! 总共删除了 " + totalRemoved + " 个方法");
            
        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static List<Path> findJavaFiles(String rootDir) throws Exception {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(Paths.get(rootDir))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        return javaFiles;
    }
    
    private static int removeMethodsFromFile(Path filePath, List<String> methodSignatures) {
        try {
            CompilationUnit cu = new JavaParser().parse(new FileInputStream(filePath.toFile()))
                    .getResult()
                    .orElseThrow(() -> new RuntimeException("解析文件失败: " + filePath));
            
            List<MethodDeclaration> methodsToRemove = new ArrayList<>();
            
            // 收集所有要删除的方法
            for (String signature : methodSignatures) {
                List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class, 
                    md -> matchesSignature(md, signature));
                methodsToRemove.addAll(methods);
            }
            
            if (!methodsToRemove.isEmpty()) {
                // 删除方法
                for (MethodDeclaration method : methodsToRemove) {
                    method.remove();
                    System.out.println("✓ 删除方法: " + buildMethodSignature(method) + " 从文件: " + filePath.getFileName());
                }
                
                // 保存修改
                Files.write(filePath, cu.toString().getBytes());
                System.out.println("✓ 更新文件: " + filePath);
                
                return methodsToRemove.size();
            }
            
        } catch (Exception e) {
            System.err.println("处理文件错误: " + filePath + " - " + e.getMessage());
        }
        return 0;
    }
    
    private static boolean matchesSignature(MethodDeclaration method, String targetSignature) {
        String methodSignature = buildMethodSignature(method);
        return methodSignature.equals(targetSignature);
    }
    
    private static String buildMethodSignature(MethodDeclaration method) {
        StringBuilder signature = new StringBuilder();
        
        // 获取完整的类名（包含包名）
        method.findCompilationUnit().ifPresent(cu -> {
            cu.getPackageDeclaration().ifPresent(pkg -> {
                signature.append(pkg.getNameAsString());
                signature.append(".");
            });
        });
        
        // 获取类名
        method.findAncestor(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class)
            .ifPresent(classDecl -> {
                signature.append(classDecl.getNameAsString());
                signature.append(".");
            });
        
        // 方法名
        signature.append(method.getNameAsString());
        
        // 参数列表
        signature.append("(");
        if (!method.getParameters().isEmpty()) {
            method.getParameters().forEach(param -> {
                signature.append(param.getType().asString());
                signature.append(",");
            });
            signature.deleteCharAt(signature.length() - 1); // 删除最后一个逗号
        }
        signature.append(")");
        
        return signature.toString();
    }
}
