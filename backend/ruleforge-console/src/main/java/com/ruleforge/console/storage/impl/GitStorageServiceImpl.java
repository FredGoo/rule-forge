package com.ruleforge.console.storage.impl;

import com.ruleforge.console.model.User;
import com.ruleforge.console.storage.GitStorageService;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
public class GitStorageServiceImpl implements GitStorageService {

    @Value("${ruleforgeV2.git.base:/opt/data}")
    private String gitBase;

    @Override
    public boolean cloneRepository(String projectName, File localPath, User user) throws Exception {
        log.info("Attempting to clone repository from {} to {}", projectName, localPath.getAbsolutePath());
        // TODO: 使用 JGit 实现克隆逻辑
        // try (Git git = Git.cloneRepository()
        //         .setURI(repositoryUrl)
        //         .setDirectory(localPath)
        //         // .setCredentialsProvider(new UsernamePasswordCredentialsProvider("username", "password")) // 如果需要认证
        //         .call()) {
        //     log.info("Repository cloned successfully to {}", localPath.getAbsolutePath());
        //     return true;
        // } catch (Exception e) {
        //     log.error("Error cloning repository: {}", e.getMessage(), e);
        //     throw e;
        // }
        log.info("cloneRepository not implemented yet");
        return false;
    }

    @Override
    public boolean pullUpdates(File localPath, User user) throws Exception {
        log.info("Attempting to pull updates for repository at {}", localPath.getAbsolutePath());
        // TODO: 使用 JGit 实现拉取更新逻辑
        // try (Git git = Git.open(localPath)) {
        //     PullResult result = git.pull().call();
        //     log.info("Pull result: {}", result.isSuccessful());
        //     return result.isSuccessful();
        // } catch (Exception e) {
        //     log.error("Error pulling updates: {}", e.getMessage(), e);
        //     throw e;
        // }
        log.info("pullUpdates not implemented yet");
        return false;
    }

    @Override
    public boolean commitChanges(File localPath, String commitMessage, User user) throws Exception {
        log.info("Attempting to commit changes in {} with message: '{}'", localPath.getAbsolutePath(), commitMessage);
        // TODO: 使用 JGit 实现提交逻辑
        // try (Git git = Git.open(localPath)) {
        //     git.add().addFilepattern(".").call(); // 添加所有更改的文件
        //     String authorName = (user != null && user.getUsername() != null) ? user.getUsername() : "DefaultUser";
        //     String authorEmail = (user != null && user.getEmail() != null) ? user.getEmail() : "default@example.com";
        //     CommitCommand commit = git.commit();
        //     commit.setMessage(commitMessage);
        //     commit.setAuthor(authorName, authorEmail);
        //     commit.call();
        //     log.info("Changes committed successfully.");
        //     return true;
        // } catch (Exception e) {
        //     log.error("Error committing changes: {}", e.getMessage(), e);
        //     throw e;
        // }
        log.info("commitChanges not implemented yet");
        return false;
    }

    @Override
    public boolean pushChanges(File localPath, User user) throws Exception {
        log.info("Attempting to push changes from {}", localPath.getAbsolutePath());
        // TODO: 使用 JGit 实现推送逻辑
        // try (Git git = Git.open(localPath)) {
        //     Iterable<PushResult> results = git.push()
        //             // .setCredentialsProvider(new UsernamePasswordCredentialsProvider("username", "password")) // 如果需要认证
        //             .call();
        //     results.forEach(result -> {
        //         result.getRemoteUpdates().forEach(remoteUpdate -> {
        //             log.info("Push status: {} - {}", remoteUpdate.getRemoteName(), remoteUpdate.getStatus());
        //         });
        //     });
        //     log.info("Changes pushed successfully.");
        //     return true; // 简化的成功判断，实际应检查 PushResult
        // } catch (Exception e) {
        //     log.error("Error pushing changes: {}", e.getMessage(), e);
        //     throw e;
        // }
        log.info("pushChanges not implemented yet");
        return false;
    }

    @Override
    public boolean createBranch(File localPath, String branchName, User user) throws Exception {
        log.info("Attempting to create branch '{}' in {}", branchName, localPath.getAbsolutePath());
        // TODO: 使用 JGit 实现创建分支逻辑
        // try (Git git = Git.open(localPath)) {
        //     git.branchCreate().setName(branchName).call();
        //     log.info("Branch '{}' created successfully.", branchName);
        //     return true;
        // } catch (Exception e) {
        //     log.error("Error creating branch '{}': {}", branchName, e.getMessage(), e);
        //     throw e;
        // }
        log.info("createBranch not implemented yet");
        return false;
    }

