package com.ruleforge.console.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author Jacky.gao
 * @since 2016年5月23日
 */
public interface ServletHandler {

    void execute(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException;

    String url();
}
