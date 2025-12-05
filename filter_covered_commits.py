import json
import os
import subprocess
import xml.etree.ElementTree as ET
from typing import Dict, List, Any
import time
import sys
import re
from pathlib import Path
import shutil

class ProgressTracker:
    """进度跟踪器 - 实现断点续跑功能"""
    
    def __init__(self, progress_dir="/home/hk/ai4se/bin/progress"):
        self.progress_dir = Path(progress_dir)
        self.progress_dir.mkdir(parents=True, exist_ok=True)
    
    def get_progress_file(self, project_name):
        """获取项目进度文件路径"""
        return self.progress_dir / f"{project_name}_progress.json"
    
    def load_progress(self, project_name):
        """加载项目进度"""
        progress_file = self.get_progress_file(project_name)
        if progress_file.exists():
            try:
                with open(progress_file, 'r', encoding='utf-8') as f:
                    return json.load(f)
            except Exception as e:
                print(f"ERROR: 加载进度文件失败: {e}")
        return {
            "project_name": project_name,
            "status": "not_started",
            "processed_commits": [],
            "current_index": 0,
            "start_time": time.time(),
            "last_update": time.time(),
            "total_commits": 0,
            "successful_commits": 0
        }
    
    def save_progress(self, project_name, progress_data):
        """保存项目进度"""
        progress_file = self.get_progress_file(project_name)
        progress_data["last_update"] = time.time()
        try:
            with open(progress_file, 'w', encoding='utf-8') as f:
                json.dump(progress_data, f, indent=2, ensure_ascii=False)
            print(f"DEBUG: 进度已保存到: {progress_file}")
            return True
        except Exception as e:
            print(f"ERROR: 保存进度文件失败: {e}")
            return False
    
    def is_commit_processed(self, project_name, commit_sha):
        """检查commit是否已处理"""
        progress = self.load_progress(project_name)
        return commit_sha in progress.get("processed_commits", [])

class DynamicSaver:
    """动态保存器 - 实现实时保存结果"""
    
    def __init__(self, output_dir="/home/hk/ai4se/bin/covered_pairs"):
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(parents=True, exist_ok=True)
    
    def get_output_file(self, project_name):
        """获取输出文件路径"""
        return self.output_dir / f"{project_name}_covered_pairs.json"
    
    def append_result(self, project_name, result):
        """追加单个结果到输出文件"""
        output_file = self.get_output_file(project_name)
        
        # 如果文件不存在，创建空列表
        if not output_file.exists():
            try:
                with open(output_file, 'w', encoding='utf-8') as f:
                    json.dump([], f, indent=2, ensure_ascii=False)
            except Exception as e:
                print(f"ERROR: 创建输出文件失败: {e}")
                return False
        
        # 读取现有结果
        try:
            with open(output_file, 'r', encoding='utf-8') as f:
                existing_results = json.load(f)
        except Exception as e:
            print(f"ERROR: 读取现有结果失败: {e}")
            existing_results = []
        
        # 检查是否已存在相同commit的结果，如果存在则更新，否则追加
        commit_exists = False
        for i, existing_result in enumerate(existing_results):
            if existing_result.get('commit') == result['commit']:
                existing_results[i] = result  # 更新现有结果
                commit_exists = True
                break
        
        if not commit_exists:
            existing_results.append(result)  # 添加新结果
        
        # 保存更新后的结果
        try:
            with open(output_file, 'w', encoding='utf-8') as f:
                json.dump(existing_results, f, indent=2, ensure_ascii=False)
            print(f"DEBUG: 结果已动态保存到: {output_file} (当前总数: {len(existing_results)})")
            return True
        except Exception as e:
            print(f"ERROR: 动态保存结果失败: {e}")
            return False

def java_type_to_descriptor(java_type: str) -> str:
    """将Java类型转换为JVM描述符部分"""
    java_type = java_type.strip()
    primitive_map = {
        'boolean': 'Z',
        'byte': 'B',
        'char': 'C',
        'double': 'D',
        'float': 'F',
        'int': 'I',
        'long': 'J',
        'short': 'S',
        'void': 'V'
    }
    if java_type in primitive_map:
        return primitive_map[java_type]
    else:
        return 'L' + java_type.replace('.', '/') + ';'

