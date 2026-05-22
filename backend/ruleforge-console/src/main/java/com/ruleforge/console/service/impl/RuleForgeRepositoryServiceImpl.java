package com.ruleforge.console.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ruleforge.Utils;
import com.ruleforge.console.repository.RepositoryService;
import com.ruleforge.console.repository.model.*;
import com.ruleforge.console.servlet.common.RefFile;
import com.ruleforge.console.servlet.frame.ExportProject;
import com.ruleforge.exception.RuleException;
import com.ruleforge.console.entity.*;
import com.ruleforge.console.exception.NoPermissionException;
import com.ruleforge.console.mapper.*;
import com.ruleforge.console.model.DefaultUser;
import com.ruleforge.console.model.PackageConfig;
import com.ruleforge.console.model.Repository;
import com.ruleforge.console.model.User;
import com.ruleforge.console.service.RuleForgeRepositoryService;
import com.ruleforge.console.service.PermissionService;
import com.ruleforge.console.service.RepositoryInterceptor;
import com.ruleforge.console.storage.ProjectStorageService;
import com.ruleforge.console.util.CompareUtils;
import com.ruleforge.console.util.FileTypeUtils;
import com.ruleforge.console.util.VersionFileUtils;
import com.ruleforge.console.util.VersionUtils;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.ruleforge.console.repository.BaseRepositoryService.CLIENT_CONFIG_FILE;
import static com.ruleforge.console.repository.BaseRepositoryService.RES_PACKGE_FILE;
import static com.ruleforge.console.storage.RuleForgeBaseRepositoryService.PACKAGE_CONFIG_FILE;
import static com.ruleforge.console.storage.RuleForgeBaseRepositoryService.RES_PACKAGE_FILE;
import static com.ruleforge.console.storage.impl.DatabaseProjectStorageServiceImpl.SNAPSHOT_VERSION;
import static com.ruleforge.console.storage.impl.DatabaseProjectStorageServiceImpl.SNAPSHOT_VERSION_REAL;


@Slf4j
@Component("ruleforge.repositoryService")
@RequiredArgsConstructor
public class RuleForgeRepositoryServiceImpl implements RuleForgeRepositoryService, RepositoryService {

    private final PermissionService permissionService;
    private final ProjectMapper projectMapper;
    private final ProjectImportFlowMapper projectImportFlowMapper;
    private final FileMapper fileMapper;
    private final FileRelationMapper fileRelationMapper;
    private final FileVersionMapper fileVersionMapper;
    private final LockMapper lockMapper;
    private final ProjectVersionMapper projectVersionMapper;
    private final ProjectVersionMappingMapper projectVersionMappingMapper;
    private final ProjectStorageService projectStorageService;
    private final RepositoryInterceptor repositoryInterceptor;
    private final ProjectRuntimeConfigMapper projectRuntimeConfigMapper;



    @Override
    public List<RepositoryFile> loadProjects(String companyId) throws Exception {
        return null;
    }

    @Override
    public List<String> loadProjectNames() throws Exception {
        List<ProjectEntity> projects = projectMapper.selectList(null);
        List<String> names = new ArrayList<>();
        for (ProjectEntity project : projects) {
            names.add(project.getName());
        }
        return names;
    }

    @Override
    public List<com.ruleforge.console.model.ClientConfig> loadClientConfigs(String project) throws Exception {
        return new ArrayList<>();
    }

    @Override
    public List<com.ruleforge.console.servlet.permission.UserPermission> loadResourceSecurityConfigs(String companyId) throws Exception {
        return new ArrayList<>();
    }

    @Override
    public InputStream readFile(String path) throws Exception {
        return readFile(path, null);
    }

    @Override
    public List<ResourcePackage> loadProjectResourcePackages(String project) throws Exception {
        return loadProjectResourcePackages(project, null);
    }

    @Override
    public List<ResourcePackage> loadProjectResourcePackages(String project, String env) throws Exception {
        String[] projectArray = project.split(":");
        String version = null;
        ProjectEntity projectEntity = this.projectMapper.selectOne(new LambdaQueryWrapper<ProjectEntity>()
                .eq(ProjectEntity::getName, projectArray[0])
                .last("limit 1"));

        if (projectArray.length > 1) {
            project = projectArray[0];
            version = projectArray[1];
        } else if (org.springframework.util.StringUtils.hasText(env)) {
            LambdaQueryWrapper<ProjectRuntimeConfigEntity> projectRuntimeConfigEntityLambdaQueryWrapper = new LambdaQueryWrapper<ProjectRuntimeConfigEntity>()
                    .eq(ProjectRuntimeConfigEntity::getProjectId, projectEntity.getId())
                    .eq(ProjectRuntimeConfigEntity::getExecEnv, env)
                    .last("limit 1");
            ProjectRuntimeConfigEntity projectRuntime = this.projectRuntimeConfigMapper.selectOne(projectRuntimeConfigEntityLambdaQueryWrapper);
            version = projectRuntime.getProjectVersion();
        }

        String filePath = processPath(project) + "/" + RES_PACKAGE_FILE;
        InputStream inputStream = readFile(filePath, version);
        String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        inputStream.close();

        // todo
        List<ProjectRuntimeConfigEntity> projectRuntimeConfigEntityList = this.projectRuntimeConfigMapper.selectList(new LambdaQueryWrapper<ProjectRuntimeConfigEntity>()
                .eq(ProjectRuntimeConfigEntity::getProjectId, projectEntity.getId()));
        Map<String, String> packageRuntimeMap = new HashMap<>();
        for (ProjectRuntimeConfigEntity projectRuntimeConfigEntity : projectRuntimeConfigEntityList) {
            packageRuntimeMap.put(projectRuntimeConfigEntity.getPackageId() + "_" + projectRuntimeConfigEntity.getExecEnv(), projectRuntimeConfigEntity.getProjectVersion());
        }

        Document document = DocumentHelper.parseText(content);
        Element rootElement = document.getRootElement();
        SimpleDateFormat sd = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        List<ResourcePackage> packages = new ArrayList<>();
        for (Object obj : rootElement.elements()) {
            if (!(obj instanceof Element)) {
                continue;
            }
            Element element = (Element) obj;
            if (!element.getName().equals("res-package")) {
                continue;
            }
            ResourcePackage p = new ResourcePackage();
            String dateStr = element.attributeValue("create_date");
            if (dateStr != null) {
                p.setCreateDate(sd.parse(dateStr));
            }
            p.setId(element.attributeValue("id"));
            p.setName(element.attributeValue("name"));
            p.setVersion(packageRuntimeMap.get(p.getId() + "_" + "prod"));
            p.setTestVersion(packageRuntimeMap.get(p.getId() + "_" + "test"));
            p.setProject(project);
            List<ResourceItem> items = new ArrayList<>();
            for (Object o : element.elements()) {
                if (!(o instanceof Element)) {
                    continue;
                }
                Element ele = (Element) o;
                if (!ele.getName().equals("res-package-item")) {
                    continue;
                }
                ResourceItem item = new ResourceItem();
                item.setName(ele.attributeValue("name"));
                item.setPackageId(p.getId());
                item.setPath(ele.attributeValue("path"));
                item.setVersion(ele.attributeValue("version"));
                items.add(item);
            }
            p.setResourceItems(items);
            packages.add(p);
        }
        return packages;
    }

    @Override
    public boolean fileExistCheck(String filePath) throws Exception {
        filePath = processPath(filePath);
        if (filePath.contains(" ") || filePath.isEmpty()) {
            return true;
        }

        LambdaQueryWrapper<FileEntity> fileLQW = new LambdaQueryWrapper<FileEntity>()
                .select(FileEntity::getId)
                .eq(FileEntity::getFilePath, filePath)
                .last("limit 1");
        FileEntity file = this.fileMapper.selectOne(fileLQW);
        return file != null;
    }

    @Override
    public RepositoryFile createProject(String projectName, User user, boolean classify) throws Exception {
        if (!permissionService.isAdmin()) {
            throw new NoPermissionException();
        }
        this.repositoryInterceptor.createProject(projectName);
        String projectRootPath = processPath(projectName);
        if (fileExistCheck(projectRootPath)) {
            throw new RuleException("Project [" + projectName + "] already exist.");
        }

        ProjectEntity project = new ProjectEntity();
        project.setName(projectName);
        project.setCreateTime(new Date());
        this.projectMapper.insert(project);

        createResourcePackageFile(project.getId(), projectName, user);
        createAllResourceFolder(project.getId(), projectRootPath);
        createPackageConfigFile(projectName, user);
        createClientConfigFile(projectName, user);
        return buildProjectFile(project, null, classify, null);
    }

