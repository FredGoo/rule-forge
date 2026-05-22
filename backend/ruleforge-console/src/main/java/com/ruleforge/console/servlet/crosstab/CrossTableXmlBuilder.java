package com.ruleforge.console.servlet.crosstab;

//import com.ruleforge.console.repository.ProjectVariable;

import com.ruleforge.console.repository.model.FileType;
import com.ruleforge.console.servlet.CellContent;
import com.ruleforge.dsl.DSLRuleSetBuilder;
import com.ruleforge.exception.RuleException;
import com.ruleforge.model.crosstab.CrosstabDefinition;
import com.ruleforge.model.library.variable.Variable;
import com.ruleforge.model.rule.*;
import com.ruleforge.model.rule.lhs.Criteria;
import com.ruleforge.model.rule.lhs.Criterion;
import com.ruleforge.model.rule.lhs.Junction;
import com.ruleforge.model.rule.lhs.Or;
import com.ruleforge.parse.deserializer.CrosstableDeserializer;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;

import java.util.ArrayList;
import java.util.List;

public class CrossTableXmlBuilder {
    private CrossData data;
    private DSLRuleSetBuilder dslRuleSetBuilder;
    private CrosstableDeserializer crosstableDeserializer;
    //    private List<ProjectVariable> projectVariables;
    private List<String> libraries = new ArrayList();

//    public CrossTableXmlBuilder(CrossData data, CrosstableDeserializer crosstableDeserializer, List<ProjectVariable> projectVariables, DSLRuleSetBuilder dslRuleSetBuilder) {
//        this.data = data;
//        this.crosstableDeserializer = crosstableDeserializer;
//        this.projectVariables = projectVariables;
//        this.dslRuleSetBuilder = dslRuleSetBuilder;
//    }

    public CrosstabDefinition doBuild() throws Exception {
        try {
            String xml = this.buildXml();
            Document doc = DocumentHelper.parseText(xml);
            return this.crosstableDeserializer.deserialize(doc.getRootElement());
        } catch (Exception e) {
            throw new RuleException(e);
        }
    }

    private String buildXml() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        CrossHeader header = this.data.getHeader();
        sb.append("<crosstab>");
        String headerContent = header.getContent();
        headerContent = headerContent == null ? "表头" : headerContent;
        sb.append("<header rowspan=\"" + header.getRowSpan() + "\" colspan=\"" + header.getColSpan() + "\"><![CDATA[" + headerContent + "]]></header>");

        for (CrossRow row : this.data.getRows()) {
            sb.append("<row number=\"" + row.getNumber() + "\" type=\"" + row.getType() + "\"");
            String content = row.getContent();
            if (StringUtils.isNotBlank(content)) {
                String[] names = this.buildName(content);
                Variable variable = this.findVariable(names);
                String category = names[0];
                String bundleDataType = "variable";
                if (category.equals("参数") || category.equals("parameter")) {
                    bundleDataType = "parameter";
                }

                sb.append(" bundle-data-type=\"" + bundleDataType + "\" var-category=\"" + category + "\"" + " var=\"" + variable.getName() + "\" var-label=\"" + variable.getLabel() + "\"" + " datatype=\"" + variable.getType().name() + "\"");
            }

            sb.append("/>");
        }

        for (CrossColumn col : this.data.getColumns()) {
            sb.append("<column number=\"" + col.getNumber() + "\" type=\"" + col.getType() + "\"");
            String content = col.getContent();
            if (StringUtils.isNotBlank(content)) {
                String[] names = this.buildName(content);
                Variable variable = this.findVariable(names);
                String category = names[0];
                String bundleDataType = "variable";
                if (category.equals("参数") || category.equals("parameter")) {
                    bundleDataType = "parameter";
                }

                sb.append(" bundle-data-type=\"" + bundleDataType + "\" var-category=\"" + category + "\"" + " var=\"" + variable.getName() + "\" var-label=\"" + variable.getLabel() + "\"" + " datatype=\"" + variable.getType().name() + "\"");
            }

            sb.append("/>");
        }