def params_to_desc_prefix(params_str: str) -> str:
    """将参数列表字符串转换为JVM描述符前缀"""
    if not params_str or params_str == "":
        return "()"
    params = [p.strip() for p in params_str.split(',')]
    desc_parts = [java_type_to_descriptor(p) for p in params]
    return "(" + "".join(desc_parts) + ")"

def parse_method_id(method_id: str) -> (str, str, str):
    """解析方法ID，返回类名、方法名和参数字符串（参数不带括号）"""
    method_part = method_id.split('(')[0]
    params_str = method_id.split('(')[1].rstrip(')')
    
    if '.' in method_part:
        last_dot = method_part.rfind('.')
        class_name = method_part[:last_dot]
        method_name = method_part[last_dot+1:]
        return class_name, method_name, params_str
    else:
        print(f"ERROR: 方法ID格式错误，缺少类名: {method_id}")
        return None, None, None

import subprocess
import time
import os
import shlex
from typing import List

def run_command(cmd: List[str], cwd: str) -> bool:
    """在Ubuntu上运行命令"""
    print(f"DEBUG: 执行命令: {' '.join(cmd)}")
    print(f"DEBUG: 工作目录: {cwd}")
    
    # Java 8路径
    java8_home = '/usr/lib/jvm/java-8-openjdk-amd64'
    
    # 首先检测系统信息和当前Java版本
    #print("DEBUG: === 系统环境信息 ===")
    
    # 检测当前shell
    shell_result = subprocess.run(["echo", "$SHELL"], capture_output=True, text=True)
    # print(f"DEBUG: 当前shell: {shell_result.stdout.strip() if shell_result.stdout else '未知'}")
    
    # 检测当前Java版本
    # print("DEBUG: 当前Java版本信息:")
    java_version_cmd = "java -version 2>&1"
    java_result = subprocess.run(java_version_cmd, shell=True, executable="/bin/bash", 
                                capture_output=True, text=True)
    for line in (java_result.stdout or java_result.stderr or "").split('\n'):
        if line.strip():
            print(f"DEBUG: {line}")
    
    # 检测当前JAVA_HOME
    java_home = os.environ.get('JAVA_HOME', '未设置')
    print(f"DEBUG: 当前JAVA_HOME: {java_home}")
    
    
    start_time = time.time()
    
    try:
        # 将命令列表转换为适合bash的字符串
        cmd_str = ' '.join(shlex.quote(arg) for arg in cmd)
        
        # 构建切换到Java 8并执行命令的完整命令
        # 设置JAVA_HOME并将Java 8的bin目录添加到PATH最前面
        java_setup_commands = [
            f"cd '{cwd}' && {cmd_str}"  # 切换到工作目录并执行命令
        ]
        
        full_cmd = '; '.join(java_setup_commands)
        # print(f"DEBUG: 完整执行的bash命令:\n{full_cmd}")
        
        # 使用bash执行完整的命令序列
        result = subprocess.run(
            full_cmd,
            shell=True,
            executable="/bin/bash",
            capture_output=True, 
            text=True,
            # 注意：这里不指定cwd，因为我们在命令中已经cd了
            env=os.environ
        )
        
        end_time = time.time()
        execution_time = end_time - start_time
        
        print(f"DEBUG: 命令执行时间: {execution_time:.2f}秒")
        print(f"DEBUG: 返回码: {result.returncode}")
        
        
        success = result.returncode == 0
        print(f"DEBUG: 命令执行{'成功' if success else '失败'}")
        return success
        
    except FileNotFoundError as e:
        end_time = time.time()
        execution_time = end_time - start_time
        print(f"ERROR: 命令未找到: {cmd}, 错误: {e}")
        print(f"DEBUG: 请确保命令在PATH中或使用绝对路径")
        print(f"DEBUG: 异常执行时间: {execution_time:.2f}秒")
        return False
    except Exception as e:
        end_time = time.time()
        execution_time = end_time - start_time
        print(f"ERROR: 命令执行异常: {cmd}, 错误: {e}")
        print(f"DEBUG: 异常执行时间: {execution_time:.2f}秒")
        return False

