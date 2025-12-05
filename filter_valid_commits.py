import json
import os
import glob

def filter_commits(input_file, output_file):
    # 读取输入的JSON文件
    with open(input_file, 'r') as f:
        commits = json.load(f)
    
    valid_commits = []
    
    for commit_data in commits:
        # 提取focal_methods和test_methods，分别记录新旧版本
        focal_methods = {"new": set(), "old": set()}
        test_methods = {"new": set(), "old": set()}
        
        # 从changed_methods中提取方法
        if 'changed_methods' in commit_data:
            for file_path, methods in commit_data['changed_methods'].items():
                for method in methods:
                    # 只处理方法类型
                    if method['element_type'] in ['METHOD', 'CONSTRUCTOR']:
                        element_name = method['element_name']
                        version = method['version']
                        
                        # 判断是否为测试方法：路径包含"test"或方法名包含"test"
                        if 'test' in file_path.lower() or 'test' in element_name.lower():
                            if version == "NEW":
                                test_methods["new"].add(element_name)
                            else:  # OLD
                                test_methods["old"].add(element_name)
                        else:
                            if version == "NEW":
                                focal_methods["new"].add(element_name)
                            else:  # OLD
                                focal_methods["old"].add(element_name)
        
        # 从changed_constructors中提取构造函数
        if 'changed_constructors' in commit_data:
            for file_path, constructors in commit_data['changed_constructors'].items():
                for constructor in constructors:
                    # 只处理CONSTRUCTOR类型
                    if constructor['element_type'] == 'CONSTRUCTOR':
                        element_name = constructor['element_name']
                        version = constructor['version']
                        
                        # 判断是否为测试方法：路径包含"test"或方法名包含"test"
                        if 'test' in file_path.lower() or 'test' in element_name.lower():
                            if version == "NEW":
                                test_methods["new"].add(element_name)
                            else:  # OLD
                                test_methods["old"].add(element_name)
                        else:
                            if version == "NEW":
                                focal_methods["new"].add(element_name)
                            else:  # OLD
                                focal_methods["old"].add(element_name)
        
        # 转换为列表并去重
        focal_methods["new"] = list(focal_methods["new"])
        focal_methods["old"] = list(focal_methods["old"])
        test_methods["new"] = list(test_methods["new"])
        test_methods["old"] = list(test_methods["old"])
        
        # 计算总数（新旧版本合并）
        total_focal_count = len(focal_methods["new"]) + len(focal_methods["old"])
        total_test_count = len(test_methods["new"]) + len(test_methods["old"])
        
        # 筛选条件：
        # 1. focal_methods和test_methods均不为空
        # 2. focal_methods和test_methods至少有一个个数为2
        if (total_focal_count > 0 and total_test_count > 0 and  # 条件1：都不为空
            (total_focal_count >= 2 or total_test_count >= 2)):  # 条件2：至少有一个个数为2
            
            # 创建commit对象
            commit_obj = {
                "commit": {
                    "sha1": commit_data['commit_id']
                },
                "focal_methods": focal_methods,
                "test_methods": test_methods
            }
            
            valid_commits.append(commit_obj)
    
    # 确保输出目录存在
    output_dir = os.path.dirname(output_file)
    if output_dir and not os.path.exists(output_dir):
        os.makedirs(output_dir)
    
    # 写入输出文件
    with open(output_file, 'w') as f:
        json.dump(valid_commits, f, indent=2)
    
    print(f"筛选完成！符合条件的commit数量：{len(valid_commits)}")
    print(f"结果已保存到：{output_file}")
    
    # 打印一些统计信息
    if valid_commits:
        total_focal_new = sum(len(commit['focal_methods']['new']) for commit in valid_commits)
        total_focal_old = sum(len(commit['focal_methods']['old']) for commit in valid_commits)
        total_tests_new = sum(len(commit['test_methods']['new']) for commit in valid_commits)
        total_tests_old = sum(len(commit['test_methods']['old']) for commit in valid_commits)
        
        print(f"总focal_methods数量 - 新版本: {total_focal_new}, 旧版本: {total_focal_old}")
        print(f"总test_methods数量 - 新版本: {total_tests_new}, 旧版本: {total_tests_old}")


# 使用示例
if __name__ == "__main__":
    input_dir = "/home/hk/ai4se/bin/code-diff-analyzer/code_changes"
    output_dir = "./validcommits"
    
    # 确保输出目录存在
    os.makedirs(output_dir, exist_ok=True)
    
    # 扫描输入目录下的所有json文件
    json_files = glob.glob(os.path.join(input_dir, "*.json"))
    
    for input_file in json_files:
        # 从输入文件名提取项目名
        filename = os.path.basename(input_file)
        project_name = filename.replace("_code_changes.json", "")

        output_file = os.path.join(output_dir, f"{project_name}-valid.json")
        
        filter_commits(input_file, output_file)

