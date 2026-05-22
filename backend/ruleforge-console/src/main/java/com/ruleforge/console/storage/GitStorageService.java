package com.ruleforge.console.storage;

import com.ruleforge.console.model.User;

import java.io.File;
import java.util.List;

/**
 * Git 本地操作服务接口
 */
public interface GitStorageService {

    /**
     * 克隆远程 Git 仓库到本地
     *
     * @param repositoryUrl 远程仓库 URL
     * @param localPath     本地存储路径
     * @param user          操作用户 (可选, 用于认证等)
     * @return 克隆操作是否成功
     * @throws Exception 操作异常
     */
    boolean cloneRepository(String repositoryUrl, File localPath, User user) throws Exception;

    /**
     * 从远程仓库拉取最新更新
     *
     * @param localPath 本地仓库路径
     * @param user      操作用户 (可选)
     * @return 拉取操作是否成功
     * @throws Exception 操作异常
     */
    boolean pullUpdates(File localPath, User user) throws Exception;

    /**
     * 提交本地更改到本地仓库
     *
     * @param localPath     本地仓库路径
     * @param commitMessage 提交信息
     * @param user          操作用户 (可选)
     * @return 提交操作是否成功
     * @throws Exception 操作异常
     */
    boolean commitChanges(File localPath, String commitMessage, User user) throws Exception;

    /**
     * 将本地提交推送到远程仓库
     *
     * @param localPath 本地仓库路径
     * @param user      操作用户 (可选)
     * @return 推送操作是否成功
     * @throws Exception 操作异常
     */
    boolean pushChanges(File localPath, User user) throws Exception;

    /**
     * 创建新分支
     *
     * @param localPath  本地仓库路径
     * @param branchName 分支名称
     * @param user       操作用户 (可选)
     * @return 创建分支操作是否成功
     * @throws Exception 操作异常
     */
    boolean createBranch(File localPath, String branchName, User user) throws Exception;

    /**
     * 切换到指定分支
     *
     * @param localPath  本地仓库路径
     * @param branchName 分支名称
     * @param user       操作用户 (可选)
     * @return 切换分支操作是否成功
     * @throws Exception 操作异常
     */
    boolean checkoutBranch(File localPath, String branchName, User user) throws Exception;

    /**
     * 获取当前分支名称
     *
     * @param localPath 本地仓库路径
     * @return 当前分支名称
     * @throws Exception 操作异常
     */
    String getCurrentBranch(File localPath) throws Exception;

    /**
     * 获取所有本地分支列表
     *
     * @param localPath 本地仓库路径
     * @return 本地分支列表
     * @throws Exception 操作异常
     */
    List<String> listLocalBranches(File localPath) throws Exception;

    List<String> compareTags(String projectName, String originVersion, String targetVersion) throws Exception;

}