def check_jacoco_coverage(jacoco_xml_path, focal_methods):
    """检查JaCoCo报告中哪些focal_methods被覆盖，返回被覆盖的方法列表"""
    print(f"DEBUG: 检查JaCoCo覆盖 - 文件: {jacoco_xml_path}")
    
    if not os.path.exists(jacoco_xml_path):
        print(f"ERROR: JaCoCo报告文件不存在: {jacoco_xml_path}")
        return []
    
    covered_methods = []
    
    try:
        tree = ET.parse(jacoco_xml_path)
        root = tree.getroot()
        
        for focal_method_id in focal_methods:
            focal_class, focal_method, params_str = parse_method_id(focal_method_id)
            if not focal_class or not focal_method:
                print(f"ERROR: 无法解析焦点方法ID: {focal_method_id}")
                continue
            
            params_prefix = params_to_desc_prefix(params_str) if params_str else ''
            internal_class_name = focal_class.replace('.', '/')
            
            class_found = False
            method_found = False
            coverage_found = False
            
            for cls in root.findall('.//class'):
                cls_name = cls.get('name')
                
                if cls_name == internal_class_name:
                    class_found = True
                    for method in cls.findall('method'):
                        method_name = method.get('name')
                        method_desc = method.get('desc', '')
                        if (method_name == focal_method and 
                            method_desc.startswith(params_prefix)):
                            method_found = True
                            counter = method.find('counter')
                            if counter is not None:
                                counter_type = counter.get('type')
                                covered = int(counter.get('covered', 0))
                                if counter_type == 'INSTRUCTION' and covered > 0:
                                    coverage_found = True
                                    covered_methods.append(focal_method_id)
                                    # print(f"DEBUG:    方法被覆盖: {focal_method_id}")
                                    break
                    break
            
            if not coverage_found:
                pass
                # print(f"DEBUG: 方法未被覆盖: {focal_method_id}")
        
        print(f"DEBUG: 总共覆盖了 {len(covered_methods)} 个方法")
        return covered_methods
        
    except Exception as e:
        print(f"ERROR: 解析JaCoCo报告失败: {e}")
        import traceback
        traceback.print_exc()
        return []

def get_commit_info(commit_hash: str, project_path: str) -> str:
    """获取commit的详细信息"""
    try:
        result = subprocess.run(['git', 'show', '--oneline', '-s', commit_hash], 
                              cwd=project_path, capture_output=True, text=True)
        if result.returncode == 0:
            return result.stdout.strip()
    except Exception as e:
        print(f"DEBUG: 获取commit信息失败: {e}")
    return f"Commit: {commit_hash}"
    