    private void createAllResourceFolder(Long projectId, String projectRootPath) {
        FileEntity allResource = new FileEntity();
        allResource.setName("资源");
        allResource.setFileType(Type.all.ordinal());
        allResource.setProjectId(projectId);
        allResource.setFilePath(projectRootPath);
        allResource.setCreateTime(new Date());
        this.fileMapper.insert(allResource);

        FileRelationEntity fileRelation = new FileRelationEntity();
        fileRelation.setAncestor(projectId);
        fileRelation.setDescendant(allResource.getId());
        fileRelation.setDistance(1);
        fileRelation.setProjectId(projectId);
        this.fileRelationMapper.insert(fileRelation);
    }

    private void createResourcePackageFile(Long projectId, String project, User user) throws Exception {
        String filePath = processPath(project) + "/" + RES_PACKGE_FILE;
        if (!fileExistCheck(filePath)) {
            FileEntity file = new FileEntity();
            file.setName("知识包.rp");
            file.setFileType(Type.resourcePackage.ordinal());
            file.setProjectId(projectId);
            file.setFilePath(filePath);
            file.setCreateTime(new Date());
            this.fileMapper.insert(file);

            FileVersionEntity fileVersionEntity = new FileVersionEntity();
            fileVersionEntity.setFilePath(filePath);
            fileVersionEntity.setFileName(filePath);
            fileVersionEntity.setFileContent("<?xml version=\"1.0\" encoding=\"utf-8\"?><res-packages></res-packages>");
            fileVersionEntity.setVersionNum("latest");
            fileVersionEntity.setProjectId(projectId);
            fileVersionEntity.setCreateUser(user.getUsername());
            fileVersionEntity.setCreateDate(new Date());
            this.fileVersionMapper.insert(fileVersionEntity);

            FileRelationEntity fileRelation = new FileRelationEntity();
            fileRelation.setAncestor(projectId);
            fileRelation.setDescendant(file.getId());
            fileRelation.setDistance(1);
            fileRelation.setProjectId(projectId);
            this.fileRelationMapper.insert(fileRelation);
        }
    }

    private void createPackageConfigFile(String project, User user) throws Exception {
        String filePath = processPath(project) + "/" + PACKAGE_CONFIG_FILE;
        if (!fileExistCheck(filePath)) {
            createFile(filePath, "<?xml version=\"1.0\" encoding=\"utf-8\"?><package-config></package-config>", user);
        }
    }

    private void createClientConfigFile(String project, User user) throws Exception {
        String filePath = processPath(project) + "/" + CLIENT_CONFIG_FILE;
        if (!fileExistCheck(filePath)) {
            createFile(filePath, "<?xml version=\"1.0\" encoding=\"utf-8\"?><client-config></client-config>", user);
        }
    }

    @Override
    public void createDir(String path, User user) throws Exception {
        if (!this.permissionService.isAdmin()) {
            throw new NoPermissionException();
        }
        createFileNode(path, null, user, false);
    }

    @Override
    public void createFile(String path, String content, User user) throws Exception {
        if (!this.permissionService.isAdmin()) {
            throw new NoPermissionException();
        }
        createFileNode(path, content, user, true);
    }

    @Override
    public String saveFile(String path, String content, boolean newVersion, String versionComment, User user) throws Exception {
        return saveFile(path, content, newVersion, versionComment, null, null, user);
    }

    @Override
    public String saveFile(String path, String content, boolean newVersion, String versionComment, String beforeComment, String afterComment, User user) throws Exception {
        return saveFile(path, content, newVersion, versionComment, beforeComment, afterComment, user, null);
    }

    @Override
    public String saveFile(String path, String content, boolean newVersion, String versionComment, String beforeComment, String afterComment, User user, Date createTime) throws Exception {
        path = Utils.decodeURL(path);
        boolean packageFile = false;
        if (path.contains(RES_PACKAGE_FILE)) {
            packageFile = true;
            if (!this.permissionService.projectPackageHasWritePermission(path)) {
                throw new NoPermissionException();
            }
        }
        if (!this.permissionService.fileHasWritePermission(path)) {
            throw new NoPermissionException();
        }

        path = processPath(path);
        int pos = path.indexOf(":");
        if (pos != -1) {
            path = path.substring(0, pos);
        }

        Long lockVersion = lockPath(path, user);
        if (lockVersion == null) {
            return null;
        }

        String versionNum = null;
        try {
            LambdaQueryWrapper<FileEntity> fileLQW = new LambdaQueryWrapper<FileEntity>()
                    .eq(FileEntity::getFilePath, path)
                    .last("limit 1");
            FileEntity file = this.fileMapper.selectOne(fileLQW);
            if (file == null) {
                throw new RuleException("File [" + path + "] not exist.");
            }
            lockCheck(file, user);

            Calendar calendar = Calendar.getInstance();
            if (createTime == null) {
                calendar.setTime(new Date());
            } else {
                calendar.setTime(createTime);
            }

            if (newVersion) {
                LambdaQueryWrapper<FileVersionEntity> tmpFileVersionLQW = new LambdaQueryWrapper<FileVersionEntity>()
                        .eq(FileVersionEntity::getFileId, file.getId());
                if (packageFile) {
                    tmpFileVersionLQW.orderByDesc(FileVersionEntity::getVersionNumReal).last("limit 1");
                } else {
                    tmpFileVersionLQW.eq(FileVersionEntity::getVersionNum, SNAPSHOT_VERSION).last("limit 1");
                }
                FileVersionEntity tmpFileVersion = this.fileVersionMapper.selectOne(tmpFileVersionLQW);

                if (tmpFileVersion != null) {
                    // 获取最新的release版本
                    LambdaQueryWrapper<FileVersionEntity> versionLQW = new LambdaQueryWrapper<FileVersionEntity>()
                            .eq(FileVersionEntity::getFilePath, path)
                            .lt(FileVersionEntity::getVersionNumReal, SNAPSHOT_VERSION_REAL)
                            .orderByDesc(FileVersionEntity::getVersionNumReal, FileVersionEntity::getId)
                            .last("limit 1");
                    FileVersionEntity latestVersion = this.fileVersionMapper.selectOne(versionLQW);

                    String tmpOldVersionNum = tmpFileVersion.getVersionNum();
                    tmpFileVersion = VersionUtils.incrementVersionFileVersion(latestVersion, tmpFileVersion);
                    if (packageFile && !SNAPSHOT_VERSION.equals(tmpOldVersionNum)) {
                        tmpFileVersion.setId(null);
                        tmpFileVersion.setFileContent(content);
                        tmpFileVersion.setAfterComment(null);
                        tmpFileVersion.setCreateUser(user.getUsername());
                        tmpFileVersion.setCreateDate(calendar.getTime());
                        tmpFileVersion.setUpdateTime(null);
                        int insertRes = this.fileVersionMapper.insert(tmpFileVersion);
                    } else {
                        LambdaUpdateWrapper<FileVersionEntity> fileLUW = new LambdaUpdateWrapper<FileVersionEntity>()
                                .eq(FileVersionEntity::getId, tmpFileVersion.getId())
                                .set(FileVersionEntity::getVersionNum, tmpFileVersion.getVersionNum())
                                .set(FileVersionEntity::getVersionNumReal, tmpFileVersion.getVersionNumReal())
                                .set(FileVersionEntity::getUpdateTime, new Date());
                        int updateRes = this.fileVersionMapper.update(null, fileLUW);
                    }
                    versionNum = tmpFileVersion.getVersionNum();
                } else {
                    // 获取最新的release版本
                    LambdaQueryWrapper<FileVersionEntity> versionLQW = new LambdaQueryWrapper<FileVersionEntity>()
                            .eq(FileVersionEntity::getFilePath, path)
                            .lt(FileVersionEntity::getVersionNumReal, SNAPSHOT_VERSION_REAL)
                            .orderByDesc(FileVersionEntity::getVersionNumReal, FileVersionEntity::getId)
                            .last("limit 1");
                    FileVersionEntity latestVersion = this.fileVersionMapper.selectOne(versionLQW);

                    String contentDiff = "";
                    if (latestVersion != null) {
                        contentDiff = CompareUtils.compareContent(latestVersion.getFileContent(), content);
                    }
                    log.info("\n{}", contentDiff);

                    tmpFileVersion = new FileVersionEntity();
                    tmpFileVersion = VersionUtils.incrementVersionFileVersion(latestVersion, tmpFileVersion);
                    tmpFileVersion.setFileId(file.getId());
                    tmpFileVersion.setFileName(file.getFilePath());
                    tmpFileVersion.setFilePath(file.getFilePath());
                    tmpFileVersion.setFileContent(content);
                    tmpFileVersion.setProjectId(file.getProjectId());
                    tmpFileVersion.setAfterComment(contentDiff);
                    tmpFileVersion.setCreateUser(user.getUsername());
                    tmpFileVersion.setCreateDate(calendar.getTime());
                    tmpFileVersion.setUpdateTime(null);
                    tmpFileVersion.setProjectVersionNumReal(SNAPSHOT_VERSION_REAL);
                    int insertRes = this.fileVersionMapper.insert(tmpFileVersion);
                    versionNum = tmpFileVersion.getVersionNum();
                }
            } else {
                // 对比差异
                LambdaQueryWrapper<FileVersionEntity> latestLQW = new LambdaQueryWrapper<FileVersionEntity>()
                        .eq(FileVersionEntity::getFilePath, path)
                        .lt(FileVersionEntity::getVersionNumReal, SNAPSHOT_VERSION_REAL)
                        .orderByDesc(FileVersionEntity::getVersionNumReal)
                        .last("limit 1");
                FileVersionEntity latestVersion = this.fileVersionMapper.selectOne(latestLQW);
                String contentDiff = "";
                if (latestVersion != null) {
                    contentDiff = CompareUtils.compareContent(latestVersion.getFileContent(), content);
                }
                log.info("\n{}", contentDiff);

                LambdaUpdateWrapper<FileVersionEntity> fileLUW = new LambdaUpdateWrapper<FileVersionEntity>()
                        .eq(FileVersionEntity::getFilePath, path)
                        .set(FileVersionEntity::getFileContent, content)
                        .set(FileVersionEntity::getAfterComment, contentDiff)
                        .set(FileVersionEntity::getUpdateTime, new Date());
                if (file.getProjectId() == 0) {
                    fileLUW.eq(FileVersionEntity::getVersionNum, "latest");
                } else {
                    fileLUW.eq(FileVersionEntity::getVersionNum, SNAPSHOT_VERSION);
                }
                int updateRes = this.fileVersionMapper.update(null, fileLUW);
                if (updateRes < 1) {
                    FileVersionEntity newFile = new FileVersionEntity();
                    newFile.setFileId(file.getId());
                    newFile.setFilePath(file.getFilePath());
                    newFile.setFileName(file.getName());
                    newFile.setProjectId(file.getProjectId());
                    newFile.setFileContent(content);
                    newFile.setAfterComment(contentDiff);
                    newFile.setVersionNum(SNAPSHOT_VERSION);
                    newFile.setVersionNumReal(SNAPSHOT_VERSION_REAL);
                    newFile.setProjectVersionNumReal(SNAPSHOT_VERSION_REAL);
                    newFile.setCreateUser(user.getUsername());
                    newFile.setCreateDate(calendar.getTime());
                    this.fileVersionMapper.insert(newFile);
                }
                versionNum = SNAPSHOT_VERSION;
            }
        } finally {
            unlockPath(path, user, lockVersion);
        }

        this.repositoryInterceptor.saveFile(path, content);
        return versionNum;
    }

