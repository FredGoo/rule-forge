package com.ruleforge.console.controller;

import com.ruleforge.Utils;
import com.ruleforge.console.servlet.flow.FlowDefinitionWrapper;
import com.ruleforge.exception.RuleException;
import com.ruleforge.model.flow.FlowDefinition;
import com.ruleforge.parse.deserializer.FlowDeserializer;
import com.ruleforge.console.service.RuleForgeRepositoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.dom4j.Element;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;

@Slf4j
@RestController
@RequestMapping("/${ruleforgeV2.root.path}/ruleflowdesigner")
@RequiredArgsConstructor
public class FlowController extends BaseController {

    private final RuleForgeRepositoryService ruleforgeRepositoryService;
    private final FlowDeserializer flowDeserializer;

    @GetMapping(value = "/loadFlowDefinition", produces = "text/json;charset=UTF-8")
    public String loadFlowDefinition(@RequestParam String file,
                                     @RequestParam(required = false) String version) {
        InputStream inputStream;
        file = Utils.decodeURL(file);
        try {
            if (StringUtils.isEmpty(version)) {
                inputStream = this.ruleforgeRepositoryService.readFile(file, null);
            } else {
                inputStream = this.ruleforgeRepositoryService.readFile(file, version);
            }
            Element root = parseXml(inputStream);
            FlowDefinition fd = this.flowDeserializer.deserialize(root);
            inputStream.close();
            return writeObjectToJson(new FlowDefinitionWrapper(fd));
        } catch (Exception ex) {
            throw new RuleException(ex);
        }
    }
}
