package com.ruleforge.console.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruleforge.console.EnvironmentProvider;
import com.ruleforge.console.repository.model.RepositoryFile;
import com.ruleforge.console.entity.ProjectEntity;
import com.ruleforge.console.entity.ProjectVersionEntity;
import com.ruleforge.console.mapper.ProjectMapper;
import com.ruleforge.console.mapper.ProjectVersionMapper;
import com.ruleforge.console.model.Repository;
import com.ruleforge.console.model.User;
import com.ruleforge.console.service.RuleForgeRepositoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.TimeZone;

@Slf4j
@RestController("ruleforgeApiController")
@RequestMapping("/${ruleforgeV2.root.path}/api")
@RequiredArgsConstructor
public class ApiController {

    private final RuleForgeRepositoryService ruleforgeRepositoryService;
    private final EnvironmentProvider environmentProvider;
    private final ProjectMapper projectMapper;
    private final ProjectVersionMapper projectVersionMapper;
    @Value("${ruleforgeV2.git.base:/opt/data}")
    private String gitBase;

    @GetMapping("/fix-git")
    public ResponseEntity<?> fixGit(@RequestParam String projectName) {
        log.info("Received request to git.");
        try {
            User user = this.environmentProvider.getLoginUser(null);

            // 获取版本文件
            Repository repository = this.ruleforgeRepositoryService.loadRepository(projectName, user, false, null, null);

            // 使用gitBase路径创建目录并写入文件
            Path gitBasePath = Paths.get(gitBase, projectName);
            if (!Files.exists(gitBasePath)) {
                Files.createDirectories(gitBasePath);
                log.info("Created directory: {}", gitBasePath);
            }

            // todo
            LambdaQueryWrapper<ProjectEntity> projectEntityLambdaQueryWrapper = new LambdaQueryWrapper<ProjectEntity>()
                    .eq(ProjectEntity::getName, projectName);
            ProjectEntity projectEntity = this.projectMapper.selectOne(projectEntityLambdaQueryWrapper);
            LambdaQueryWrapper<ProjectVersionEntity> projectVersionEntityLambdaQueryWrapper = new LambdaQueryWrapper<ProjectVersionEntity>()
                    .eq(ProjectVersionEntity::getProjectId, projectEntity.getId())
                    .orderByAsc(ProjectVersionEntity::getVersionNumReal);
            List<ProjectVersionEntity> projectVersionEntityList = this.projectVersionMapper.selectList(projectVersionEntityLambdaQueryWrapper);

            // 使用 JGit 初始化 Git 仓库
            try (Git git = Git.init().setDirectory(gitBasePath.toFile()).call()) {
                log.info("Initialized Git repository in: {}", gitBasePath);
                RepositoryFile rootFile = repository.getRootFile().getChildren().get(0);

                for (ProjectVersionEntity projectVersion : projectVersionEntityList) {
                    String version = projectVersion.getVersionName();

                    // 检查本地 Git 仓库是否已经存在该版本的 tag
                    boolean tagExists = git.tagList().call().stream()
                            .anyMatch(tag -> tag.getName().equals("refs/tags/" + version));
                    if (tagExists) {
                        log.info("Tag {} already exists. Skipping version: {}", version, version);
                        continue;
                    }

                    iterateRepositoryFile(rootFile, Paths.get(gitBase), version);

                    // 提交更改到 Git
                    git.add().addFilepattern(".").call();

                    // 设定自定义的时间（作者时间 & 提交时间）
                    TimeZone timeZone = TimeZone.getTimeZone("Asia/Shanghai");
                    PersonIdent committer = new PersonIdent(projectVersion.getCreateUser(), "ruleforge@example.com", projectVersion.getCreateTime(), timeZone);

                    git.commit()
                            .setMessage(projectVersion.getComment())
                            .setCommitter(committer)
                            .call();
                    log.info("Committed changes to Git repository in: {}", gitBasePath);

                    // 打一个 tag
                    git.tag().setName(version).call();
                    log.info("Created tag: {} in Git repository in: {}", version, gitBasePath);
                }
            } catch (Exception e) {
                log.error("Failed to process Git repository in: {}", gitBasePath, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to initialize, commit, or tag in Git repository.");
            }

            return ResponseEntity.ok(null);
        } catch (Exception e) {
            log.error("Project git failed.", e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("git failed: " + e.getMessage());
        }
    }

    @GetMapping("/fix-git-push")
    public ResponseEntity<?> fixGitPush(@RequestParam String projectName) {
        log.info("Received request to git push.");
        try {
            // 使用gitBase路径创建目录并写入文件
            Path gitBasePath = Paths.get(gitBase, projectName);

            // 使用 JGit 初始化 Git 仓库
            try (Git git = Git.init().setDirectory(gitBasePath.toFile()).call()) {
                log.info("Initialized Git repository in: {}", gitBasePath);

                // 设置远程仓库地址
                String remoteRepoUrl = "http://localhost:8083/ruleforge-data-dev/" + projectName + ".git";
                git.remoteAdd().setName("origin").setUri(new URIish(remoteRepoUrl)).call();

                // 推送主分支到远程
                git.push()
                        .setRemote("origin")
                        .setRefSpecs(new RefSpec("refs/heads/master"))
                        .setCredentialsProvider(new UsernamePasswordCredentialsProvider("ruleforge", "CHANGE_ME"))
                        .call();

                // 推送所有 tag
                git.push()
                        .setRemote("origin")
                        .setPushTags()
                        .setCredentialsProvider(new UsernamePasswordCredentialsProvider("ruleforge", "CHANGE_ME"))
                        .call();

            } catch (Exception e) {
                log.error("Failed to process Git repository in: {}", gitBasePath, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to initialize, commit, or tag in Git repository.");
            }

            return ResponseEntity.ok(null);
        } catch (Exception e) {
            log.error("Project git failed.", e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("git failed: " + e.getMessage());
        }
    }

    private void iterateRepositoryFile(RepositoryFile repositoryFile, Path gitBasePath, String version) throws Exception {
        List<RepositoryFile> repositoryFileList = repositoryFile.getChildren();
        if (repositoryFileList != null && !repositoryFileList.isEmpty()) {
            for (RepositoryFile repositoryFileItem : repositoryFileList) {
                if (repositoryFileItem.getChildren() != null && !repositoryFileItem.getChildren().isEmpty()) {
                    Path folderPath = gitBasePath.resolve(repositoryFileItem.getFullPath().substring(1));
                    if (!Files.exists(folderPath)) {
                        Files.createDirectories(folderPath);
                        log.info("Created directory: {}", folderPath);
                    }
                    iterateRepositoryFile(repositoryFileItem, gitBasePath, version);
                } else {
                    log.info("iterateRepositoryFile repositoryFileItem: {}", repositoryFileItem.getFullPath());
                    syncVersionFileList(repositoryFileItem, gitBasePath, version);
                }
            }
        }
    }

    private void syncVersionFileList(RepositoryFile repositoryFile, Path gitBasePath, String version) throws Exception {
        Path filePath = gitBasePath.resolve(repositoryFile.getFullPath().substring(1));

        InputStream is = this.ruleforgeRepositoryService.readFile(repositoryFile.getFullPath(), "latest", version, false);
        if (is != null) {
            String fileContent = IOUtils.toString(is);
            Files.write(filePath, fileContent.getBytes());
            log.info("Wrote file: {}", filePath);
        } else {
            log.info("Wrote none file: {}", filePath);
        }
    }

}
