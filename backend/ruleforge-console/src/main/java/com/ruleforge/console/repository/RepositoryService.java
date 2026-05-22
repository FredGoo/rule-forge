package com.ruleforge.console.repository;

import java.io.InputStream;

public interface RepositoryService {

    InputStream readFile(String path, String version) throws Exception;
}
