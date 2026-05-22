package com.ruleforge.console.servlet.variable;

import com.ruleforge.console.servlet.RenderPageServletHandler;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.library.variable.Act;
import com.ruleforge.model.library.variable.Variable;
import org.apache.commons.fileupload2.core.FileItemInput;
import org.apache.commons.fileupload2.jakarta.servlet6.JakartaServletFileUpload;
import org.apache.commons.io.IOUtils;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Jacky.gao
 * @since 2016年6月3日
 */
public class VariableEditorServletHandler extends RenderPageServletHandler {

    @Override
    public void execute(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String method = retriveMethod(req);
        if (method != null) {
            invokeMethod(method, req, resp);
        }
    }

    public void importXml(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        JakartaServletFileUpload upload = new JakartaServletFileUpload();
        InputStream inputStream = null;
        try {
            List<FileItemInput> items = upload.parseRequest(req);
            if (items.size() != 1) {
                throw new ServletException("Upload xml file is invalid.");
            }
            FileItemInput item = items.get(0);
            inputStream = item.getInputStream();
            String xmlContent = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            List<Variable> variables = new ArrayList<>();
            Document doc = DocumentHelper.parseText(xmlContent);
            Element root = doc.getRootElement();
            String clazz = root.attributeValue("clazz");
            for (Object obj : root.elements()) {
                if (obj == null || !(obj instanceof Element)) {
                    continue;
                }
                Element ele = (Element) obj;
                Variable var = new Variable();
                var.setAct(Act.InOut);
                var.setDefaultValue(ele.attributeValue("defaultValue"));
                var.setLabel(ele.attributeValue("label"));
                var.setName(ele.attributeValue("name"));
                var.setType(Datatype.valueOf(ele.attributeValue("type")));
                variables.add(var);
            }
            Map<String, Object> result = new HashMap<>();
            result.put("clazz", clazz);
            result.put("variables", variables);
            writeObjectToJson(resp, result);
        } catch (Exception e) {
            throw new ServletException(e);
        } finally {
            IOUtils.closeQuietly(inputStream);
        }
    }

    @Override
    public String url() {
        return "/variableeditor";
    }
}
