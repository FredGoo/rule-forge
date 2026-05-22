package com.ruleforge.console.servlet.crosstab;

import com.ruleforge.Utils;
import com.ruleforge.console.repository.RepositoryService;
import com.ruleforge.console.servlet.CellContent;
import com.ruleforge.console.servlet.RenderPageServletHandler;
import com.ruleforge.console.servlet.common.CommonServletHandler;
import com.ruleforge.dsl.DSLRuleSetBuilder;
import com.ruleforge.exception.RuleException;
import com.ruleforge.parse.deserializer.CrosstableDeserializer;
import org.apache.commons.fileupload2.core.FileItemInput;
import org.apache.commons.fileupload2.jakarta.servlet6.JakartaServletFileUpload;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class CrosstabEditorServletHandler extends RenderPageServletHandler {
    private RepositoryService repositoryService;
    private CommonServletHandler commonServletHandler;
    private CrosstableDeserializer crosstableDeserializer;
    private DSLRuleSetBuilder dslRuleSetBuilder;

    public void execute(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String method = this.retriveMethod(req);
        if (method != null) {
            this.invokeMethod(method, req, resp);
        }
    }

    public void importExcel(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        Map<String, Object> result = new HashMap();

        try {
            String project = req.getParameter("project");
            project = Utils.decodeURL(project);
            JakartaServletFileUpload upload = new JakartaServletFileUpload();
            List<FileItemInput> items = upload.parseRequest(req);
            CrossData data = null;

            for (FileItemInput item : items) {
                String name = item.getFieldName();
                if (name.equals("excel_file")) {
                    InputStream stream = item.getInputStream();
                    data = this.parseExcel(stream);
                    stream.close();
                    break;
                }
            }

            if (data == null) {
                throw new RuleException("请上传一个Excel文件！");
            }

//            Principal principal = EnvironmentUtils.getLoginPrincipal(new RequestContext(req, resp));
//            List<ProjectVariable> libraries = this.repositoryService.loadProjectLibraries(project, principal);
//
//            for(ProjectVariable pv : libraries) {
//                String path = pv.getPath();
//                Object obj = this.commonServletHandler.buildData(path).get(0);
//                if (path.endsWith(FileType.ParameterLibrary.toString())) {
//                    List<Variable> vars = (List)obj;
//                    List<VariableCategory> list = new ArrayList();
//                    VariableCategory vc = new VariableCategory();
//                    vc.setClazz(HashMap.class.getName());
//                    vc.setName("参数");
//                    vc.setType(CategoryType.Clazz);
//                    vc.setVariables(vars);
//                    list.add(vc);
//                    pv.setVariableCategories(list);
//                } else {
//                    pv.setVariableCategories((List)obj);
//                }
//            }

//            CrossTableXmlBuilder builder = new CrossTableXmlBuilder(data, this.crosstableDeserializer, libraries, this.dslRuleSetBuilder);
//            CrosstabDefinition crossTable = builder.doBuild();
//            req.getSession().setAttribute("_import_data_", crossTable);
            result.put("fail", false);
        } catch (Exception ex) {
            ex.printStackTrace();
            String msg = this.buildErrorMsg(ex);
            result.put("fail", true);
            result.put("msg", msg);
        }

        this.writeObjectToJson(resp, result);
    }

    private String buildErrorMsg(Exception ex) {
        Throwable e = this.buildCause(ex);
        if (e instanceof NullPointerException) {
            return "空指针错误！";
        } else {
            String msg = e.getMessage();
            msg = msg == null ? "服务端错误!" : msg;
            return msg;
        }
    }

    private Throwable buildCause(Throwable e) {
        return e.getCause() != null ? this.buildCause(e.getCause()) : e;
    }

    private CrossData parseExcel(InputStream stream) throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook(stream);
        if (wb.getNumberOfSheets() == 0) {
            wb.close();
            throw new RuleException("导入Excel不合法！");
        } else {
            List<CrossRow> rows = new ArrayList();
            List<CrossColumn> cols = new ArrayList();
            XSSFSheet sheet = wb.getSheetAt(0);
            CrossHeader header = this.buildHeader(sheet);
            XSSFRow firstRow = sheet.getRow(0);
            int totalColumn = firstRow.getLastCellNum();
            XSSFRow firstSpanRow = sheet.getRow(header.getRowSpan());

            for (int i = 0; i < totalColumn; ++i) {
                CrossColumn col = new CrossColumn();
                col.setNumber(i + 1);
                if (i < header.getColSpan()) {
                    col.setType(Type.left);
                    XSSFCell cell = firstSpanRow.getCell(i);
                    XSSFComment cellComment = cell.getCellComment();
                    if (cellComment != null) {
                        String comment = cellComment.getString().toString().toLowerCase().trim();
                        col.setContent(comment);
                    }
                } else {
                    col.setType(Type.top);
                }

                cols.add(col);
            }

            int totalRow = sheet.getLastRowNum();

            for (int i = 0; i <= totalRow; ++i) {
                XSSFRow row = sheet.getRow(i);
                CrossRow crossRow = new CrossRow();
                crossRow.setNumber(i + 1);
                if (i < header.getRowSpan()) {
                    crossRow.setType(Type.top);
                    XSSFCell firstCell = row.getCell(header.getColSpan());
                    XSSFComment cellComment = firstCell.getCellComment();
                    if (cellComment != null) {
                        String comment = cellComment.getString().toString().toLowerCase().trim();
                        crossRow.setContent(comment);
                    }
                } else {
                    crossRow.setType(Type.left);
                }

                rows.add(crossRow);
            }

            List<CellContent> cells = new ArrayList();

            for (int i = 0; i <= totalRow; ++i) {
                XSSFRow row = sheet.getRow(i);

                for (int j = 0; j < totalColumn; ++j) {
                    if (i != 0 || j != 0) {
                        XSSFCell cell = row.getCell(j);
                        if (cell != null) {
                            Span span = this.getCellSpan(i, j, sheet);
                            if (span != null) {
                                String cellData = this.getCellData(cell);
                                CellContent cc = new CellContent();
                                cc.setCol(j + 1);
                                cc.setRow(i + 1);
                                cc.setContent(cellData);
                                if (i < header.getRowSpan()) {
                                    cc.setType("condition");
                                    cc.setSpan(span.getCol());
                                }

                                if (j < header.getColSpan()) {
                                    cc.setType("condition");
                                    cc.setSpan(span.getRow());
                                }

                                cells.add(cc);
                            }
                        }
                    }
                }
            }

            wb.close();
            CrossData data = new CrossData();
            data.setCells(cells);
            data.setColumns(cols);
            data.setRows(rows);
            data.setHeader(header);
            return data;
        }
    }

    private CrossHeader buildHeader(XSSFSheet sheet) {
        Span span = this.getCellSpan(0, 0, sheet);
        if (span == null) {
            throw new RuleException("导入的Excel不合法!");
        } else {
            CrossHeader header = new CrossHeader();
            header.setRowSpan(span.getRow());
            header.setColSpan(span.getCol());
            XSSFRow row = sheet.getRow(0);
            XSSFCell cell = row.getCell(0);
            header.setContent(this.getCellData(cell));
            return header;
        }
    }

    private Span getCellSpan(int row, int col, XSSFSheet sheet) {
        for (CellRangeAddress range : sheet.getMergedRegions()) {
            if (range.getFirstColumn() == col && range.getFirstRow() == row) {
                int rowSpan = range.getLastRow() - range.getFirstRow();
                if (rowSpan > 0) {
                    ++rowSpan;
                }

                Span s = new Span();
                s.setRow(rowSpan);
                int colSpan = range.getLastColumn() - range.getFirstColumn();
                if (colSpan > 0) {
                    ++colSpan;
                }

                s.setCol(colSpan);
                return s;
            }

            if (col >= range.getFirstColumn() && col <= range.getLastColumn() && row >= range.getFirstRow() && row <= range.getLastRow()) {
                return null;
            }
        }

        Span s = new Span();
        s.setRow(1);
        s.setCol(1);
        return s;
    }

    private String getCellData(XSSFCell cell) {
        String data = null;
        CellType type = cell.getCellType();
        switch (type) {
            case STRING:
                data = cell.getStringCellValue();
                break;
            case BOOLEAN:
                data = String.valueOf(cell.getBooleanCellValue());
                break;
            case NUMERIC:
                data = String.valueOf(cell.getNumericCellValue());
            case _NONE:
            case BLANK:
            case ERROR:
            case FORMULA:
        }

        return data;
    }

    public void setCommonServletHandler(CommonServletHandler commonServletHandler) {
        this.commonServletHandler = commonServletHandler;
    }

    public void setRepositoryService(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    public void setCrosstableDeserializer(CrosstableDeserializer crosstableDeserializer) {
        this.crosstableDeserializer = crosstableDeserializer;
    }

    public void setDslRuleSetBuilder(DSLRuleSetBuilder dslRuleSetBuilder) {
        this.dslRuleSetBuilder = dslRuleSetBuilder;
    }

    public String url() {
        return "/crosstabeditor";
    }
}
