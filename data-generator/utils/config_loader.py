"""
配置加载器
负责加载字段映射配置并提供查找功能
"""
import json
import os
from typing import Dict, Any, Optional, Tuple


class ConfigLoader:
    """配置加载器"""
    
    def __init__(self, config_path: str):
        """初始化配置加载器
        
        Args:
            config_path: 配置文件路径
        """
        self.config_path = config_path
        self.config = {}
        self.load_config()
    
    def load_config(self) -> None:
        """加载配置文件"""
        if not os.path.exists(self.config_path):
            raise FileNotFoundError(f"配置文件不存在: {self.config_path}")
        
        try:
            with open(self.config_path, 'r', encoding='utf-8') as f:
                self.config = json.load(f)
        except json.JSONDecodeError as e:
            raise ValueError(f"配置文件格式错误: {str(e)}")
        except Exception as e:
            raise Exception(f"加载配置文件失败: {str(e)}")
    
    def get_field_config(self, class_name: str, field_name: str) -> Tuple[str, Optional[str]]:
        """根据类名和字段名获取生成配置
        
        Args:
            class_name: 类名
            field_name: 字段名
            
        Returns:
            (datatype, ext_json) 元组
        """
        # 1. 优先查找类特定的字段配置
        class_mappings = self.config.get("class_mappings", {})
        if class_name in class_mappings:
            class_config = class_mappings[class_name]
            if field_name in class_config:
                field_config = class_config[field_name]
                return field_config.get("datatype", "String"), field_config.get("ext_json")
        
        # 2. 查找通用字段模式配置
        field_patterns = self.config.get("field_patterns", {})
        if field_name in field_patterns:
            pattern_config = field_patterns[field_name]
            return pattern_config.get("datatype", "String"), pattern_config.get("ext_json")
        
        # 3. 根据字段名推断数据类型
        inferred_type = self._infer_datatype_from_field_name(field_name)
        
        # 4. 查找默认规则
        default_rules = self.config.get("default_rules", {})
        if inferred_type in default_rules:
            default_config = default_rules[inferred_type]
            return default_config.get("datatype", "String"), default_config.get("ext_json")
        
        # 5. 返回默认配置
        return "String", None
    
    def _infer_datatype_from_field_name(self, field_name: str) -> str:
        """根据字段名推断数据类型"""
        field_lower = field_name.lower()
        
        # ID类字段
        if field_lower in ["id", "userid", "productid", "orderid"] or field_lower.endswith("id"):
            return "Long"
        
        # 布尔类字段
        if field_lower.startswith("is") or field_lower in ["active", "deleted", "enabled", "disabled"]:
            return "Boolean"
        
        # 数值类字段
        if field_lower in ["age", "count", "num", "number", "quantity", "stock", "level", "rank", "score"]:
            return "Integer"
        
        # 金额类字段
        if field_lower in ["price", "amount", "money", "cost", "fee", "salary", "balance"]:
            return "BigDecimal"
        
        # 小数类字段
        if field_lower in ["rate", "ratio", "percent", "weight", "height", "width", "length"]:
            return "Double"
        
        # 状态类字段
        if field_lower in ["status", "state", "type", "category", "level"]:
            return "Integer"
        
        # 默认为字符串
        return "String"
    
    def get_class_config(self, class_name: str) -> Dict[str, Dict[str, Any]]:
        """获取指定类的所有字段配置
        
        Args:
            class_name: 类名
            
        Returns:
            字段配置字典
        """
        class_mappings = self.config.get("class_mappings", {})
        return class_mappings.get(class_name, {})
    
    def get_all_classes(self) -> list:
        """获取所有已配置的类名"""
        class_mappings = self.config.get("class_mappings", {})
        return list(class_mappings.keys())
    
    def add_class_config(self, class_name: str, fields_config: Dict[str, Dict[str, Any]]) -> None:
        """添加类配置
        
        Args:
            class_name: 类名
            fields_config: 字段配置字典
        """
        if "class_mappings" not in self.config:
            self.config["class_mappings"] = {}
        
        self.config["class_mappings"][class_name] = fields_config
    
    def add_field_pattern(self, field_name: str, config: Dict[str, Any]) -> None:
        """添加字段模式配置
        
        Args:
            field_name: 字段名
            config: 配置字典
        """
        if "field_patterns" not in self.config:
            self.config["field_patterns"] = {}
        
        self.config["field_patterns"][field_name] = config
    
    def save_config(self, output_path: Optional[str] = None) -> None:
        """保存配置到文件
        
        Args:
            output_path: 输出路径，如果为None则覆盖原文件
        """
        save_path = output_path or self.config_path
        
        try:
            with open(save_path, 'w', encoding='utf-8') as f:
                json.dump(self.config, f, ensure_ascii=False, indent=2)
        except Exception as e:
            raise Exception(f"保存配置文件失败: {str(e)}")
    
    def validate_config(self) -> Tuple[bool, list]:
        """验证配置文件格式
        
        Returns:
            (是否有效, 错误信息列表)
        """
        errors = []
        
        # 检查必要的顶级键
        required_keys = ["default_rules", "class_mappings", "field_patterns"]
        for key in required_keys:
            if key not in self.config:
                errors.append(f"缺少必要的配置项: {key}")
        
        # 验证class_mappings格式
        class_mappings = self.config.get("class_mappings", {})
        for class_name, class_config in class_mappings.items():
            if not isinstance(class_config, dict):
                errors.append(f"类配置格式错误: {class_name}")
                continue
            
            for field_name, field_config in class_config.items():
                if not isinstance(field_config, dict):
                    errors.append(f"字段配置格式错误: {class_name}.{field_name}")
                    continue
                
                if "datatype" not in field_config:
                    errors.append(f"字段缺少datatype配置: {class_name}.{field_name}")
        
        # 验证field_patterns格式
        field_patterns = self.config.get("field_patterns", {})
        for field_name, pattern_config in field_patterns.items():
            if not isinstance(pattern_config, dict):
                errors.append(f"字段模式配置格式错误: {field_name}")
                continue
            
            if "datatype" not in pattern_config:
                errors.append(f"字段模式缺少datatype配置: {field_name}")
        
        return len(errors) == 0, errors
    
    def get_config_summary(self) -> Dict[str, Any]:
        """获取配置摘要信息"""
        class_mappings = self.config.get("class_mappings", {})
        field_patterns = self.config.get("field_patterns", {})
        default_rules = self.config.get("default_rules", {})
        
        return {
            "version": self.config.get("version", "unknown"),
            "classes_count": len(class_mappings),
            "field_patterns_count": len(field_patterns),
            "default_rules_count": len(default_rules),
            "classes": list(class_mappings.keys()),
            "common_fields": list(field_patterns.keys())
        }