    @Override
    public List<RefFile> getFlowRefs(List<String> pathList) {
        // 遍历项目
        LambdaQueryWrapper<FileEntity> tmpFileVersionLQW = new LambdaQueryWrapper<FileEntity>()
                .in(FileEntity::getFilePath, pathList);
        List<FileEntity> fileEntityList = this.fileMapper.selectList(tmpFileVersionLQW);

        List<RefFile> repositoryFileList = new ArrayList<>(fileEntityList.size());
        for (FileEntity fileEntity : fileEntityList) {
            RefFile refFile = new RefFile();
            refFile.setName(fileEntity.getName());
            refFile.setPath(fileEntity.getFilePath());
            refFile.setVersion("LATEST");
            // todo
            List<String> versionList = new ArrayList<>(25);
            LambdaQueryWrapper<FileVersionEntity> fileVersionEntityLambdaQueryWrapper = new LambdaQueryWrapper<FileVersionEntity>()
                    .eq(FileVersionEntity::getFileId, fileEntity.getId())
                    .orderByDesc(FileVersionEntity::getVersionNumReal)
                    .last("limit 25");
            List<FileVersionEntity> fileVersionEntityList = this.fileVersionMapper.selectList(fileVersionEntityLambdaQueryWrapper);
            for (FileVersionEntity fileVersion : fileVersionEntityList) {
                versionList.add(fileVersion.getVersionNum());
            }
            refFile.setVersionHistory(versionList);
            if (fileEntity.getFilePath().endsWith(FileType.Ruleset.toString())) {
                refFile.setType("决策集");
            } else if (fileEntity.getFilePath().endsWith(FileType.UL.toString())) {
                refFile.setType("脚本决策集");
            } else if (fileEntity.getFilePath().endsWith(FileType.DecisionTable.toString())) {
                refFile.setType("决策表");
            } else if (fileEntity.getFilePath().endsWith(FileType.ScriptDecisionTable.toString())) {
                refFile.setType("脚本决策表");
            } else if (fileEntity.getFilePath().endsWith(FileType.DecisionTree.toString())) {
                refFile.setType("决策树");
            } else if (fileEntity.getFilePath().endsWith(FileType.RuleFlow.toString())) {
                refFile.setType("决策流");
            } else if (fileEntity.getFilePath().endsWith(FileType.Scorecard.toString())) {
                refFile.setType("评分卡");
            } else if (fileEntity.getFilePath().endsWith(FileType.ComplexScorecard.toString())) {
                refFile.setType("复杂评分卡");
            }

            repositoryFileList.add(refFile);
        }
        return repositoryFileList;
    }

