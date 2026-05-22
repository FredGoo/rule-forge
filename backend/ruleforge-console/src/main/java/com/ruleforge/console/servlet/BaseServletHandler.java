package com.ruleforge.console.servlet;

import com.ruleforge.exception.RuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;


/**
 * @author Jacky.gao
 * @since 2016年6月3日
 */
public abstract class BaseServletHandler implements ServletHandler {
    private Logger logger = LoggerFactory.getLogger(BaseServletHandler.class);

    protected void invokeMethod(String methodName, HttpServletRequest req, HttpServletResponse resp) {
        Method method;
        try {
            method = this.getClass().getMethod(methodName, new Class<?>[]{HttpServletRequest.class, HttpServletResponse.class});
            method.invoke(this, new Object[]{req, resp});
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            logger.error("BaseServletHandler invokeMethod, error", e);
            throw new RuleException(e);
        }
    }

    protected String retriveMethod(HttpServletRequest req) throws ServletException {
        String path = req.getContextPath() + URuleServlet.URL;
        String uri = req.getRequestURI();
        String targetUrl = uri.substring(path.length());
        int slashPos = targetUrl.indexOf("/", 1);
        if (slashPos > -1) {
            String methodName = targetUrl.substring(slashPos + 1).trim();
            return methodName.length() > 0 ? methodName : null;
        }
        return null;
    }
}
