#!/usr/bin/env python3
"""
数据生成器主程序
读取Excel模板，根据配置生成模拟数据并填充Excel文件
"""
import os
import sys
import argparse
import logging
from typing import Dict, List, Any, Optional
from pathlib import Path

# 添加项目根目录到Python路径
project_root = Path(__file__).parent
sys.path.insert(0, str(project_root))

from utils.excel_handler import ExcelHandler, ExcelTemplateGenerator
from utils.config_loader import ConfigManager
from generators.mock_value_generator import MockValueGenerator, AdvancedMockValueGenerator


class DataGenerator:
    """数据生成器主类"""
    
    def __init__(self, config_dir: str = "config", use_faker: bool = True):
        """初始化数据生成器
        
        Args:
            config_dir: 配置目录路径
            use_faker: 是否使用Faker生成更真实的数据
        """
        self.config_manager = ConfigManager(config_dir)
        self.excel_handler = None  # 将在需要时初始化
        
        # 选择数据生成器
        if use_faker:
            self.mock_generator = AdvancedMockValueGenerator()
        else:
            self.mock_generator = MockValueGenerator()
        
        # 设置日志
        self._setup_logging()

    def _setup_logging(self) -> None:
        """设置日志配置"""
        log_file = os.path.join(project_root, 'data_generator.log')
        logging.basicConfig(
            level=logging.INFO,
            format='%(asctime)s - %(levelname)s - %(message)s',
            handlers=[
                logging.FileHandler(log_file, encoding='utf-8'),
                logging.StreamHandler(sys.stdout)
            ]
        )
        self.logger = logging.getLogger(__name__)
        self.logger.info("数据生成器初始化完成")

    def validate_template(self, template_path: str) -> bool:
        """验证Excel模板文件"""
        try:
            handler = ExcelHandler(template_path)
            is_valid, errors = handler.validate_template()
            if not is_valid:
                self.logger.error("模板验证失败:")
                for err in errors:
                    self.logger.error(f"  - {err}")
            return is_valid
        except Exception as e:
            self.logger.error(f"验证模板时发生错误: {str(e)}")
            return False

    def get_config_summary(self) -> Dict[str, Any]:
        """获取配置摘要信息"""
        try:
            loader = self.config_manager.get_loader()
            return loader.get_config_summary()
        except Exception as e:
            self.logger.error(f"获取配置摘要失败: {str(e)}")
            return {}

    def generate_data_from_template(self, template_path: str, output_path: str, 
                                  rows_per_sheet: int = 100) -> None:
        """从Excel模板生成数据（仅使用JSON配置，集中一次性写入）
        
        Args:
            template_path: Excel模板文件路径
            output_path: 输出文件路径
            rows_per_sheet: 每个sheet生成的数据行数
        """
        try:
            self.logger.info(f"开始处理模板文件: {template_path}")
            
            # 初始化ExcelHandler
            self.excel_handler = ExcelHandler(template_path)
            
            # 读取Excel模板
            template_data = self.excel_handler.read_template()
            
            if not template_data:
                self.logger.warning("模板文件为空或无法读取")
                return
            
            # 获取配置加载器（仅JSON）
            config_loader = self.config_manager.get_loader()
            
            # 为每个sheet生成数据
            generated_data = {}
            
            for sheet_name, columns in template_data.items():
                self.logger.info(f"正在为sheet '{sheet_name}' 生成数据...")
                
                # 准备字段配置列表（仅按JSON规则：class_mappings -> field_patterns -> default_rules）
                field_configs: List[Dict[str, Any]] = []
                for column_name in columns:
                    datatype, ext_json = config_loader.get_field_config(sheet_name, column_name)
                    field_configs.append({
                        "field_name": column_name,
                        "datatype": datatype,
                        "ext_json": ext_json
                    })
                
                # 生成数据行
                sheet_rows = self.mock_generator.generate_batch(field_configs, rows_per_sheet)
                generated_data[sheet_name] = sheet_rows
                self.logger.info(f"为sheet '{sheet_name}' 生成了 {len(sheet_rows)} 行数据")
            
            # 写入Excel文件（新文件，一次性集中写出）
            output_handler = ExcelHandler(output_path)
            output_handler.write_data(generated_data)
            self.logger.info(f"数据已成功写入: {output_path}")
            
        except Exception as e:
            self.logger.error(f"生成数据时发生错误: {str(e)}")
            raise

    def create_template_from_config(self, output_path: str, 
                                  class_names: Optional[List[str]] = None) -> None:
        """根据配置创建Excel模板"""
        try:
            config_loader = self.config_manager.get_loader()
            
            # 获取要创建的类名
            if class_names is None:
                class_names = config_loader.get_all_classes()
            
            if not class_names:
                self.logger.warning("没有找到任何类配置")
                return
            
            # 构建模板数据
            template_data = {}
            
            for class_name in class_names:
                class_config = config_loader.get_class_config(class_name)
                if class_config:
                    # 获取字段名列表
                    field_names = list(class_config.keys())
                    template_data[class_name] = field_names
                    self.logger.info(f"为类 '{class_name}' 添加了 {len(field_names)} 个字段")
            
            # 生成模板文件
            ExcelTemplateGenerator.generate_from_config(template_data, output_path)
            self.logger.info(f"模板文件已创建: {output_path}")
            
        except Exception as e:
            self.logger.error(f"创建模板时发生错误: {str(e)}")
            raise


