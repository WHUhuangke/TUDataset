Git 仓库管理说明
# 项目概述
本仓库用于分析Java项目的代码变更历史，特别关注测试方法与焦点方法之间的覆盖关系，并进行相关分支操作。
# 系统要求

Java OpenJDK 21
Maven 3.6.3
Git
Python 3.x


# 数据处理流程

1.代码变更分析​ → 2. 提交筛选​ → 3. 覆盖对筛选​ → 4. 分支构建​ → 5. 测试执行

# 使用流程
## 1. 准备目标项目

# 将需要分析的项目clone到目标目录
git clone <repository_url> /TUDataset/TUGraph-main/target_projects/<project_name>

## 2. 运行代码差异分析

# 进入分析工具目录
cd /TUDataset/code-diff-analyzer

# 将项目路径写入配置文件
echo "/TUDataset/TUGraph-main/target_projects/<project_name>" > repo_list.txt

# 编译并运行分析工具
mvn compile exec:java -Dexec.mainClass="CodeReviewTool" -Dexec.args="./repo_list.txt"

输出结果: /TUDataset/code-diff-analyzer/code_changes/
## 3. 筛选有效提交

# 运行初步筛选脚本
python filter_valid_commits.py

输出结果: /TUDataset/validcommits/
## 4. 筛选覆盖的提交对

# 运行覆盖对筛选脚本（支持断点续跑）
python filter_covered_commits.py

输出结果: /TUDataset/covered_pairs/
## 5. 构建和测试分支

# 进入Docker环境
cd /java-evolution-project

# 构建Docker镜像
docker build -t java-evolution .

# 运行Docker容器
docker run -it --rm java-evolution

# 在容器内运行分支恢复脚本
python revert_branch.py

## 6. 运行特定测试

# 进入项目目录
cd /java-evolution-project/commons-csv

# 查看分支
git branch

# 查看当前提交
git log --oneline -1

# 运行筛选出的测试方法
mvn test -Dtest=org.apache.commons.csv.CSVFormatTest#testDelimiterCharLineBreakLfThrowsException1 -DskipTests=false

# 重要脚本说明
filter_valid_commits.py

功能：筛选存在多个测试方法和多个焦点方法修改的commit及其父commit
输入：/TUDataset/code-diff-analyzer/code_changes/

输出：/TUDataset/validcommits/

filter_covered_commits.py

功能：提取满足覆盖条件的有效commit
条件：

存在2对及以上new test method覆盖new focal method

    存在2对及以上old test method覆盖old focal method

特性：支持断点续跑
断点存储：/progress/

    输出：/TUDataset/covered_pairs/

revert_branch.py

功能：依据筛选的有效commit，从当前commit出发构建新分支
操作：创建master-revert-test-changes分支

    位置：/java-evolution-project/目录内


