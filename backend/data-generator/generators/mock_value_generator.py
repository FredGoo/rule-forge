"""
模拟数据生成器 - Python版本
翻译自Java版本的MockValueGenerator
"""
import json
import random
import re
from decimal import Decimal
from typing import Any, Dict, List, Optional, Union
from concurrent.futures import ThreadPoolExecutor
from .data_types import TypeConverter


class MockValueGenerator:
    """模拟数据生成器"""
    
    def __init__(self, seed: Optional[int] = None):
        """初始化生成器
        
        Args:
            seed: 随机种子，用于生成可重复的结果
        """
        if seed is not None:
            random.seed(seed)
        self.random = random.Random(seed)
    
    def generate(self, datatype: str, ext_json: Optional[str] = None) -> Any:
        """为给定的变量定义生成一个模拟值
        
        Args:
            datatype: 数据类型
            ext_json: 扩展JSON配置
            
        Returns:
            生成的模拟值
        """
        datatype = self._safe_string(datatype, "String")
        
        if not ext_json or not ext_json.strip():
            return TypeConverter.default_by_type(datatype)
        
        try:
            spec = json.loads(ext_json)
        except json.JSONDecodeError:
            return TypeConverter.default_by_type(datatype)
        
        gen_type = spec.get("type")
        if not gen_type:
            return TypeConverter.default_by_type(datatype)
        
        if gen_type == "enum":
            return self._gen_enum(spec, datatype)
        elif gen_type == "range":
            return self._gen_range(spec, datatype)
        elif gen_type == "pattern":
            return self._gen_pattern(spec, datatype)
        else:
            return TypeConverter.default_by_type(datatype)
    
    def _gen_enum(self, spec: Dict, datatype: str) -> Any:
        """生成枚举类型的值"""
        items = spec.get("items", [])
        if not items:
            return TypeConverter.default_by_type(datatype)
        
        item = self.random.choice(items)
        value = item.get("value")
        
        if value is None:
            # 如果未提供value，则使用label或默认值
            value = item.get("label", "")
        
        return TypeConverter.cast_by_type(value, datatype)
    
    def _gen_range(self, spec: Dict, datatype: str) -> Any:
        """生成范围类型的值"""
        # 处理特殊值（如-1）
        special_values = spec.get("specialValues", [])
        if special_values and self.random.random() < 0.1:  # 10%概率返回特殊值
            special_val = self.random.choice(special_values)
            return TypeConverter.cast_by_type(special_val, datatype)
        
        # 处理数值范围
        min_val = TypeConverter.as_number(spec.get("min"))
        max_val = TypeConverter.as_number(spec.get("max"))
        
        if min_val is None and max_val is None:
            # 支持字符串数字范围（需要width），如zip/TIN
            width = spec.get("width")
            if width and datatype.lower() == "string":
                lo = int(spec.get("min", 0))
                hi = int(spec.get("max", 0)) if spec.get("max") is not None else lo + 100
                val = self.random.randint(lo, hi)
                return f"{val:0{int(width)}d}"
            return TypeConverter.default_by_type(datatype)
        
        datatype_lower = datatype.lower()
        
        if datatype_lower in ["integer", "int", "long"]:
            lo = int(min_val) if min_val is not None else 0
            hi = int(max_val) if max_val is not None else lo + 100
            if hi < lo:
                hi = lo
            return self.random.randint(lo, hi)
        
        elif datatype_lower in ["double", "float"]:
            lo = float(min_val) if min_val is not None else 0.0
            hi = float(max_val) if max_val is not None else lo + 100.0
            if hi < lo:
                hi = lo
            value = self.random.uniform(lo, hi)
            return float(value) if datatype_lower == "float" else value
        
        elif datatype_lower == "bigdecimal":
            lo = float(min_val) if min_val is not None else 0.0
            hi = float(max_val) if max_val is not None else lo + 100.0
            if hi < lo:
                hi = lo
            value = self.random.uniform(lo, hi)
            return Decimal(str(value))
        
        elif datatype_lower == "string":
            # 字符串数字范围（带width）
            width = spec.get("width")
            if width:
                lo = int(min_val) if min_val is not None else 0
                hi = int(max_val) if max_val is not None else lo + 100
                val = self.random.randint(lo, hi)
                return f"{val:0{int(width)}d}"
        
        return TypeConverter.default_by_type(datatype)
    
    def _gen_pattern(self, spec: Dict, datatype: str) -> Any:
        """生成模式匹配类型的值"""
        pattern = spec.get("pattern")
        if not pattern:
            return TypeConverter.default_by_type(datatype)
        
        # 支持简写：email
        if pattern == "email":
            # 无faker时的后备简易邮箱
            user = f"user{self.random.randint(1000, 9999)}"
            host = f"example{self.random.randint(10, 99)}"
            return f"{user}@{host}.com"
        
        # digits 纯数字串（支持长度范围）
        if pattern == "digits":
            min_len = int(spec.get("min_len", 8))
            max_len = int(spec.get("max_len", 12))
            length = self.random.randint(min_len, max_len)
            return "".join(str(self.random.randint(0, 9)) for _ in range(length))
        
        # 5位数字邮编
        if pattern == r"^\d{5}$":
            lo, hi = 0, 99999
            range_numeric = spec.get("rangeNumeric", {})
            if range_numeric:
                min_val = TypeConverter.as_number(range_numeric.get("min"))
                max_val = TypeConverter.as_number(range_numeric.get("max"))
                if min_val is not None:
                    lo = int(min_val)
                if max_val is not None:
                    hi = int(max_val)
            
            value = self.random.randint(lo, hi)
            return f"{value:05d}"
        
        # 邮箱地址（包含@提示）
        if "@" in pattern:
            user = f"user{self.random.randint(1000, 9999)}"
            host = f"example{self.random.randint(10, 99)}"
            return f"{user}@{host}.com"
        
        # 纯数字串
        if pattern == r"^\d+$":
            length = 10 + self.random.randint(0, 5)  # 10-15位
            return "".join(str(self.random.randint(0, 9)) for _ in range(length))
        
        # 13位数字
        if pattern == r"^\d{13}$":
            return "".join(str(self.random.randint(0, 9)) for _ in range(13))
        
        # 手机号码
        if pattern in [r"^1[3-9]\d{9}$", r"^1\d{10}$"]:
            prefixes = ["130", "131", "132", "133", "134", "135", "136", "137", "138", "139",
                       "150", "151", "152", "153", "155", "156", "157", "158", "159",
                       "180", "181", "182", "183", "184", "185", "186", "187", "188", "189"]
            prefix = self.random.choice(prefixes)
            suffix = "".join(str(self.random.randint(0, 9)) for _ in range(8))
            return prefix + suffix
        
        # 身份证号码
        if pattern == r"^\d{17}[\dXx]$":
            # 简化版身份证生成
            area_codes = ["110000", "120000", "130000", "140000", "150000", "210000", "220000"]
            area = self.random.choice(area_codes)
            birth_year = self.random.randint(1950, 2005)
            birth_month = self.random.randint(1, 12)
            birth_day = self.random.randint(1, 28)
            birth_date = f"{birth_year}{birth_month:02d}{birth_day:02d}"
            sequence = f"{self.random.randint(0, 999):03d}"
            
            # 简化校验码计算
            check_codes = "0123456789X"
            check_code = self.random.choice(check_codes)
            
            return area + birth_date + sequence + check_code
        
        # 默认返回字符串
        return TypeConverter.default_by_type(datatype)
    
    def _safe_string(self, s: Optional[str], default: str) -> str:
        """安全字符串处理"""
        return default if not s or not s.strip() else s
    
    def generate_batch(self, configs: List[Dict[str, Any]], count: int = 100) -> List[Dict[str, Any]]:
        """批量生成数据
        
        Args:
            configs: 配置列表，每个配置包含field_name, datatype, ext_json
            count: 生成数据条数
            
        Returns:
            生成的数据列表
        """
        results = []
        for i in range(count):
            row = {}
            for config in configs:
                field_name = config.get("field_name", f"field_{i}")
                datatype = config.get("datatype", "String")
                ext_json = config.get("ext_json")
                
                row[field_name] = self.generate(datatype, ext_json)
            results.append(row)
        
        return results


class AdvancedMockValueGenerator(MockValueGenerator):
    """高级模拟数据生成器，支持更多功能"""
    
    def __init__(self, seed: Optional[int] = None):
        super().__init__(seed)
        self.faker_available = False
        try:
            from faker import Faker
            self.faker = Faker('zh_CN')  # 中文本地化
            self.faker_available = True
        except ImportError:
            pass
    
    def _gen_pattern(self, spec: Dict, datatype: str) -> Any:
        """增强的模式生成，支持Faker库"""
        pattern = spec.get("pattern")
        if not pattern:
            return TypeConverter.default_by_type(datatype)
        
        # 如果有Faker库，优先使用
        if self.faker_available:
            faker_type = spec.get("faker_type")
            if faker_type:
                try:
                    return getattr(self.faker, faker_type)()
                except AttributeError:
                    pass
        
        # digits（支持长度范围）
        if pattern == "digits":
            min_len = int(spec.get("min_len", 8))
            max_len = int(spec.get("max_len", 12))
            length = self.random.randint(min_len, max_len)
            return "".join(str(self.random.randint(0, 9)) for _ in range(length))
        
        # 回退到父类实现
        return super()._gen_pattern(spec, datatype)