def json_dumps(obj: Any) -> str:
    import json
    return json.dumps(obj, ensure_ascii=False)


def main():
    """主函数"""
    parser = argparse.ArgumentParser(description='数据生成器')
    parser.add_argument('--template', '-t', type=str, default=os.path.join('templates', 'urule-batch-test-template.xlsx'), help='Excel 模板路径（默认使用 urule 模板）')
    parser.add_argument('--output', '-o', type=str, default=os.path.join('output', 'urule-batch-test-generated.xlsx'), help='输出文件路径（默认保存到 output 目录）')
    parser.add_argument('--rows', '-r', type=int, default=100, help='每个sheet生成的数据行数')
    parser.add_argument('--config-dir', '-c', type=str, default='config', help='配置目录路径')
    parser.add_argument('--create-template', action='store_true', help='创建模板文件')
    parser.add_argument('--validate', action='store_true', help='验证模板文件')
    parser.add_argument('--summary', action='store_true', help='显示配置摘要')
    parser.add_argument('--no-faker', action='store_true', help='不使用Faker生成数据')
    parser.add_argument('--classes', nargs='*', help='指定要处理的类名')
    
    args = parser.parse_args()
    
    # 创建数据生成器
    generator = DataGenerator(
        config_dir=args.config_dir,
        use_faker=not args.no_faker
    )
    
    try:
        if args.summary:
            # 显示配置摘要
            summary = generator.get_config_summary()
            print("配置摘要:")
            print(f"  版本: {summary.get('version', 'unknown')}")
            print(f"  类数量: {summary.get('classes_count', 0)}")
            print(f"  字段模式数量: {summary.get('field_patterns_count', 0)}")
            print(f"  默认规则数量: {summary.get('default_rules_count', 0)}")
            if summary.get('classes'):
                print(f"  已配置的类: {', '.join(summary['classes'])}")
            
        elif args.create_template:
            # 创建模板文件
            if not args.output:
                print("错误: 创建模板时必须指定输出文件路径 (--output)")
                sys.exit(1)
            
            generator.create_template_from_config(args.output, args.classes)
            print(f"模板文件已创建: {args.output}")
            
        elif args.validate:
            # 验证模板文件
            if not args.template:
                print("错误: 验证模板时必须指定模板文件路径 (--template)")
                sys.exit(1)
            
            is_valid = generator.validate_template(args.template)
            if is_valid:
                print("模板文件格式正确")
            else:
                print("模板文件格式错误")
                sys.exit(1)
                
        else:
            # 生成数据（仅JSON规则，集中写入）
            if not args.template or not args.output:
                print("错误: 生成数据时必须指定模板文件路径 (--template) 和输出文件路径 (--output)")
                sys.exit(1)
            
            generator.generate_data_from_template(
                args.template,
                args.output,
                args.rows
            )
            print(f"数据生成完成: {args.output}")
            
    except Exception as e:
        print(f"执行失败: {str(e)}")
        sys.exit(1)


if __name__ == "__main__":
    main()