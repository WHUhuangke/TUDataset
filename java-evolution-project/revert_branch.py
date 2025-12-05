#!/usr/bin/env python3
"""
Git测试代码改动还原工具
用于将当前commit中涉及测试代码的改动还原，并创建新分支
"""

import os
import subprocess
import sys
from pathlib import Path
import argparse

class GitTestRevert:
    def __init__(self, repo_path="/workspace/commons-csv"):
        self.original_cwd = Path.cwd()
        self.repo_path = Path(repo_path).absolute()
        self.test_keywords = ['test', 'spec', 'test_', '_test', '.test.', '.spec.']
        
    def change_to_repo_directory(self):
        """切换到仓库目录"""
        try:
            if not self.repo_path.exists():
                print(f"错误: 目录不存在: {self.repo_path}")
                return False
            
            os.chdir(self.repo_path)
            print(f"已切换到目录: {self.repo_path}")
            return True
        except Exception as e:
            print(f"切换目录失败: {e}")
            return False
    
    def restore_original_directory(self):
        """恢复原始工作目录"""
        try:
            os.chdir(self.original_cwd)
        except Exception as e:
            print(f"恢复原始目录失败: {e}")
    
    def run_git_command(self, cmd, check=True):
        """运行git命令并返回结果"""
        try:
            result = subprocess.run(
                ['git'] + cmd,
                cwd=self.repo_path,
                capture_output=True,
                text=True,
                check=check
            )
            return result.stdout.strip()
        except subprocess.CalledProcessError as e:
            print(f"Git命令执行失败: {' '.join(cmd)}")
            print(f"错误信息: {e.stderr}")
            if check:
                raise
            return None
    
    def is_test_file(self, file_path):
        """判断文件是否为测试文件"""
        file_path_str = str(file_path).lower()
        return any(keyword in file_path_str for keyword in self.test_keywords)
    
    def get_current_commit_info(self):
        """获取当前commit信息"""
        commit_hash = self.run_git_command(['rev-parse', 'HEAD'])
        parent_hash = self.run_git_command(['rev-parse', 'HEAD^'])
        return commit_hash, parent_hash
    
    def get_diff_files(self):
        """获取当前commit与父commit的差异文件列表"""
        diff_output = self.run_git_command(['diff', 'HEAD^', 'HEAD', '--name-status'])
        if not diff_output:
            return []
        
        files = []
        for line in diff_output.split('\n'):
            if not line.strip():
                continue
            # 格式: status<TAB>filepath
            parts = line.split('\t')
            if len(parts) >= 2:
                status = parts[0]
                filepath = parts[1]
                files.append((status, filepath))
        
        return files
    
    def get_test_files_from_diff(self):
        """从diff中提取测试文件"""
        diff_files = self.get_diff_files()
        test_files = []
        
        for status, filepath in diff_files:
            if self.is_test_file(filepath):
                test_files.append((status, filepath))
        
        return test_files
    
    def create_new_branch(self, base_branch=None):
        """基于当前commit创建新分支"""
        if base_branch is None:
            # 获取当前分支名
            current_branch = self.run_git_command(['branch', '--show-current'])
            base_branch = current_branch if current_branch else 'main'
        
        new_branch_name = f"{base_branch}-revert-test-changes"
        
        # 检查分支是否已存在
        branches = self.run_git_command(['branch', '--list', new_branch_name])
        if branches:
            # 分支已存在，删除后重新创建
            self.run_git_command(['branch', '-D', new_branch_name])
        
        # 创建新分支
        self.run_git_command(['checkout', '-b', new_branch_name])
        return new_branch_name
    
    def revert_test_changes(self):
        """还原测试文件的改动"""
        test_files = self.get_test_files_from_diff()
        
        if not test_files:
            print("未找到测试文件的改动")
            return True
        
        print(f"找到 {len(test_files)} 个测试文件的改动:")
        for status, filepath in test_files:
            print(f"  {status}: {filepath}")
        
        # 还原每个测试文件的改动
        for status, filepath in test_files:
            try:
                if status == 'A':  # 新增文件，删除它
                    file_full_path = self.repo_path / filepath
                    if file_full_path.exists():
                        os.remove(file_full_path)
                        print(f"已删除新增的测试文件: {filepath}")
                elif status in ['M', 'D']:  # 修改或删除的文件，从父commit恢复
                    self.run_git_command(['checkout', 'HEAD^', '--', filepath])
                    print(f"已恢复文件: {filepath}")
                else:  # 重命名等操作
                    # 对于重命名，我们需要特殊处理
                    if 'R' in status:
                        # 提取源文件和目标文件
                        if len(status.split('\t')) > 2:
                            old_file = status.split('\t')[1]
                            new_file = status.split('\t')[2]
                            # 恢复原文件，删除新文件
                            self.run_git_command(['checkout', 'HEAD^', '--', old_file])
                            new_file_full_path = self.repo_path / new_file
                            if new_file_full_path.exists():
                                os.remove(new_file_full_path)
                    else:
                        print(f"未知的文件状态: {status}，跳过文件: {filepath}")
            except Exception as e:
                print(f"处理文件 {filepath} 时出错: {e}")
                return False
        
        return True
    
    def commit_changes(self, commit_message=None):
        """提交还原后的更改"""
        if commit_message is None:
            commit_hash = self.run_git_command(['rev-parse', '--short', 'HEAD'])
            commit_message = f"Revert test changes from commit {commit_hash}"
        
        # 检查是否有需要提交的更改
        status = self.run_git_command(['status', '--porcelain'])
        if not status:
            print("没有需要提交的更改")
            return True
        
        self.run_git_command(['add', '-A'])
        self.run_git_command(['commit', '-m', commit_message])
        print(f"已创建提交: {commit_message}")
        return True
    
    def execute(self, new_branch_name=None):
        """执行完整的还原流程"""
        print("开始处理Git测试代码还原...")
        
        # 切换到目标目录
        if not self.change_to_repo_directory():
            return False
        
        try:
            # 检查是否在git仓库中
            try:
                self.run_git_command(['status'])
            except subprocess.CalledProcessError:
                print(f"错误: 目录 {self.repo_path} 不是Git仓库")
                return False
            
            # 检查是否有父commit
            try:
                commit_hash, parent_hash = self.get_current_commit_info()
                print(f"当前commit: {commit_hash}")
                print(f"父commit: {parent_hash}")
            except subprocess.CalledProcessError:
                print("错误: 当前commit没有父commit（可能是初始commit）")
                return False
            
            # 创建新分支
            branch_name = self.create_new_branch(new_branch_name)
            print(f"已创建新分支: {branch_name}")
            
            # 还原测试文件改动
            if not self.revert_test_changes():
                return False
            
            # 提交更改
            if not self.commit_changes():
                return False
            
            print("测试代码还原完成！")
            print(f"新分支: {branch_name}")
            return True
            
        finally:
            # 无论成功与否，都恢复原始目录
            self.restore_original_directory()

def main():
    parser = argparse.ArgumentParser(description='还原Git仓库中测试代码的改动并创建新分支')
    parser.add_argument('--repo-path', default='/workspace/commons-csv', 
                       help='Git仓库路径（默认为/workspace/commons-csv）')
    parser.add_argument('--branch-name', help='新分支名称（默认为原分支名-revert-test-changes）')
    parser.add_argument('--add-keyword', action='append', help='添加测试文件关键词')
    
    args = parser.parse_args()
    
    try:
        revert_tool = GitTestRevert(args.repo_path)
        
        # 添加自定义关键词
        if args.add_keyword:
            revert_tool.test_keywords.extend([kw.lower() for kw in args.add_keyword])
        
        success = revert_tool.execute(args.branch_name)
        
        if not success:
            sys.exit(1)
            
    except Exception as e:
        print(f"执行过程中发生错误: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()