        for (CellContent cell : this.data.getCells()) {
            String type = cell.getType();
            String cellType = "condition-cell";
            if (type.equals("value")) {
                cellType = "value-cell";
            }

            sb.append("<");
            sb.append(cellType);
            int rowSpan = 1;
            int colSpan = 1;
            boolean condition = false;
            if (cell.getRow() <= this.data.getHeader().getRowSpan()) {
                colSpan = cell.getSpan();
                condition = true;
            }

            if (cell.getCol() <= this.data.getHeader().getColSpan()) {
                rowSpan = cell.getSpan();
                condition = true;
            }

            sb.append(" row=\"" + cell.getRow() + "\" col=\"" + cell.getCol() + "\"");
            if (condition) {
                sb.append(" rowspan=\"" + rowSpan + "\" colspan=\"" + colSpan + "\"");
            }

            sb.append(">");
            String content = cell.getContent();
            if (StringUtils.isNotBlank(content)) {
                if (condition) {
//                    Criterion criterion = this.dslRuleSetBuilder.buildCriterion(content);
//                    sb.append(this.criterionToXml(criterion));
                } else {
                    content = StringEscapeUtils.escapeXml(content);
                    sb.append("<value content=\"" + content + "\" type=\"Input\"/>");
                }
            }

            sb.append("");
            sb.append("</");
            sb.append(cellType);
            sb.append(">");
        }

        for (String path : this.libraries) {
            if (path.endsWith(FileType.VariableLibrary.toString())) {
                sb.append("<import-variable-library path=\"" + path + "\"/>");
            } else {
                sb.append("<import-parameter-library path=\"" + path + "\"/>");
            }
        }

        sb.append("");
        sb.append("");
        sb.append("");
        sb.append("");
        sb.append("");
        sb.append("</crosstab>");
        return sb.toString();
    }

    private String criterionToXml(Criterion criterion) {
        StringBuilder sb = new StringBuilder();
        if (criterion instanceof Junction) {
            Junction junction = (Junction) criterion;
            List<Criterion> children = junction.getCriterions();
            String type = "and";
            if (junction instanceof Or) {
                type = "or";
            }

            sb.append("<joint type=\"" + type + "\">");
            if (children != null) {
                for (Criterion c : children) {
                    if (c instanceof Criteria) {
                        Criteria criteria = (Criteria) c;
                        sb.append("<condition op=\"" + criteria.getOp().name() + "\">");
                        Value value = criteria.getValue();
                        sb.append(this.buildValue(value));
                        sb.append("</condition>");
                    }
                }
            }
        } else {
            sb.append("<joint type=\"and\">");
            Criteria criteria = (Criteria) criterion;
            sb.append("<condition op=\"" + criteria.getOp().name() + "\">");
            Value value = criteria.getValue();
            sb.append(this.buildValue(value));
            sb.append("</condition>");
        }

        sb.append("</joint>");
        return sb.toString();
    }

    private String buildValue(Value value) {
        StringBuilder sb = new StringBuilder();
        if (value instanceof SimpleValue) {
            SimpleValue sv = (SimpleValue) value;
            String data = StringEscapeUtils.escapeXml(sv.getContent());
            sb.append("<value content=\"" + data + "\" type=\"Input\">");
        } else if (value instanceof VariableCategoryValue) {
            VariableCategoryValue vc = (VariableCategoryValue) value;
            String category = vc.getVariableCategory();
            String data = StringEscapeUtils.escapeXml(category);
            sb.append("<value content=\"" + data + "\" type=\"Input\">");
        } else if (value instanceof VariableValue) {
            VariableValue vv = (VariableValue) value;
            String data = vv.getVariableCategory() + "." + vv.getVariableLabel();
            data = StringEscapeUtils.escapeXml(data);
            sb.append("<value content=\"" + data + "\" type=\"Input\">");
        } else {
            sb.append("<value content=\"\" type=\"Input\">");
        }

        ComplexArithmetic arithmetic = value.getArithmetic();
        if (arithmetic == null) {
            sb.append("</value>");
            return sb.toString();
        } else {
            ArithmeticType type = arithmetic.getType();
            sb.append("<complex-arith type=\"" + type.name() + "\">");
            sb.append(this.buildValue(arithmetic.getValue()));
            sb.append("</complex-arith>");
            sb.append("</value>");
            return sb.toString();
        }
    }

    private Variable findVariable(String[] names) {
        String category = names[0];
        String label = names[1];

//        for(ProjectVariable pv : this.projectVariables) {
//            for(VariableCategory vc : pv.getVariableCategories()) {
//                if (vc.getName().equals(category)) {
//                    String path = "jcr:" + pv.getPath();
//                    if (!this.libraries.contains(path)) {
//                        this.libraries.add(path);
//                    }
//
//                    List<Variable> variables = vc.getVariables();
//                    if (variables != null) {
//                        for(Variable var : variables) {
//                            if (var.getLabel().equals(label)) {
//                                return var;
//                            }
//                        }
//                    }
//                }
//            }
//        }

        throw new RuleException("变量[" + category + "." + label + "]在当前项目中未定义!");
    }

    private String[] buildName(String name) {
        String[] arr = name.split("\\.");
        if (arr.length != 2) {
            throw new RuleException("表头[" + name + "]不合法！");
        } else {
            return arr;
        }
    }
}