class ConfigManager:
    """配置管理器"""
    
    def __init__(self, config_dir: str = "config"):
        """初始化配置管理器
        
        Args:
            config_dir: 配置目录
        """
        self.config_dir = config_dir
        self.loaders = {}
    
    def get_loader(self, config_name: str = "field_mapping") -> ConfigLoader:
        """获取配置加载器
        
        Args:
            config_name: 配置名称
            
        Returns:
            配置加载器实例
        """
        if config_name not in self.loaders:
            config_path = os.path.join(self.config_dir, f"{config_name}.json")
            self.loaders[config_name] = ConfigLoader(config_path)
        
        return self.loaders[config_name]
    
    def reload_config(self, config_name: str = "field_mapping") -> None:
        """重新加载配置
        
        Args:
            config_name: 配置名称
        """
        if config_name in self.loaders:
            self.loaders[config_name].load_config()
    
    def create_default_config(self, config_name: str = "field_mapping") -> str:
        """创建默认配置文件
        
        Args:
            config_name: 配置名称
            
        Returns:
            配置文件路径
        """
        config_path = os.path.join(self.config_dir, f"{config_name}.json")
        
        # 确保配置目录存在
        os.makedirs(self.config_dir, exist_ok=True)
        
        # 创建默认配置
        default_config = {
            "description": "字段映射配置文件",
            "version": "1.0.0",
            "default_rules": {
                "String": {"datatype": "String", "ext_json": None},
                "Integer": {"datatype": "Integer", "ext_json": "{\"type\": \"range\", \"min\": 1, \"max\": 1000}"},
                "Long": {"datatype": "Long", "ext_json": "{\"type\": \"range\", \"min\": 1, \"max\": 999999}"},
                "Double": {"datatype": "Double", "ext_json": "{\"type\": \"range\", \"min\": 0.0, \"max\": 100.0}"},
                "Boolean": {"datatype": "Boolean", "ext_json": "{\"type\": \"enum\", \"items\": [{\"value\": true}, {\"value\": false}]}"}
            },
            "class_mappings": {},
            "field_patterns": {}
        }
        
        with open(config_path, 'w', encoding='utf-8') as f:
            json.dump(default_config, f, ensure_ascii=False, indent=2)
        
        return config_path