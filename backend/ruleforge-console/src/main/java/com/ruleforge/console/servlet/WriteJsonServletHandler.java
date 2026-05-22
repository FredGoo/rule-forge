package com.ruleforge.console.servlet;

import com.ruleforge.Configure;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;

/**
 * @author Jacky.gao
 * 2016年5月23日
 */
public abstract class WriteJsonServletHandler extends BaseServletHandler {

    protected void writeObjectToJson(HttpServletResponse resp, Object obj) throws IOException {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setContentType("text/json");
        resp.setCharacterEncoding("UTF-8");
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(Include.NON_NULL);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.setDateFormat(new SimpleDateFormat(Configure.getDateFormat()));
        OutputStream out = resp.getOutputStream();
        try {
            mapper.writeValue(out, obj);
        } finally {
            out.flush();
            out.close();
        }
    }

    protected void writeStringToJson(HttpServletResponse resp, String content) throws IOException {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setContentType("text/json");
        resp.setCharacterEncoding("UTF-8");

        PrintWriter writer = resp.getWriter();
        writer.write(content);
        writer.flush();
        writer.close();
    }
}
