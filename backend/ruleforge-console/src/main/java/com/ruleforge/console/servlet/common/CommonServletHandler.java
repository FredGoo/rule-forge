package com.ruleforge.console.servlet.common;

import com.ruleforge.console.repository.RepositoryService;
import com.ruleforge.console.repository.model.FileType;
import com.ruleforge.console.repository.model.RepositoryFile;
import com.ruleforge.console.repository.model.Type;
import com.ruleforge.console.servlet.RenderPageServletHandler;
import com.ruleforge.console.servlet.RequestContext;
import com.ruleforge.exception.RuleException;
import com.ruleforge.model.function.FunctionDescriptor;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.model.rule.RuleSet;
import com.ruleforge.parse.deserializer.RuleSetDeserializer;
import com.ruleforge.console.model.Repository;
import com.ruleforge.console.model.User;
import com.ruleforge.console.util.EnvironmentUtils;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Jacky.gao
 * @author fred
 * @date 2016年7月25日
 */
public class CommonServletHandler extends RenderPageServletHandler {

    private RepositoryService repositoryService;

    @Override
    public void execute(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String method = retriveMethod(req);
        if (method != null) {
            invokeMethod(method, req, resp);
        } else {
            throw new ServletException("Unsupport this operation.");
        }
    }

    public void findRuleByKey(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        List<Rule> ruleList = new ArrayList<>();

        String ruleKey = req.getParameter("ruleKey");
        String projectName = req.getParameter("projectName");
        User user = EnvironmentUtils.getLoginUser(new RequestContext(req, resp));
        FileType[] types = new FileType[]{FileType.RulesetLib};

        Repository repo = repositoryService.loadRepository(projectName, user, false, types, "");

        List<RepositoryFile> repositoryFileList = fetchRsl(repo.getRootFile());
        // 遍历文件
        for (RepositoryFile repositoryFile : repositoryFileList) {
            try {
                InputStream inputStream = null;
                inputStream = repositoryService.readFile(repositoryFile.getFullPath(), null);
                Element element = parseXml(inputStream);

                // 编译文件
                RuleSetDeserializer ruleSetDeserializer = (RuleSetDeserializer) applicationContext.getBean(RuleSetDeserializer.BEAN_ID);
                RuleSet ruleSet = ruleSetDeserializer.deserialize(element);
                inputStream.close();

                // 遍历规则
                for (Rule rule : ruleSet.getRules()) {
                    if (ruleKey.equals(rule.getName())) {
                        ruleList.add(rule);
                    }
                }
            } catch (Exception ex) {
                throw new RuleException(ex);
            }
        }
        writeObjectToJson(resp, ruleList);
    }

    private List<RepositoryFile> fetchRsl(RepositoryFile repositoryFile) {
        List<RepositoryFile> repositoryFileList = new ArrayList<>();
        // 判断文件类型
        if (Type.rule == repositoryFile.getType()) {
            repositoryFileList.add(repositoryFile);
        } else if (repositoryFile.getChildren() != null) {
            for (RepositoryFile repositoryFile1 : repositoryFile.getChildren()) {
                repositoryFileList.addAll(fetchRsl(repositoryFile1));
            }
        }

        return repositoryFileList;
    }

    protected Element parseXml(InputStream stream) {
        SAXReader reader = new SAXReader();
        Document document;
        try {
            document = reader.read(stream);
            return document.getRootElement();
        } catch (DocumentException e) {
            throw new RuleException(e);
        }
    }

    public void setRepositoryService(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Override
    public String url() {
        return "/common";
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        super.setApplicationContext(applicationContext);
    }
}
