/**
 * Created by Jacky.gao on 2016/9/19.
 */
import Cell from './Cell.js';

export default class AttributeCell extends Cell {
    constructor(row, col, cellData) {
        super(row, col, cellData);
        this.type = "attribute";
        this.category = null; // 当前选择的category
    }

    initCell(cellData) {
        const container = $(`<div></div>`);
        this.td.append(container);
        
        // Category选择器
        const categoryContainer = $(`<div style="margin-bottom: 5px;"></div>`);
        container.append(categoryContainer);
        const categoryLabel = $(`<label style="margin-right: 5px; color: #666;">分类：</label>`);
        categoryContainer.append(categoryLabel);
        this.categorySelector = $(`<span class="dropdown" style="cursor: pointer"></span>`);
        categoryContainer.append(this.categorySelector);
        const categorySpan = $(`<span class="dropdown-toggle" data-toggle="dropdown" style="color: #3c763d;font-weight: bold;font-size: 11px"></span>`);
        this.categorySelector.append(categorySpan);
        this.categoryContainer = $(`<span></span>`);
        RuleForge.setDomContent(this.categoryContainer, "选择分类");
        categorySpan.append(this.categoryContainer);
        categorySpan.append(` <span class="caret"></span>`);
        this.categoryMenuDef = $(`<ul class="dropdown-menu" role="menu"></ul>`);
        this.categorySelector.append(this.categoryMenuDef);
        
        this.propContainer = generateContainer();
        if (cellData) {
            this.variableLabel = cellData.variableLabel;
            this.variableName = cellData.variableName;
            this.weight = cellData.weight;

            // 加载 datatype 字段
            this.datatype = cellData.datatype;

            // 处理category字段，可能是category或variableCategory
            const categoryValue = cellData.category || cellData.variableCategory;
            if (categoryValue) {
                this.category = categoryValue;
                // 处理category可能是字符串或对象的情况
                const categoryName = typeof categoryValue === 'string' ? categoryValue : categoryValue.name;
            this.categoryName = categoryName;
                RuleForge.setDomContent(this.categoryContainer, categoryName);
            }
            RuleForge.setDomContent(this.propContainer, this.variableLabel || this.variableName);
        } else {
            RuleForge.setDomContent(this.propContainer, "请选择属性");
            console.log('AttributeCell init: no cellData provided');
        }
        container.append(this.propContainer);
        this.propContainer.css({color: 'green'});
        const _this = this;
        const del = $(`<span class="attribute-operation" style="color: #ff0600"><i class="glyphicon glyphicon-remove" style="cursor: pointer" title="删除当前属性行"/></span>`);
        container.append(del);
        del.click(function () {
            bootbox.confirm("真的要删除？", function (result) {
                if (!result) return;
                _this.row.remove();
            });
        });
        const addCondition = $(`<span class="attribute-operation" style="color: #019dff"><i class="glyphicon glyphicon-plus-sign" style="cursor: pointer" title="添加条件行"/></span>`);
        container.append(addCondition);
        addCondition.click(function () {
            _this.row.addConditionRow();
        });
        // 初始化时不自动加载属性菜单，需要先选择category
        this.weightContainer = $("<div style='margin-top: 5px;color:#999'><label>权重：</label></div>");
        if (!this.row.scoreCardTable.weightSupport) {
            this.weightContainer.hide();
        }

        this.weightEditor = $(`<input type="text" class="form-control" style="width:60px;height: 25px;display: inline-block">`);
        this.weightContainer.append(this.weightEditor);
        if (this.weight) {
            this.weightEditor.val(this.weight);
        }
        this.weightEditor.change(function () {
            _this.weight = $(this).val();
        });
        container.append(this.weightContainer);
        
        // 通过AttributeCol初始化category选项
        this.col.initCategoryForCell(this);
    }

    // 更新category选项
    updateCategoryOptions(categories) {
        const _this = this;
        this.categoryMenuDef.empty();
        
        if (!categories || categories.length === 0) {
            return;
        }
        
        for (let category of categories) {
            
            const categoryName = category.name || category.label || 'Unknown Category';
            const menuItem = $(`<li><a href="###">${categoryName}</a></li>`);
            this.categoryMenuDef.append(menuItem);
            menuItem.click(function (e) {
                e.preventDefault();
                e.stopPropagation();
                _this.category = category;
                RuleForge.setDomContent(_this.categoryContainer, categoryName);
                // 更新属性选项
                _this.refreshAttributeCellMenus(category.variables || []);
                // 清空当前选择的属性
                _this.variable = null;
                _this.variableName = null;
                _this.variableLabel = null;
                _this.datatype = null;
                RuleForge.setDomContent(_this.propContainer, "请选择属性");
                // 手动关闭dropdown
                _this.categorySelector.removeClass('open');
            });
        }
        
        // 确保Bootstrap dropdown正确初始化
        if (this.categorySelector && !this.categorySelector.hasClass('dropdown-initialized')) {
            this.categorySelector.addClass('dropdown-initialized');
            // 手动绑定dropdown toggle事件
            this.categorySelector.find('.dropdown-toggle').off('click.dropdown').on('click.dropdown', function(e) {
                e.preventDefault();
                e.stopPropagation();
                _this.categorySelector.toggleClass('open');
            });
            
            // 点击其他地方关闭dropdown
            $(document).off('click.category-dropdown').on('click.category-dropdown', function(e) {
                if (!_this.categorySelector.is(e.target) && _this.categorySelector.has(e.target).length === 0) {
                    _this.categorySelector.removeClass('open');
                }
            });
        }
    }

    // 获取当前选择的category名称
    getCategoryName() {
        if (!this.category) return null;
        return typeof this.category === 'string' ? this.category : this.category.name;
    }

    showWeight() {
        this.weightContainer.show();
        this.weight = null;
        this.weightEditor.val('');
    }

    hideWeight() {
        this.weightContainer.hide();
        this.weight = null;
        this.weightEditor.val('');
    }

    refreshAttributeCellMenus(variables) {
        if (!variables || variables.length === 0) {
            return;
        }
        
        const menuItems = [];
        const _this = this;
        for (let variable of variables) {
            menuItems.push({
                label: variable.label || variable.name,
                onClick: function () {
                    _this.variable = variable;
                    _this.variableName = variable.name;
                    _this.variableLabel = variable.label;
                    _this.datatype = variable.type;
                    RuleForge.setDomContent(_this.propContainer, variable.label || variable.name);
                    // 更新propContainer的颜色以表示已选择
                    _this.propContainer.css({color: 'green'});
                }
            })
        }
        if (!this.propContainer.menu) {
            this.propContainer.menu = new RuleForge.menu.Menu({menuItems});
            this.propContainer.off('click.property').on('click.property', function (e) {
                e.preventDefault();
                e.stopPropagation();
                _this.propContainer.menu.show(e);
            });
        } else {
            this.propContainer.menu.setConfig({menuItems});
        }
    }
}