def modify_parent_version(pom_path):
    """修改parent内的version标签内容"""
    try:
        with open(pom_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # 匹配parent内的version标签，如果是-SNAPSHOT结尾则删除-SNAPSHOT部分
        pattern = r'(<parent>.*?<version>)([^<]*?)-SNAPSHOT(</version>.*?</parent>)'
        replacement = r'\g<1>\g<2>\g<3>'
        modified_content = re.sub(pattern, replacement, content, flags=re.DOTALL)
        
        # 检查是否实际修改了内容
        if content != modified_content:
            with open(pom_path, 'w', encoding='utf-8') as f:
                f.write(modified_content)
            print(f"成功删除parent版本中的-SNAPSHOT: {pom_path}")
            return True
        else:
            print(f"未找到需要修改的-SNAPSHOT版本: {pom_path}")
            return False
            
    except Exception as e:
        print(f"修改parent版本时出错 {pom_path}: {str(e)}")
        return False

def modify_java_version(pom_path):
    """根据pom.xml中的Java版本配置设置对应的JDK环境（在当前进程中设置）"""
    try:
        with open(pom_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # 提取javac.src.version的值
        src_version_match = re.search(r'<javac\.src\.version>([^<]+)</javac\.src\.version>', content)
        # 提取javac.target.version的值
        target_version_match = re.search(r'<javac\.target\.version>([^<]+)</javac\.target\.version>', content)
        
        if not src_version_match and not target_version_match:
            print(f"未找到javac.src.version或javac.target.version配置: {pom_path}")
            return False
        
        # 优先使用src.version，如果没有则使用target.version
        if src_version_match:
            java_version = src_version_match.group(1).strip()
        else:
            java_version = target_version_match.group(1).strip()
        
        print(f"从pom.xml中读取到Java版本: {java_version}")
        
        # 规范化版本号（处理1.8和8的等价情况）
        if java_version == '1.8':
            java_version = '8'
        
        # 可用的Java版本路径
        java_versions = {
            '8': '/usr/lib/jvm/java-8-openjdk-amd64',
            '17': '/usr/lib/jvm/java-17-openjdk-amd64', 
            '21': '/usr/lib/jvm/java-21-openjdk-amd64'
        }
        
        # 检查读取的版本是否在可用版本中，如果不在则使用Java 8
        if java_version not in java_versions:
            print(f"版本 {java_version} 不在可用版本中(8,17,21)，使用默认版本8")
            java_version = '8'
        
        java_home = java_versions[java_version]
        
        if not os.path.exists(java_home):
            print(f"Java Home路径不存在: {java_home}")
            return False
        
        # 关键修改：在当前Python进程中设置环境变量
        os.environ['JAVA_HOME'] = java_home
        # 更新PATH，确保java命令使用正确的版本
        os.environ['PATH'] = f"{java_home}/bin:{os.environ.get('PATH', '')}"
        
        print(f"成功设置Java版本为: {java_version}")
        
        # 验证设置
        print("DEBUG: 验证当前Java版本:")
        result = subprocess.run(['java', '-version'], capture_output=True, text=True)
        # java -version 输出到stderr
        for line in (result.stderr or result.stdout or "").split('\n'):
            if line.strip():
                print(f"DEBUG: {line}")
        
        print(f"DEBUG: 当前JAVA_HOME: {os.environ.get('JAVA_HOME')}")
        
        return True
            
    except Exception as e:
        print(f"处理pom.xml时出错 {pom_path}: {str(e)}")
        return False


        
        
def configure_jacoco_in_pom(pom_file_path):
    """直接通过文本查找和替换在pom.xml中配置JaCoCo插件（强制配置）"""
    try:
        with open(pom_file_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        jacoco_config = """        <plugin>
            <groupId>org.jacoco</groupId>
            <artifactId>jacoco-maven-plugin</artifactId>
            <version>0.8.11</version>
            <executions>
                <execution>
                    <id>prepare-agent</id>
                    <goals>
                        <goal>prepare-agent</goal>
                    </goals>
                </execution>
                <execution>
                    <id>report</id>
                    <phase>test</phase>
                    <goals>
                        <goal>report</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>"""
        
        # 检查是否已存在jacoco插件，如果存在则先删除旧配置
        if 'jacoco-maven-plugin' in content:
            print("INFO: 检测到已有JaCoCo插件，进行强制替换")
            
            # 使用正则表达式匹配并删除现有的jacoco插件配置
            jacoco_pattern = r'<plugin>\s*<groupId>org\.jacoco</groupId>\s*<artifactId>jacoco-maven-plugin</artifactId>.*?</plugin>'
            content = re.sub(jacoco_pattern, '', content, flags=re.DOTALL)
            
            # 清理可能留下的空行
            content = re.sub(r'\n\s*\n', '\n', content)
        
        build_pattern = r'<build>(.*?)</build>'
        build_match = re.search(build_pattern, content, re.DOTALL)
        
        if build_match:
            build_content = build_match.group(1)
            plugins_pattern = r'<plugins>(.*?)</plugins>'
            plugins_match = re.search(plugins_pattern, build_content, re.DOTALL)
            
            if plugins_match:
                plugins_content = plugins_match.group(1)
                plugin_pattern = r'</plugin>'
                plugin_matches = list(re.finditer(plugin_pattern, plugins_content))
                
                if plugin_matches:
                    last_plugin_end = plugin_matches[-1].end()
                    new_plugins_content = (plugins_content[:last_plugin_end] + 
                                         '\n' + jacoco_config + 
                                         plugins_content[last_plugin_end:])
                else:
                    new_plugins_content = '\n' + jacoco_config + plugins_content
                
                new_build_content = build_content.replace(
                    plugins_match.group(0), 
                    f'<plugins>{new_plugins_content}</plugins>'
                )
                
                new_content = content.replace(
                    build_match.group(0),
                    f'<build>{new_build_content}</build>'
                )
            else:
                new_build_content = build_content + f'\n    <plugins>\n{jacoco_config}\n    </plugins>'
                new_content = content.replace(
                    build_match.group(0),
                    f'<build>{new_build_content}</build>'
                )
        else:
            build_plugins_content = f"""    <build>
        <plugins>{jacoco_config}
        </plugins>
    </build>"""
            
            if '</project>' in content:
                new_content = content.replace('</project>', build_plugins_content + '\n</project>')
            else:
                new_content = content + '\n' + build_plugins_content
        
        with open(pom_file_path, 'w', encoding='utf-8') as f:
            f.write(new_content)
        
        print(f"SUCCESS: JaCoCo插件已成功配置到 {pom_file_path}")
        return True
        
    except Exception as e:
        print(f"ERROR: 配置JaCoCo插件时发生错误: {e}")
        import traceback
        traceback.print_exc()
        return False

def process_single_commit(commit_info, project_path, progress_tracker, dynamic_saver, project_name):
    """处理单个commit"""
    commit_id = commit_info['commit']['sha1']
    
    # 检查是否已处理
    if progress_tracker.is_commit_processed(project_name, commit_id):
        print(f"跳过已处理的commit: {commit_id}")
        return True
    
    new_focal_methods = commit_info['focal_methods']['new']
    new_test_methods = commit_info['test_methods']['new']
    old_focal_methods = commit_info['focal_methods']['old']
    old_test_methods = commit_info['test_methods']['old']
    
    print("\n" + "=" * 40)
    print(f"处理commit: {commit_id}")
    print(f"New版本测试方法数: {len(new_test_methods)}")
    print(f"New版本焦点方法数: {len(new_focal_methods)}")
    print(f"Old版本测试方法数: {len(old_test_methods)}")
    print(f"Old版本焦点方法数: {len(old_focal_methods)}")
    print("=" * 40)
    if len(new_test_methods) >= 40 or len(new_focal_methods) >= 40 or len(old_test_methods) >= 40 or len(old_focal_methods) >= 40 or len(old_test_methods) ==0 or         len(new_test_methods) == 0 or len(new_focal_methods) == 0  or len(old_focal_methods) == 0:
        print("方法数不符合要求")
        return False
    new_pairs = []
    old_pairs = []
    status = "success"
    reason = ""
    
    try:
    
        # 步骤1: 切换到当前commit
        print(f"\n步骤1: 切换到当前commit {commit_id}")
        if not run_command(['git', 'reset', '--hard', commit_id], project_path):
            print(f"ERROR: 切换commit失败: {commit_id}")
            status = "failed"
            reason = "failed_to_switch_commit"
            return False
        
        # 清理之前的构建结果

        
        # 配置jacoco
        
        print(f"DEBUG: 配置xml")
        configure_jacoco_in_pom(os.path.join(project_path, 'pom.xml'))
        modify_java_version(os.path.join(project_path, 'pom.xml'))
        modify_parent_version(os.path.join(project_path, 'pom.xml'))
        # 步骤2: 执行new版本的测试方法并检查覆盖
        print(f"\n步骤2: 执行New版本测试方法并检查覆盖")
        new_coverage_count = 0
        
        for test_method_id in new_test_methods:
            print(f"DEBUG: 清理构建结果...")

            run_command(['mvn', 'clean'], project_path)
            test_class, test_method, _ = parse_method_id(test_method_id)
            if not test_class or not test_method:
                print(f"ERROR: 无法解析测试方法ID: {test_method_id}")
                continue
            jacoco_path = os.path.join(project_path, 'target', 'site')

            # 直接尝试删除，如果文件夹不存在会抛出异常，可以捕获处理
            try:
                shutil.rmtree(jacoco_path)
                
            except FileNotFoundError:
                print(f"文件夹不存在: {jacoco_path}")
            except Exception as e:
                print(f"删除失败: {e}")
            # 运行指定测试方法
            print(f"\n运行测试方法: {test_method_id}")
            test_cmd = ['mvn', 'test', f'-Dtest={test_class}#{test_method}', '-DskipTests=false']
            if not run_command(test_cmd, project_path):
                print(f"ERROR: 测试运行失败: {test_method_id}")
                continue
            
            # 检查JaCoCo覆盖
            jacoco_xml_path = os.path.join(project_path, 'target', 'site', 'jacoco', 'jacoco.xml')
            covered_methods = check_jacoco_coverage(jacoco_xml_path, new_focal_methods)
            
            # 记录覆盖的pairs
            for focal_method_id in covered_methods:
                new_pairs.append({
                    'test_method_id': test_method_id,
                    'focal_method_id': focal_method_id
                })
                new_coverage_count += 1
                # print(f"覆盖pair: {test_method_id} -> {focal_method_id}")
        
        print(f"\nNew版本覆盖统计: {new_coverage_count} 个pairs")
        
        # 如果new版本覆盖数小于2，则早停
        if new_coverage_count < 2:
            print(f"早停: New版本覆盖数 {new_coverage_count} < 2")
            status = "failed"
            reason = "new_coverage_less_than_2"
            result = {
                'commit': commit_id,
                'new_pairs': [],
                'old_pairs': [],
                'status': status,
                'reason': reason
            }
            dynamic_saver.append_result(project_name, result)
            return False
        
        # 步骤3: 切换到前一个commit
        prev_commit = commit_id + '~1'
        print(f"\n步骤3: 切换到前一个commit {prev_commit}")
        prev_commit_info = get_commit_info(prev_commit, project_path)
        print(f"前一个commit信息: {prev_commit_info}")
        
        if not run_command(['git', 'reset', '--hard', prev_commit], project_path):
            print(f"ERROR: 切换前一个commit失败: {prev_commit}")
            status = "failed"
            reason = "failed_to_switch_to_prev_commit"
            result = {
                'commit': commit_id,
                'new_pairs': [],
                'old_pairs': [],
                'status': status,
                'reason': reason
            }
            dynamic_saver.append_result(project_name, result)
            return False

        # 清理构建结果
        run_command(['mvn', 'clean'], project_path)

        # 配置jacoco
        configure_jacoco_in_pom(os.path.join(project_path, 'pom.xml'))
        modify_java_version(os.path.join(project_path, 'pom.xml'))
        modify_parent_version(os.path.join(project_path, 'pom.xml'))
        # 步骤4: 执行old版本的测试方法并检查覆盖
        print(f"\n步骤4: 执行Old版本测试方法并检查覆盖")
        old_coverage_count = 0
        
        for test_method_id in old_test_methods:
            test_class, test_method, _ = parse_method_id(test_method_id)
            if not test_class or not test_method:
                print(f"ERROR: 无法解析测试方法ID: {test_method_id}")
                continue
            
            # 运行指定测试方法
            print(f"\n运行测试方法: {test_method_id}")
            test_cmd = ['mvn', 'test', f'-Dtest={test_class}#{test_method}', '-DskipTests=false']
            if not run_command(test_cmd, project_path):
                print(f"ERROR: 测试运行失败: {test_method_id}")
                continue
            
            # 检查JaCoCo覆盖
            jacoco_xml_path = os.path.join(project_path, 'target', 'site', 'jacoco', 'jacoco.xml')
            covered_methods = check_jacoco_coverage(jacoco_xml_path, old_focal_methods)
            
            # 记录覆盖的pairs
            for focal_method_id in covered_methods:
                old_pairs.append({
                    'test_method_id': test_method_id,
                    'focal_method_id': focal_method_id
                })
                old_coverage_count += 1
                print(f"覆盖pair: {test_method_id} -> {focal_method_id}")
        
        print(f"\nOld版本覆盖统计: {old_coverage_count} 个pairs")
        
        # 如果old版本覆盖数小于2，则跳过
        if old_coverage_count < 2:
            print(f"Old版本覆盖数 {old_coverage_count} < 2")
            status = "failed"
            reason = "old_coverage_less_than_2"
            result = {
                'commit': commit_id,
                'new_pairs': [],
                'old_pairs': [],
                'status': status,
                'reason': reason
            }
            dynamic_saver.append_result(project_name, result)
            return False
        
        # 两个版本都满足条件，保存结果
        print(f"\n✓ Commit {commit_id} 通过验证")
        result = {
            'commit': commit_id,
            'new_pairs': new_pairs,
            'old_pairs': old_pairs,
            'status': 'success'
        }
        dynamic_saver.append_result(project_name, result)
        return True
        
    except Exception as e:
        print(f"ERROR: 处理commit {commit_id} 时发生异常: {e}")
        import traceback
        traceback.print_exc()
        # 记录异常情况
        result = {
            'commit': commit_id,
            'new_pairs': [],
            'old_pairs': [],
            'status': 'error',
            'error_message': str(e)
        }
        dynamic_saver.append_result(project_name, result)
        return False

def main():
    # 配置基础路径
    projects_root = "/home/hk/ai4se/bin/TUGraph-main/target_projects"
    valid_commits_dir = "/home/hk/ai4se/bin/validcommits"
    
    # 初始化进度跟踪器和动态保存器
    progress_tracker = ProgressTracker("/home/hk/ai4se/bin/progress")
    dynamic_saver = DynamicSaver("/home/hk/ai4se/bin/covered_pairs")
    
    # 获取所有项目目录
    project_dirs = [d for d in os.listdir(projects_root) 
                   if os.path.isdir(os.path.join(projects_root, d))]
    
    print("=" * 80)
    print("多项目COMMIT覆盖验证程序启动（重构版）")
    print(f"发现 {len(project_dirs)} 个项目需要处理")
    print("=" * 80)
    
    # 保存原始工作目录
    original_cwd = os.getcwd()
    
    for project_name in project_dirs:
        project_path = os.path.join(projects_root, project_name)
        valid_commits_path = os.path.join(valid_commits_dir, f"{project_name}-valid.json")
        
        print("\n" + "=" * 60)
        print(f"开始处理项目: {project_name}")
        print(f"项目路径: {project_path}")
        print(f"有效commits文件: {valid_commits_path}")
        print("=" * 60)
        
        # 检查有效commits文件是否存在
        if not os.path.exists(valid_commits_path):
            print(f"WARNING: 有效commits文件不存在，跳过项目: {project_name}")
            continue
        
        # 读取有效commits JSON文件
        try:
            with open(valid_commits_path, 'r', encoding='utf-8') as f:
                commits_data = json.load(f)
            print(f"DEBUG: 成功读取 {len(commits_data)} 个commit")
        except Exception as e:
            print(f"ERROR: 读取有效commits文件失败: {e}")
            continue
        
        total_commits = len(commits_data)
        
        # 加载项目进度
        progress = progress_tracker.load_progress(project_name)
        
        # 如果是新项目，初始化进度
        if progress["status"] == "not_started":
            progress = {
                "project_name": project_name,
                "status": "running",
                "processed_commits": [],
                "current_index": 0,
                "start_time": time.time(),
                "last_update": time.time(),
                "total_commits": total_commits,
                "successful_commits": 0
            }
        
        # 切换到项目目录
        print(f"DEBUG: 切换到项目目录: {project_path}")
        os.chdir(project_path)
        
        # 检查git仓库状态
        run_command(['git', 'status', '--short'], project_path)
        
        processed_in_this_run = 0
        successful_in_this_run = 0
        project_start_time = time.time()
        
        # 从上次中断的地方继续处理
        start_index = progress.get("current_index", 0)
        
        for commit_idx in range(start_index, total_commits):
            
            commit_info = commits_data[commit_idx]
            commit_id = commit_info['commit']['sha1']

            # 更新当前处理进度
            progress["current_index"] = commit_idx
            progress["last_update"] = time.time()
            progress_tracker.save_progress(project_name, progress)
            
            # 处理单个commit
            success = process_single_commit(
                commit_info, project_path, progress_tracker, 
                dynamic_saver, project_name
            )
            
            # 更新处理结果
            processed_in_this_run += 1
            if success:
                successful_in_this_run += 1
                progress["successful_commits"] = progress.get("successful_commits", 0) + 1
            
            progress["processed_commits"].append(commit_id)
            progress_tracker.save_progress(project_name, progress)
            
            print(f"\n进度: [{commit_idx + 1}/{total_commits}] - 成功: {successful_in_this_run}/{processed_in_this_run}")
        
        # 切换回项目根目录
        os.chdir(original_cwd)
        print(f"DEBUG: 切换回原始目录: {original_cwd}")
        
        # 更新项目完成状态
        project_end_time = time.time()
        execution_time = project_end_time - project_start_time
        
        progress["status"] = "completed"
        progress["end_time"] = time.time()
        progress["execution_time_seconds"] = round(execution_time, 2)
        progress["progress_percentage"] = 100.0
        progress_tracker.save_progress(project_name, progress)
        
        print(f"\n" + "=" * 60)
        print(f"项目 {project_name} 验证完成")
        print(f"总共需要处理: {total_commits} 个commit")
        print(f"本次运行处理了: {processed_in_this_run} 个commit")
        print(f"成功处理的commit数: {successful_in_this_run}")
        print(f"执行时间: {execution_time:.2f} 秒")
        print("=" * 60)

if __name__ == "__main__":
    main()