    @Override
    public boolean checkoutBranch(File localPath, String branchName, User user) throws Exception {
        log.info("Attempting to checkout branch '{}' in {}", branchName, localPath.getAbsolutePath());
        // TODO: 使用 JGit 实现切换分支逻辑
        // try (Git git = Git.open(localPath)) {
        //     git.checkout().setName(branchName).call();
        //     log.info("Checked out to branch '{}' successfully.", branchName);
        //     return true;
        // } catch (Exception e) {
        //     log.error("Error checking out branch '{}': {}", branchName, e.getMessage(), e);
        //     throw e;
        // }
        log.info("checkoutBranch not implemented yet");
        return false;
    }

    @Override
    public String getCurrentBranch(File localPath) throws Exception {
        log.info("Attempting to get current branch for {}", localPath.getAbsolutePath());
        // TODO: 使用 JGit 实现获取当前分支逻辑
        // try (Git git = Git.open(localPath)) {
        //     return git.getRepository().getBranch();
        // } catch (Exception e) {
        //     log.error("Error getting current branch: {}", e.getMessage(), e);
        //     throw e;
        // }
        log.info("getCurrentBranch not implemented yet");
        return null;
    }

    @Override
    public List<String> listLocalBranches(File localPath) throws Exception {
        log.info("Attempting to list local branches for {}", localPath.getAbsolutePath());
        // TODO: 使用 JGit 实现列出本地分支逻辑
        // try (Git git = Git.open(localPath)) {
        //     List<Ref> branches = git.branchList().call();
        //     return branches.stream()
        //             .map(Ref::getName)
        //             .collect(Collectors.toList());
        // } catch (Exception e) {
        //     log.error("Error listing local branches: {}", e.getMessage(), e);
        //     throw e;
        // }
        log.info("listLocalBranches not implemented yet");
        return Collections.emptyList();
    }

    @Override
    public List<String> compareTags(String projectName, String originVersion, String targetVersion) throws Exception {

        // git diff
        if (originVersion != null && targetVersion != null) {
            Path gitBasePath = Paths.get(gitBase, projectName);

            // 检查目录是否存在
            if (Files.exists(gitBasePath) && Files.isDirectory(gitBasePath) && Files.exists(gitBasePath.resolve(".git"))) {
                try (Git git = Git.open(gitBasePath.toFile())) {
                    // 比较 originVersion 和 targetVersion 的 tag 差异
                    Repository repo = git.getRepository();

                    ObjectId oldTagId = repo.resolve(String.format("refs/tags/%s^{tree}", originVersion));
                    ObjectId newTagId = repo.resolve(String.format("refs/tags/%s^{tree}", targetVersion));

                    CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
                    CanonicalTreeParser newTreeIter = new CanonicalTreeParser();

                    try (ObjectReader reader = repo.newObjectReader()) {
                        oldTreeIter.reset(reader, oldTagId);
                        newTreeIter.reset(reader, newTagId);
                    }

                    List<DiffEntry> diffs = git.diff()
                            .setOldTree(oldTreeIter)
                            .setNewTree(newTreeIter)
                            .call();

                    // 使用单个输出流和分隔符
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    String separator = "\n===DIFF_ENTRY_SEPARATOR===\n";
                    
                    try (DiffFormatter formatter = new DiffFormatter(out)) {
                        formatter.setRepository(repo);
                        for (int i = 0; i < diffs.size(); i++) {
                            DiffEntry entry = diffs.get(i);
                            formatter.format(entry);
                            
                            // 在每个差异后添加分隔符（除了最后一个）
                            if (i < diffs.size() - 1) {
                                out.write(separator.getBytes(StandardCharsets.UTF_8));
                            }
                        }
                    }

                    // 将整个输出转换为字符串，然后按分隔符分割
                    String allDiffs = out.toString("UTF-8");
                    List<String> diffList = new ArrayList<>();
                    
                    if (!allDiffs.trim().isEmpty()) {
                        String[] diffArray = allDiffs.split(Pattern.quote(separator));
                        diffList = Arrays.asList(diffArray);
                    }

                    return diffList;
                } catch (Exception e) {
                    log.error("Failed to process Git repository in: {}", gitBasePath, e);
                }
            }
        }

        return Collections.emptyList();
    }
}