    @Override
    public String getPackageVersionDiff(String project, String version) {
        try {
            LambdaQueryWrapper<ProjectEntity> projectEntityLambdaQueryWrapper = new LambdaQueryWrapper<ProjectEntity>()
                    .eq(ProjectEntity::getName, project)
                    .last("limit 1");
            ProjectEntity projectEntity = this.projectMapper.selectOne(projectEntityLambdaQueryWrapper);
            LambdaQueryWrapper<FileVersionEntity> lqw = new LambdaQueryWrapper<FileVersionEntity>()
                    .eq(FileVersionEntity::getProjectId, projectEntity.getId())
                    .eq(FileVersionEntity::getProjectVersionNumReal, VersionUtils.convertVersionToLong(version));

            List<FileVersionEntity> fileVersionEntityList = this.fileVersionMapper.selectList(lqw);
            StringBuilder sb = new StringBuilder();
            for (FileVersionEntity fileVersion : fileVersionEntityList) {
                sb.append(fileVersion.getFilePath()).append("\n")
                        .append(fileVersion.getAfterComment()).append("\n\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("getPackageVersionDiff", e);
        }

        return null;
    }

    @Override
    public String getFileVersionDiff(String filePath, String targetVersion) {
        LambdaQueryWrapper<FileVersionEntity> lqw = new LambdaQueryWrapper<FileVersionEntity>()
                .eq(FileVersionEntity::getFilePath, filePath)
                .orderByDesc(FileVersionEntity::getVersionNumReal)
                .last("limit 1");

        FileVersionEntity fileVersion = this.fileVersionMapper.selectOne(lqw);
        return fileVersion.getFilePath() + "\n" +
                fileVersion.getAfterComment() + "\n\n";
    }

    @Override
    public void deleteFile(String path, User user) throws Exception {
        deleteFile(path, user, null);
    }

    @Override
    public void deleteFile(String path, User user, Type type) throws Exception {
        // 获取所有file
        LambdaQueryWrapper<FileEntity> fileLQW = new LambdaQueryWrapper<FileEntity>()
                .select(FileEntity::getId, FileEntity::getFileType)
                .eq(FileEntity::getFilePath, path)
                .last("limit 1");
        if (type != null) {
            fileLQW.eq(FileEntity::getFileType, type.ordinal());
        }
        FileEntity file = this.fileMapper.selectOne(fileLQW);
        if (file == null) {
            return;
        }

        long fileId = file.getId();

        // 判断下级是否为空，为空则终止删除
        LambdaQueryWrapper<FileRelationEntity> relationLQW = new LambdaQueryWrapper<FileRelationEntity>()
                .eq(FileRelationEntity::getAncestor, fileId);
        List<FileRelationEntity> relationFileList = this.fileRelationMapper.selectList(relationLQW);
        if (!relationFileList.isEmpty()) {
            return;
        }

        // 删除relation
        relationLQW = new LambdaQueryWrapper<FileRelationEntity>()
                .eq(FileRelationEntity::getDescendant, fileId);
        Integer relationDeleteNum = this.fileRelationMapper.delete(relationLQW);

        // 删除file
        fileLQW = new LambdaQueryWrapper<FileEntity>()
                .eq(FileEntity::getId, fileId);
        Integer fileDeleteNum = this.fileMapper.delete(fileLQW);

        // 删除file version
        LambdaQueryWrapper<FileVersionEntity> versionLQW = new LambdaQueryWrapper<FileVersionEntity>()
                .eq(FileVersionEntity::getFileId, fileId);
        Integer fileVersionDeleteNum = this.fileVersionMapper.delete(versionLQW);

        this.repositoryInterceptor.deleteFile(path);
    }

    @Override
    public void deleteProject(String projectName, User user) throws Exception {
        // 获取所有file
        LambdaQueryWrapper<ProjectEntity> projectLQW = new LambdaQueryWrapper<ProjectEntity>()
                .select(ProjectEntity::getId)
                .eq(ProjectEntity::getName, projectName)
                .last("limit 1");
        ProjectEntity project = this.projectMapper.selectOne(projectLQW);
        if (project == null) {
            return;
        }
        long projectId = project.getId();

        // 删除project
        Integer projectDeleteNum = this.projectMapper.delete(projectLQW);

        // 删除relation
        LambdaQueryWrapper<FileRelationEntity> relationLQW = new LambdaQueryWrapper<FileRelationEntity>()
                .in(FileRelationEntity::getProjectId, projectId);
        Integer relationDeleteNum = this.fileRelationMapper.delete(relationLQW);

        // 删除file
        LambdaQueryWrapper<FileEntity> fileLQW = new LambdaQueryWrapper<FileEntity>()
                .eq(FileEntity::getId, projectId)
                .or()
                .eq(FileEntity::getProjectId, projectId);
        Integer fileDeleteNum = this.fileMapper.delete(fileLQW);

        // 删除file version
        LambdaQueryWrapper<FileVersionEntity> versionLQW = new LambdaQueryWrapper<FileVersionEntity>()
                .eq(FileVersionEntity::getProjectId, projectId);
        Integer fileVersionDeleteNum = this.fileVersionMapper.delete(versionLQW);

        // 删除project version
        LambdaQueryWrapper<ProjectVersionEntity> projectVersionEntityLambdaQueryWrapper = new LambdaQueryWrapper<ProjectVersionEntity>()
                .eq(ProjectVersionEntity::getProjectId, projectId);
        Integer projectVersionDeleteNum = this.projectVersionMapper.delete(projectVersionEntityLambdaQueryWrapper);

        // 删除project version mapping
        LambdaQueryWrapper<ProjectVersionMappingEntity> projectVersionMappingEntityLambdaQueryWrapper = new LambdaQueryWrapper<ProjectVersionMappingEntity>()
                .eq(ProjectVersionMappingEntity::getProjectId, projectId);
        Integer projectVersionMappingDeleteNum = this.projectVersionMappingMapper.delete(projectVersionMappingEntityLambdaQueryWrapper);

        this.repositoryInterceptor.deleteFile("/" + projectName);
    }

    @Override
    public Long lockPath(String project, User user) throws Exception {
        LambdaQueryWrapper<LockEntity> queryWrapper = new LambdaQueryWrapper<LockEntity>()
                .eq(LockEntity::getLockResource, project);

        LockEntity lockEntity = this.lockMapper.selectOne(queryWrapper);
        if (lockEntity != null) {
            return null;
        }

        lockEntity = new LockEntity();
        lockEntity.setLockResource(project);
        lockEntity.setCreateTime(new Date());
        return this.lockMapper.insert(lockEntity) > 0 ? lockEntity.getId() : null;
    }

    @Override
    public boolean unlockPath(String project, User user, Long versionNum) throws Exception {
        return this.lockMapper.deleteById(versionNum) > 0;
    }

    @Override
    public Repository loadRepository(String project, User user, boolean classify, FileType[] types, String searchFileName) throws Exception {
        return loadRepository(project, user, classify, types, searchFileName, true);
    }

    @Override
    public Repository loadRepository(String project, User user, boolean classify, FileType[] types, String searchFileName, Boolean detailed) throws Exception {
        if (project != null && project.startsWith("/")) {
            project = project.substring(1);
        }
        Repository repo = new Repository();

        // 遍历项目
        LambdaQueryWrapper<ProjectEntity> fileQW = new LambdaQueryWrapper<>();
        if (!StringUtils.isEmpty(project)) {
            fileQW.eq(ProjectEntity::getName, project);
        }
        List<ProjectEntity> projectEntityList = this.projectMapper.selectList(fileQW);
        if (projectEntityList != null) {
            List<String> projectNames = new ArrayList<>(projectEntityList.size());
            RepositoryFile rootFile = new RepositoryFile();
            rootFile.setFullPath("/");
            rootFile.setName("项目列表");
            rootFile.setType(Type.root);
            for (ProjectEntity file : projectEntityList) {
                try {
                    projectNames.add(file.getName());
                    RepositoryFile projectFile;
                    if (detailed) {
                        projectFile = buildProjectFile(file, types, classify, searchFileName);
                    } else {
                        projectFile = new RepositoryFile();
                        projectFile.setType(Type.project);
                        projectFile.setName(file.getName());
                        projectFile.setFullPath("/" + file.getName());
                    }
                    rootFile.addChild(projectFile, false);
                } catch (Exception e) {
                    log.error("loadRepository projectEntityList.forEach", e);
                }
            }
            repo.setRootFile(rootFile);

            // 添加公共资源
            RepositoryFile publicResourceFile = new RepositoryFile();
            publicResourceFile.setFullPath("/__public__");
            publicResourceFile.setName("公共资源");
            publicResourceFile.setType(Type.folder);
            RepositoryFile subLib = new RepositoryFile();
            subLib.setFullPath("/__public__");
            subLib.setName("库");
            subLib.setLibType(LibType.res);
            subLib.setType(Type.lib);
            FileType[] librarySubTypes = types;
            if (types == null || types.length == 0) {
                librarySubTypes = new FileType[]{FileType.VariableLibrary, FileType.ParameterLibrary, FileType.ConstantLibrary, FileType.ActionLibrary};
            }
            FileEntity fileEntity = new FileEntity();
            fileEntity.setId(0L);
            buildNodes(fileEntity, subLib, librarySubTypes, Type.lib, null);
            publicResourceFile.setChildren(subLib.getChildren());
            // 插入默认公共资源
            repo.setPublicResource(publicResourceFile);
            repo.setProjectNames(projectNames);
        }

        return repo;
    }

    @Override
    public void fileRename(String path, String newPath) throws Exception {
    }

    @Override
    public List<String> getReferenceFiles(String targetProject, String path, String searchText, String searchTextScript) throws Exception {
        List<String> referenceFiles = new ArrayList<>();

        LambdaQueryWrapper<ProjectEntity> projectEntityLambdaQueryWrapper = new LambdaQueryWrapper<ProjectEntity>()
                .select(ProjectEntity::getId)
                .gt(ProjectEntity::getId, 0);
        if (org.springframework.util.StringUtils.hasText(targetProject)) {
            projectEntityLambdaQueryWrapper.eq(ProjectEntity::getName, targetProject);
        }
        List<ProjectEntity> projectEntityList = this.projectMapper.selectList(projectEntityLambdaQueryWrapper);

        for (ProjectEntity projectEntity : projectEntityList) {
            LambdaQueryWrapper<FileEntity> fileEntityLambdaQueryWrapper = new LambdaQueryWrapper<FileEntity>()
                    .ne(FileEntity::getFileType, Type.resourcePackage.ordinal())
                    .ne(FileEntity::getFileType, Type.packageConfig.ordinal())
                    .eq(FileEntity::getProjectId, projectEntity.getId());
            List<FileEntity> fileEntityList = this.fileMapper.selectList(fileEntityLambdaQueryWrapper);
            List<Long> fileIdList = new ArrayList<>();
            for (FileEntity fileEntity : fileEntityList) {
                fileIdList.add(fileEntity.getId());
            }

            List<FileVersionEntity> fileVersionEntityList = this.fileVersionMapper.selectLatestVersionByFileIds(fileIdList);
            for (FileVersionEntity fileVersionEntity : fileVersionEntityList) {
                String content = fileVersionEntity.getFileContent();
                boolean containPath = content.contains(path);
                boolean containText = content.contains(searchText);
                boolean containScriptText = content.contains(searchTextScript);
                if ((containPath && containText)
                        || (containPath && containScriptText)) {
                    referenceFiles.add(fileVersionEntity.getFilePath());
                }
            }
        }

        return referenceFiles;
    }

    @Override
    public InputStream readFile(String path, String version) throws Exception {
        return readFile(path, version, null, true);
    }

    @Override
    public InputStream readFile(String path, String version, String projectVersion) throws Exception {
        return readFile(path, version, projectVersion, true);
    }

    @Override
    public InputStream readFile(String path, String version, String projectVersion, boolean containSnapshot) throws Exception {
        path = Utils.decodeURL(path);
        String[] pathArray = path.split(":");
        if (pathArray.length > 1) {
            path = pathArray[0];
            version = pathArray[1];
        }
        LambdaQueryWrapper<FileVersionEntity> fileVersionLQW = new LambdaQueryWrapper<FileVersionEntity>()
                .select(FileVersionEntity::getFileContent, FileVersionEntity::getVersionNum)
                .eq(FileVersionEntity::getFilePath, path)
                .last("limit 1");
        if (!containSnapshot) {
            fileVersionLQW.lt(FileVersionEntity::getVersionNumReal, SNAPSHOT_VERSION_REAL);
            if (org.springframework.util.StringUtils.hasText(projectVersion)) {
                fileVersionLQW.le(FileVersionEntity::getProjectVersionNumReal, VersionUtils.convertVersionToLong(projectVersion));
            }
        }
        if (org.springframework.util.StringUtils.hasText(version) && !version.equalsIgnoreCase("latest")) {
            fileVersionLQW.eq(FileVersionEntity::getVersionNum, version);
        } else {
            fileVersionLQW.orderByDesc(FileVersionEntity::getVersionNumReal);
        }

        FileVersionEntity fileVersionEntity = this.fileVersionMapper.selectOne(fileVersionLQW);
        if (fileVersionEntity != null) {
            log.info(String.format("readFile path: %s, project version: %s, input version: %s, real version: %s containSnapshot：%s", path, projectVersion, version, fileVersionEntity.getVersionNum(), containSnapshot));
            return IOUtils.toInputStream(fileVersionEntity.getFileContent(), StandardCharsets.UTF_8);
        } else {
            log.info(String.format("readFile none path: %s, input version: %s, containSnapshot：%s", path, version, containSnapshot));
            return null;
        }
    }

    @Override
    public VersionFile loadFileProperty(String path, String version) throws Exception {
        path = processPath(path);
        LambdaQueryWrapper<FileVersionEntity> fileVersionLQW = new LambdaQueryWrapper<FileVersionEntity>()
                .eq(FileVersionEntity::getFilePath, path)
                .eq(FileVersionEntity::getVersionNum, version)
                .last("limit 1");
        FileVersionEntity fileVersionEntity = this.fileVersionMapper.selectOne(fileVersionLQW);

        VersionFile versionFile = new VersionFile();
        versionFile.setName(fileVersionEntity.getFileName());
        versionFile.setPath(path);
        versionFile.setComment(fileVersionEntity.getFileComment());
        versionFile.setBeforeComment(fileVersionEntity.getBeforeComment());
        versionFile.setAfterComment(fileVersionEntity.getAfterComment());
        return versionFile;
    }

    @Override
    public List<VersionFile> getVersionFiles(String path) throws Exception {
        return getVersionFiles(path, false, 0, 0, false, false);
    }

    @Override
    public List<VersionFile> getVersionFiles(String path, boolean desc, int page, int row, boolean containContent, boolean containLatest) throws Exception {
        path = processPath(path);
        FileEntity fileEntity = this.fileMapper.selectOne(new LambdaQueryWrapper<FileEntity>()
                .eq(FileEntity::getFilePath, path)
                .last("limit 1"));
        if (fileEntity == null) {
            throw new RuleException("File [" + path + "] not exist.");
        }

        List<VersionFile> files = new ArrayList<>();
        LambdaQueryWrapper<FileVersionEntity> versionLQW = new LambdaQueryWrapper<FileVersionEntity>()
                .eq(FileVersionEntity::getFilePath, path);
        if (!containLatest) {
            versionLQW.ne(FileVersionEntity::getVersionNum, "latest");
        }
        if (desc) {
            versionLQW.orderByDesc(FileVersionEntity::getCreateDate);
        } else {
            versionLQW.orderByAsc(FileVersionEntity::getCreateDate);
        }
        if (row > 0 && page > 0) {
            versionLQW.last("limit " + (page - 1) * row + "," + row);
        }
        List<FileVersionEntity> versionEntityList = this.fileVersionMapper.selectList(versionLQW);

        versionEntityList.forEach(version -> {
            String versionName = version.getVersionNum();
            if (versionName == null || versionName.isEmpty()) {
                return;
            }

            VersionFile file = new VersionFile();
            file.setName(versionName);
            file.setVersionNumReal(version.getVersionNumReal());
            file.setProjectVersionNumReal(version.getProjectVersionNumReal());
            file.setPath(version.getFilePath());
            file.setCreateUser(version.getCreateUser());
            file.setCreateDate(version.getCreateDate());
            file.setComment(version.getFileComment());
            file.setBeforeComment(version.getBeforeComment());
            file.setAfterComment(version.getAfterComment());
            file.setProjectId(fileEntity.getProjectId());
            if (containContent) {
                file.setContent(version.getFileContent());
            }

            files.add(file);
        });

        return files;
    }

    @Override
    public Long countVersionFiles(String path) throws Exception {
        path = processPath(path);
        LambdaQueryWrapper<FileEntity> fileLQW = new LambdaQueryWrapper<FileEntity>()
                .eq(FileEntity::getFilePath, path)
                .last("limit 1");
        FileEntity fileEntity = this.fileMapper.selectOne(fileLQW);
        if (fileEntity == null) {
            throw new RuleException("File [" + path + "] not exist.");
        }

        LambdaQueryWrapper<FileVersionEntity> versionLQW = new LambdaQueryWrapper<FileVersionEntity>()
                .eq(FileVersionEntity::getFilePath, path)
                .ne(FileVersionEntity::getVersionNum, "latest");
        return this.fileVersionMapper.selectCount(versionLQW);
    }

    @Override
    public Long importFromZip(User user, MultipartFile importFile, RepositoryFile repositoryFile, Map<String, ExportProject> exportProjectMap, Boolean loadLatest) throws Exception {
        // 插入项目
        ProjectEntity project = new ProjectEntity();
        project.setName(repositoryFile.getName());
        project.setCreateTime(new Date());
        this.projectMapper.insert(project);

        Map<String, Long> fileIdMap = importFile(repositoryFile.getChildren(), Lists.newArrayList(project.getId()), loadLatest, false);

        // 处理版本
        for (String file : exportProjectMap.keySet()) {
            ExportProject exportProject = exportProjectMap.get(file);
            int size = exportProject.getVersionFileMap().size();
            // 获取最近内容
            String filePath = null;
            for (int i = 0; i < size; i++) {
                VersionFile versionFile = exportProject.getVersionFileMap().get(String.valueOf(i));
                filePath = versionFile.getPath();
                break;
            }

            Long fileId = fileIdMap.get(filePath);
            List<VersionFile> versionFileList = new ArrayList<>(exportProject.getVersionFileMap().values());
            saveVersionFileList(file, filePath, project.getId(), versionFileList, fileId);
            log.debug("version file path: {}", filePath);
            Thread.sleep(10);
        }

        return project.getId();
    }

    private void createFileNode(String path, String content, User user, boolean isFile) throws Exception {
        path = processPath(path);
        try {
            if (fileExistCheck(path)) {
                throw new RuleException("File [" + path + "] already exist.");
            }

            String parentPath = path.substring(0, path.lastIndexOf("/"));
            LambdaQueryWrapper<FileEntity> parentFileLQW = new LambdaQueryWrapper<FileEntity>()
                    .eq(FileEntity::getFilePath, parentPath)
                    .ne(FileEntity::getFileType, Type.project.ordinal())
                    .last("limit 1");
            FileEntity parentFile = this.fileMapper.selectOne(parentFileLQW);

            String fileName = path.substring(path.lastIndexOf("/") + 1);
            String createUser = user.getUsername();
            long projectId = parentFile.getProjectId();

            FileEntity file = new FileEntity();
            file.setName(fileName);
            Type type = FileTypeUtils.mapFileNameToType(fileName);
            if (type != null) {
                file.setFileType(type.ordinal());
            } else if (!isFile) {
                file.setFileType(Type.folder.ordinal());
            } else {
                file.setFileType(-127);
            }
            file.setProjectId(projectId);
            file.setFilePath(path);
            file.setCreateTime(new Date());
            this.fileMapper.insert(file);

            if (isFile) {
                FileVersionEntity fileVersionEntity = new FileVersionEntity();
                fileVersionEntity.setFileId(file.getId());
                fileVersionEntity.setFilePath(path);
                fileVersionEntity.setFileName(fileName);
                fileVersionEntity.setFileContent(content);
                fileVersionEntity.setVersionNum("latest");
                fileVersionEntity.setVersionNumReal(1L);
                fileVersionEntity.setProjectId(projectId);
                fileVersionEntity.setCreateUser(createUser);
                fileVersionEntity.setCreateDate(new Date());
                this.fileVersionMapper.insert(fileVersionEntity);
            }

            LambdaQueryWrapper<FileRelationEntity> relationLQW = new LambdaQueryWrapper<FileRelationEntity>()
                    .eq(FileRelationEntity::getDescendant, parentFile.getId());
            List<FileRelationEntity> parentRelationList = this.fileRelationMapper.selectList(relationLQW);
            List<FileRelationEntity> fileRelationList = new ArrayList<>(parentRelationList.size() + 1);
            parentRelationList.forEach(parentRelation -> {
                FileRelationEntity fileRelation = new FileRelationEntity();
                fileRelation.setAncestor(parentRelation.getAncestor());
                fileRelation.setDescendant(file.getId());
                fileRelation.setDistance(parentRelation.getDistance() + 1);
                fileRelation.setProjectId(projectId);
                fileRelationList.add(fileRelation);
            });
            FileRelationEntity fileRelation = new FileRelationEntity();
            fileRelation.setAncestor(parentFile.getId());
            fileRelation.setDescendant(file.getId());
            fileRelation.setDistance(1);
            fileRelation.setProjectId(projectId);
            fileRelationList.add(fileRelation);
            this.fileRelationMapper.insertBatchSomeColumn(fileRelationList);

            this.repositoryInterceptor.createFile(path, content);
        } catch (Exception ex) {
            throw new RuleException(ex);
        }
    }


    private void lockCheck(FileEntity file, User user) throws Exception {
//        if (lockManager.isLocked(node.getPath())) {
//            String lockOwner = lockManager.getLock(node.getPath()).getLockOwner();
//            if (lockOwner.equals(user.getUsername())) {
//                return;
//            }
//    }
//        throw new NodeLockException("【" + file.getName() + "】已被" + "lockOwner" + "锁定!");
    }

    private Map<String, Long> importFile(List<RepositoryFile> repositoryFileList, List<Long> parentIdList, Boolean loadLatest, Boolean loadRemoteVersionFile) {
        if (repositoryFileList == null || repositoryFileList.isEmpty()) {
            return null;
        }

        Map<String, Long> fileIdMap = new HashMap<>();
        repositoryFileList.forEach(repositoryFile -> {
            List<Long> parentIdListCopy = Lists.newCopyOnWriteArrayList(parentIdList);

            FileEntity file = new FileEntity();
            file.setName(repositoryFile.getName());
            file.setFilePath(repositoryFile.getFullPath());
            file.setFileType(repositoryFile.getType().ordinal());
            file.setProjectId(parentIdListCopy.get(0));
            this.fileMapper.insert(file);

            // 记录文件ID
            fileIdMap.put(file.getFilePath(), file.getId());

            if (!Arrays.asList(Type.folder, Type.all).contains(repositoryFile.getType())
                    && loadLatest) {
                if (loadRemoteVersionFile) {
                    // todo
//                    syncVersionFileListFromRemote(repositoryFile, parentIdListCopy.get(0));
                } else {
                    syncVersionFileLatestFromLocal(repositoryFile, parentIdListCopy.get(0));
                }
            }

            // 插入关系表
            List<FileRelationEntity> relationList = new ArrayList<>(parentIdList.size());
            for (int i = 0; i < parentIdListCopy.size(); i++) {
                FileRelationEntity relation = new FileRelationEntity();
                relation.setProjectId(parentIdList.get(0));
                relation.setAncestor(parentIdListCopy.get(i));
                relation.setDescendant(file.getId());
                relation.setDistance(parentIdListCopy.size() - i);
                relationList.add(relation);
            }
            this.fileRelationMapper.insertBatchSomeColumn(relationList);

            parentIdListCopy.add(file.getId());
            Map<String, Long> fileIdMapChild = importFile(repositoryFile.getChildren(), parentIdListCopy, loadLatest, loadRemoteVersionFile);
            if (fileIdMapChild != null) {
                fileIdMap.putAll(fileIdMapChild);
            }
        });

        return fileIdMap;
    }

    protected String processPath(String path) {
        if (!path.startsWith("/")) {
            return "/" + path;
        }
        return path;
    }

    private void syncVersionFileLatestFromLocal(RepositoryFile repositoryFile, long projectId) {
        try {
            String fileContent = IOUtils.toString(this.readFile(repositoryFile.getFullPath(), null));
            VersionFile latestFile = new VersionFile();
            latestFile.setPath(repositoryFile.getFullPath());
            latestFile.setName("latest");
            latestFile.setContent(fileContent);
            // todo
            saveVersionFileList(repositoryFile.getName(), repositoryFile.getFullPath(), projectId, Lists.newArrayList(latestFile), null);
        } catch (Exception e) {
            log.error("syncVersionFileLatestFromLocal {}", repositoryFile.getFullPath(), e);
        }
    }

    private void saveVersionFileList(String fileName, String filePath, Long projectId, List<VersionFile> versionFileList, Long fileId) {
        FileVersionEntity fileVersionEntity;
        List<FileVersionEntity> fileVersionEntityList = null;

        if (versionFileList != null && !versionFileList.isEmpty()) {
            log.info("saveVersionFileList versionFileList {} {}", filePath, versionFileList.size());
            fileVersionEntityList = new ArrayList<>(versionFileList.size() + 1);

            for (VersionFile versionFile : versionFileList) {
                fileVersionEntity = new FileVersionEntity();
                fileVersionEntity.setFileId(fileId);
                fileVersionEntity.setFileName(fileName);
                fileVersionEntity.setFilePath(versionFile.getPath());
                fileVersionEntity.setFileComment(versionFile.getComment());
                fileVersionEntity.setVersionNum(versionFile.getName());
                if (versionFile.getVersionNumReal() != null && versionFile.getVersionNumReal() > 0) {
                    fileVersionEntity.setVersionNumReal(versionFile.getVersionNumReal());
                } else {
                    fileVersionEntity.setVersionNumReal(VersionUtils.convertVersionToLong(versionFile.getName()));
                }
                fileVersionEntity.setProjectVersionNumReal(versionFile.getProjectVersionNumReal());
                fileVersionEntity.setProjectId(projectId);
                fileVersionEntity.setAfterComment(versionFile.getAfterComment());
                fileVersionEntity.setBeforeComment(versionFile.getBeforeComment());
                fileVersionEntity.setCreateUser(versionFile.getCreateUser());
                fileVersionEntity.setCreateDate(versionFile.getCreateDate());
                fileVersionEntity.setFileContent(versionFile.getContent());

                fileVersionEntityList.add(fileVersionEntity);
            }
        }

        int batchSize = 50;
        if (fileVersionEntityList.size() > batchSize) {
            Lists.partition(fileVersionEntityList, batchSize).forEach(this.fileVersionMapper::insertBatchSomeColumn);
        } else {
            this.fileVersionMapper.insertBatchSomeColumn(fileVersionEntityList);
        }
    }

    @Override
    public PackageConfig loadPackageConfigs(String project) throws Exception {
        String filePath = processPath(project) + "/" + PACKAGE_CONFIG_FILE;

        InputStream inputStream = readFile(filePath);
        String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        inputStream.close();

        Document document = DocumentHelper.parseText(content);
        Element rootElement = document.getRootElement();

        PackageConfig packageConfig = new PackageConfig();
        packageConfig.setVersion(rootElement.attributeValue("version"));
        packageConfig.setLock(Boolean.parseBoolean(rootElement.attributeValue("lock")));
//        Map<String, Integer> auditStatusMap = (Map<String, Integer>) JSON.parse(rootElement.attributeValue("audit"));
//        if (auditStatusMap == null) {
//            auditStatusMap = new HashMap<>();
//        }
//        packageConfig.setAuditStatusMap(auditStatusMap);

        // todo diff
        Map<String, String> versionDiffMap = (Map<String, String>) JSON.parse(rootElement.attributeValue("diff"));
        if (versionDiffMap == null) {
            versionDiffMap = new HashMap<>();
        }
        packageConfig.setVersionDiffMap(versionDiffMap);

        // todo runtime config

        return packageConfig;
    }

    @Override
    public void updatePackageConfigs(String project, PackageConfig packageConfig) throws Exception {
        String filePath = processPath(project) + "/" + PACKAGE_CONFIG_FILE;

        InputStream inputStream = readFile(filePath);
        String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        inputStream.close();

        Document document = DocumentHelper.parseText(content);
        Element rootElement = document.getRootElement();
        rootElement.setAttributeValue("version", packageConfig.getVersion());
        rootElement.setAttributeValue("lock", packageConfig.getLock().toString());

        // todo diff
        rootElement.setAttributeValue("diff", JSON.toJSON(packageConfig.getVersionDiffMap()).toString());

        // todo runtime config

        DefaultUser defaultUser = new DefaultUser();
        defaultUser.setUsername("system");
        defaultUser.setAdmin(true);
        LambdaUpdateWrapper<FileVersionEntity> fileLUW = new LambdaUpdateWrapper<FileVersionEntity>()
                .eq(FileVersionEntity::getFilePath, filePath)
                .eq(FileVersionEntity::getVersionNum, "latest")
                .set(FileVersionEntity::getFileContent, document.asXML())
                .set(FileVersionEntity::getUpdateTime, new Date());
        int updateRes = this.fileVersionMapper.update(null, fileLUW);
    }

    @Override
    public boolean fileExist(String var1) throws Exception {
        return false;
    }

    @Override
    public String getProject(String path) {
        if (path == null) {
            return null;
        }
        String processedPath = path;
        if (processedPath.startsWith("/")) {
            processedPath = processedPath.substring(1);
        }
        int slashPos = processedPath.indexOf("/");
        if (slashPos == -1) {
            return processedPath;
        }
        return processedPath.substring(0, slashPos);
    }

    @Override
    public List<RepositoryFile> loadTemplates(String project) throws Exception {
        return new ArrayList<>();
    }

    @Override
    public String saveTemplateFile(String path, String content) throws Exception {
        // Template save not implemented in DB-backed storage
        return null;
    }

    private RepositoryFile buildProjectFile(ProjectEntity projectNode, FileType[] types, boolean classify, String searchFileName) throws Exception {
        RepositoryFile projectFile = new RepositoryFile();
        projectFile.setType(Type.project);
        projectFile.setName(projectNode.getName());
        projectFile.setFullPath("/" + projectNode.getName());

        LambdaQueryWrapper<FileRelationEntity> fileLQW = new LambdaQueryWrapper<>();
        fileLQW.eq(FileRelationEntity::getAncestor, projectNode.getId())
                .eq(FileRelationEntity::getDistance, 1);
        List<FileEntity> fileEntityList = this.fileMapper.selectListByAncestor(fileLQW);
        log.info("{}: fileEntityList result:{}", projectNode.getName(), JSON.toJSONString(fileEntityList));
        if (CollectionUtils.isEmpty(fileEntityList)) {
            log.info("{}: fileEntityList file is null", projectNode.getName());
            return projectFile;
        }

        for (FileEntity file : fileEntityList) {
            if (Objects.isNull(file)) {
                log.info("{}: buildProjectFile file is null", projectNode.getName());
                continue;
            }
            Type type = Type.values()[file.getFileType()];
            switch (type) {
                case all:
                    RepositoryFile resDir = new RepositoryFile();
                    resDir.setFullPath(projectFile.getFullPath());
                    resDir.setName(file.getName());

                    try {
                        if (classify) {
                            resDir.setType(Type.resource);
                            createResourceCategory(file, resDir, types, searchFileName);
                        } else {
                            resDir.setType(Type.all);
                            buildResources(file, resDir, types, searchFileName);
                        }
                    } catch (Exception e) {
                        log.error("buildProjectFile error", e);
                    }

                    projectFile.addChild(resDir, false);
                    break;
                case resourcePackage:
                    if ((types == null || types.length == 0) && this.permissionService.projectPackageHasReadPermission("projectNode.getFilePath()")) {
                        RepositoryFile packageFile = new RepositoryFile();
                        packageFile.setName(file.getName());
                        packageFile.setType(Type.resourcePackage);
                        packageFile.setFullPath(file.getFilePath());
                        projectFile.addChild(packageFile, false);
                    }
                    break;
                default:
            }
        }

        return projectFile;
    }

    private void createResourceCategory(FileEntity projectNode, RepositoryFile libDir, FileType[] types, String searchFileName) throws Exception {
        // 遍历库文件
        RepositoryFile subLib = buildLibFile(libDir, "库", LibType.res);
        subLib.setType(Type.lib);
        libDir.addChild(subLib, false);
        FileType[] librarySubTypes = types;
        if (types == null || types.length == 0) {
            librarySubTypes = new FileType[]{FileType.VariableLibrary, FileType.ParameterLibrary, FileType.ConstantLibrary, FileType.ActionLibrary};
        }
        buildNodes(projectNode, subLib, librarySubTypes, Type.lib, searchFileName);

        // 遍历决策集
        RepositoryFile rulesLib = buildLibFile(libDir, "决策集", LibType.ruleset);
        rulesLib.setFullPath(libDir.getFullPath());
        rulesLib.setType(Type.ruleLib);

        RepositoryFile decisionTableLib = buildLibFile(libDir, "决策表", LibType.decisiontable);
        decisionTableLib.setFullPath(libDir.getFullPath());
        decisionTableLib.setType(Type.decisionTableLib);

        RepositoryFile decisionTreeLib = buildLibFile(libDir, "决策树", LibType.decisiontree);
        decisionTreeLib.setFullPath(libDir.getFullPath());
        decisionTreeLib.setType(Type.decisionTreeLib);

        RepositoryFile scorecardLib = buildLibFile(libDir, "评分卡", LibType.scorecard);
        scorecardLib.setFullPath(libDir.getFullPath());
        scorecardLib.setType(Type.scorecardLib);

        RepositoryFile flowLib = buildLibFile(libDir, "决策流", LibType.ruleflow);
        flowLib.setFullPath(libDir.getFullPath());
        flowLib.setType(Type.flowLib);

        libDir.addChild(rulesLib, false);
        libDir.addChild(decisionTableLib, false);
        libDir.addChild(decisionTreeLib, false);
        libDir.addChild(scorecardLib, false);
        libDir.addChild(flowLib, false);

        FileType[] libraryRuleTypes = types;
        if (types == null || types.length == 0) {
            libraryRuleTypes = new FileType[]{FileType.Ruleset, FileType.RulesetLib, FileType.UL};
        }

        FileType[] libraryDecisionTypes = types;
        if (types == null || types.length == 0) {
            libraryDecisionTypes = new FileType[]{FileType.DecisionTable, FileType.ScriptDecisionTable, FileType.Crosstab};
        }
        FileType[] libraryDecisionTreeTypes = types;
        if (types == null || types.length == 0) {
            libraryDecisionTreeTypes = new FileType[]{FileType.DecisionTree};
        }

        FileType[] libraryFlowTypes = types;
        if (types == null || types.length == 0) {
            libraryFlowTypes = new FileType[]{FileType.RuleFlow};
        }

        FileType[] libraryScorecardTypes = types;
        if (types == null || types.length == 0) {
            libraryScorecardTypes = new FileType[]{FileType.Scorecard, FileType.ComplexScorecard};
        }

        buildNodes(projectNode, rulesLib, libraryRuleTypes, Type.ruleLib, searchFileName);
        buildNodes(projectNode, decisionTableLib, libraryDecisionTypes, Type.decisionTableLib, searchFileName);
        buildNodes(projectNode, decisionTreeLib, libraryDecisionTreeTypes, Type.decisionTreeLib, searchFileName);
        buildNodes(projectNode, scorecardLib, libraryScorecardTypes, Type.scorecardLib, searchFileName);
        buildNodes(projectNode, flowLib, libraryFlowTypes, Type.flowLib, searchFileName);
    }

    private RepositoryFile buildLibFile(RepositoryFile libraryDir, String name, LibType libType) {
        RepositoryFile subLib = new RepositoryFile();
        subLib.setFullPath(libraryDir.getFullPath());
        subLib.setName(name);
        subLib.setLibType(libType);
        return subLib;
    }

    private void buildNodes(FileEntity parentFile, RepositoryFile parent, FileType[] types, Type folderType, String searchFileName) throws Exception {
        LibType libType = parent.getLibType();

        LambdaQueryWrapper<FileRelationEntity> fileLQW = new LambdaQueryWrapper<>();
        fileLQW
                .eq(FileRelationEntity::getAncestor, parentFile.getId())
                .eq(FileRelationEntity::getDistance, 1);
        List<FileEntity> fileEntityList = this.fileMapper.selectListByAncestor(fileLQW);
        fileEntityList.forEach(fileNode -> {
            if (fileNode.getFileType() < 0) {
                return;
            }

            Type type = Type.values()[fileNode.getFileType()];
            String name = fileNode.getName();

            // TODO: 2023/6/30
//            if (!fileNode.hasProperty(FILE)) {
//                return;
//            }
            RepositoryFile file = new RepositoryFile();
            file.setLibType(libType);
            // TODO: 2023/6/30
//            if (name.toLowerCase().contains(RES_PACKGE_FILE)
//                    || name.toLowerCase().contains(PACKAGE_CONFIG_FILE)
//                    || name.toLowerCase().contains(CLIENT_CONFIG_FILE)
//                    || name.toLowerCase().contains(RESOURCE_SECURITY_CONFIG_FILE)) {
//                return;
//            }

            if (type != Type.folder) {
                if (!this.permissionService.fileHasReadPermission(fileNode.getFilePath())) {
                    return;
                }
                FileType fileType = null;
                boolean add = false;
                for (FileType typeItem : types) {
                    if (name.toLowerCase().endsWith(typeItem.toString())) {
                        fileType = typeItem;
                        add = true;
                        break;
                    }
                }
                if (!add) {
                    return;
                }

                if (libType.equals(LibType.res)) {
                    if (!fileType.equals(FileType.ActionLibrary) && !fileType.equals(FileType.ParameterLibrary) && !fileType.equals(FileType.ConstantLibrary) && !fileType.equals(FileType.VariableLibrary)) {
                        return;
                    }
                }

                if (libType.equals(LibType.decisiontable)) {
                    if (!fileType.equals(FileType.ScriptDecisionTable) && !fileType.equals(FileType.DecisionTable) && !fileType.equals(FileType.Crosstab)) {
                        return;
                    }
                }

                if (libType.equals(LibType.decisiontree)) {
                    if (!fileType.equals(FileType.DecisionTree)) {
                        return;
                    }
                }

                if (libType.equals(LibType.ruleflow)) {
                    if (!fileType.equals(FileType.RuleFlow)) {
                        return;
                    }
                }

                if (libType.equals(LibType.scorecard)) {
                    if (!fileType.equals(FileType.Scorecard) && !fileType.equals(FileType.ComplexScorecard)) {
                        return;
                    }
                }

                if (libType.equals(LibType.ruleset)) {
                    if (!fileType.equals(FileType.Ruleset) && !fileType.equals(FileType.UL) && !fileType.equals(FileType.RulesetLib)) {
                        return;
                    }
                }

                if (StringUtils.isNotBlank(searchFileName)) {
                    boolean fileNameContain = name.toLowerCase().contains(searchFileName.toLowerCase());
                    if (name.toLowerCase().endsWith(FileType.Ruleset.toString())) {
                        // 搜索文件本身
                        try {
                            InputStream inputStream = null;
                            inputStream = readFile(fileNode.getFilePath());

                            byte[] bytes;
                            bytes = new byte[inputStream.available()];
                            inputStream.read(bytes);
                            String ruleContent = new String(bytes);

                            if (!ruleContent.toLowerCase().contains(searchFileName.toLowerCase()) && !fileNameContain) {
                                return;
                            }
                        } catch (Exception ex) {
                        }
                    } else {
                        // 搜索文件名
                        if (!fileNameContain) {
                            return;
                        }
                    }
                }

                Type mapType = FileTypeUtils.mapFileNameToType(name);
                if (mapType != null) {
                    file.setType(mapType);
                }
                file.setFullPath(fileNode.getFilePath());
                file.setName(name);
                try {
                    buildNodeLockInfo(fileNode, file);
                    parent.addChild(file, false);
                    buildNodes(fileNode, file, types, folderType, searchFileName);

                } catch (Exception e) {
                    log.error("buildNodes not folder error", e);
                }

            } else {
                file.setFullPath(fileNode.getFilePath());
                file.setName(name);
                file.setType(Type.folder);
                try {
                    buildNodeLockInfo(fileNode, file);
                    file.setFolderType(folderType);
                    parent.addChild(file, true);
                    buildNodes(fileNode, file, types, folderType, searchFileName);
                } catch (Exception e) {
                    log.error("buildNodes  folder error", e);
                }
            }

        });
    }

    private void buildNodeLockInfo(FileEntity node, RepositoryFile file) throws Exception {
    }

    private void buildResources(FileEntity projectNode, RepositoryFile libDir, FileType[] types, String searchFileName) throws Exception {
        FileType[] fileTypes = types;
        if (types == null || types.length == 0) {
            fileTypes = new FileType[]{FileType.VariableLibrary,
                    FileType.ParameterLibrary, FileType.ConstantLibrary,
                    FileType.ActionLibrary, FileType.Ruleset, FileType.RulesetLib,
                    FileType.RuleFlow, FileType.DecisionTable,
                    FileType.DecisionTree, FileType.ScriptDecisionTable,
                    FileType.UL, FileType.Scorecard, FileType.ComplexScorecard, FileType.Crosstab};
        }
        libDir.setLibType(LibType.all);
        buildNodes(projectNode, libDir, fileTypes, Type.all, searchFileName);
    }

    @Override
    public String createProjectVersion(String projectName, String packageId, String projectVersion, User user, String comment, Integer status) throws Exception {
        log.info("Attempting to create a new version for project [{}] with comment: {}", projectName, comment);
        try {
            // 将数据库插入操作委托给 ProjectStorageService
            String createdVersionName = projectStorageService.createProjectPackageVersion(projectName, packageId, projectVersion, user.getUsername(), comment, status);
            log.info("Successfully requested storage service to create project version [{}] for project [{}].", createdVersionName, projectName);
            return createdVersionName;
        } catch (Exception e) {
            log.error("Storage service failed to create project version for project [{}]", projectName, e);
            // 抛出异常以触发事务回滚
            throw new RuleException("Failed to create project version for " + projectName + " due to storage error.", e);
        }
    }

    @Override
    public String createProjectVersion(String projectName, User user, String comment) throws Exception {
        return createProjectVersion(projectName, null, null, user, comment, 0);
    }

    @Override
    public List<VersionFile> getProjectVersions(String projectName, boolean desc, int page, int row) throws Exception {
        ProjectEntity project = projectMapper.selectOne(new LambdaQueryWrapper<ProjectEntity>()
                .eq(ProjectEntity::getName, projectName));
        if (project == null) {
            throw new RuleException("Project [" + projectName + "] not found.");
        }
        Long projectId = project.getId();
        LambdaQueryWrapper<ProjectVersionEntity> queryWrapper = new LambdaQueryWrapper<ProjectVersionEntity>()
                .eq(ProjectVersionEntity::getProjectId, projectId);
        if (desc) {
            queryWrapper.orderByDesc(ProjectVersionEntity::getVersionNumReal);
        } else {
            queryWrapper.orderByAsc(ProjectVersionEntity::getVersionNumReal);
        }
        if (row > 0 && page > 0) {
            queryWrapper.last("limit " + (page - 1) * row + "," + row);
        }

        List<ProjectVersionEntity> projectVersionEntities = projectVersionMapper.selectList(queryWrapper);
        List<VersionFile> versionFiles = new ArrayList<>();
        if (!CollectionUtils.isEmpty(projectVersionEntities)) {
            for (ProjectVersionEntity entity : projectVersionEntities) {
                versionFiles.add(VersionFileUtils.getVersionFile(projectName, entity));
            }
        }
        return versionFiles;
    }

    @Override
    public List<VersionFile> getProjectPackageVersions(String projectName, String packageId) throws Exception {
        ProjectEntity project = projectMapper.selectOne(new LambdaQueryWrapper<ProjectEntity>()
                .eq(ProjectEntity::getName, projectName));
        if (project == null) {
            throw new RuleException("Project [" + projectName + "] not found.");
        }
        Long projectId = project.getId();

        List<ProjectVersionEntity> projectVersionEntities = projectVersionMapper.selectList(new LambdaQueryWrapper<ProjectVersionEntity>()
                .eq(ProjectVersionEntity::getProjectId, projectId)
                .eq(ProjectVersionEntity::getPackageId, packageId)
                .orderByDesc(ProjectVersionEntity::getVersionNumReal));
        List<VersionFile> versionFiles = new ArrayList<>();
        if (!CollectionUtils.isEmpty(projectVersionEntities)) {
            for (ProjectVersionEntity entity : projectVersionEntities) {
                versionFiles.add(VersionFileUtils.getVersionFile(projectName, entity));
            }
        }
        return versionFiles;
    }

}
