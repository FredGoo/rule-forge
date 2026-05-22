package com.ruleforge.console.storage.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ruleforge.console.repository.model.FileType;
import com.ruleforge.console.repository.model.ResourcePackage;
import com.ruleforge.exception.RuleException;
import com.ruleforge.console.entity.*;
import com.ruleforge.console.mapper.*;
import com.ruleforge.console.model.Repository;
import com.ruleforge.console.model.User;
import com.ruleforge.console.storage.ProjectStorageService;
import com.ruleforge.console.util.VersionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseProjectStorageServiceImpl implements ProjectStorageService {

    private final ProjectMapper projectMapper;
    private final FileVersionMapper fileVersionMapper;
    private final FileMapper fileMapper;
    private final ProjectVersionMapper projectVersionMapper;
    private final ProjectVersionMappingMapper projectVersionMappingMapper;
    public static final String SNAPSHOT_VERSION = "snapshot";
    public static final Long SNAPSHOT_VERSION_REAL = 1000_000_000L;

    @Override
    public String createProjectVersion(String projectName, String projectVersion, String createUser, String comment, Integer status) throws Exception {
        // 1. 查找项目
        ProjectEntity project = projectMapper.selectOne(new LambdaQueryWrapper<ProjectEntity>()
                .eq(ProjectEntity::getName, projectName));
        if (project == null) {
            log.error("Project [{}] not found.", projectName);
            throw new RuleException("Project [" + projectName + "] not found.");
        }
        Long projectId = project.getId();

        // 2. 确定新版本的版本号和名称
        if (StringUtils.isEmpty(projectVersion)) {
            ProjectVersionEntity latestExistingVersion = projectVersionMapper.selectOne(new LambdaQueryWrapper<ProjectVersionEntity>()
                    .eq(ProjectVersionEntity::getProjectId, projectId)
                    .orderByDesc(ProjectVersionEntity::getVersionNumReal)
                    .last("limit 1"));

            projectVersion = VersionUtils.incrementVersion(latestExistingVersion.getVersionName());
        }
        Long newVersionNumReal = VersionUtils.convertVersionToLong(projectVersion);
        log.info("Determined new version (Real: {}) for project [{}]", newVersionNumReal, projectName);
        String finalComment = StringUtils.isNotBlank(comment) ? comment : "Version created on " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        // 3. 创建 gr_project_version 记录 (使用传入的参数)
        ProjectVersionEntity projectVersionEntity = new ProjectVersionEntity();
        projectVersionEntity.setProjectId(projectId);
        projectVersionEntity.setVersionName(projectVersion);
        projectVersionEntity.setVersionNumReal(newVersionNumReal);
        projectVersionEntity.setAuditStatus(status);
        projectVersionEntity.setCreateUser(createUser);
        projectVersionEntity.setComment(finalComment);
        projectVersionEntity.setCreateTime(new Date());
        int insertCount = projectVersionMapper.insert(projectVersionEntity);
        // 检查插入是否成功以及 ID 是否已生成
        if (insertCount <= 0 || projectVersionEntity.getId() == null) {
            log.error("Failed to insert project version record for project ID [{}], version name [{}].", projectId, projectVersion);
            throw new RuleException("Failed to insert project version record for project ID [" + projectId + "]");
        }
        Long projectVersionId = projectVersionEntity.getId();
        log.info("Storage service: Successfully created project version record with ID [{}] for project ID [{}].", projectVersionId, projectId);

        String updateStr = updateSnapshotToRelease(projectId, newVersionNumReal);
        log.info("updateStr {}", updateStr);

        // 5. 返回成功创建的版本名称 (从参数中获取)
        return projectVersion;
    }

    @Override
    public String createProjectPackageVersion(String projectName, String packageId, String packageVersion, String createUser, String comment, Integer status) throws Exception {
        // 1. 查找项目
        ProjectEntity project = projectMapper.selectOne(new LambdaQueryWrapper<ProjectEntity>()
                .eq(ProjectEntity::getName, projectName));
        if (project == null) {
            log.error("Project [{}] not found.", projectName);
            throw new RuleException("Project [" + projectName + "] not found.");
        }
        Long projectId = project.getId();

        // 2. 确定新版本的版本号和名称
        if (StringUtils.isEmpty(packageVersion)) {
            ProjectVersionEntity latestExistingVersion = projectVersionMapper.selectOne(new LambdaQueryWrapper<ProjectVersionEntity>()
                    .eq(ProjectVersionEntity::getProjectId, projectId)
                    .orderByDesc(ProjectVersionEntity::getVersionNumReal)
                    .last("limit 1"));

            packageVersion = VersionUtils.incrementVersion(latestExistingVersion.getVersionName());
        }
        Long newVersionNumReal = VersionUtils.convertVersionToLong(packageVersion);
        log.info("Determined new version (Real: {}) for project [{}]", newVersionNumReal, projectName);
        String finalComment = StringUtils.isNotBlank(comment) ? comment : "Version created on " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        // 3. 创建 gr_project_version 记录 (使用传入的参数)
        ProjectVersionEntity projectVersionEntity = new ProjectVersionEntity();
        projectVersionEntity.setProjectId(projectId);
        projectVersionEntity.setPackageId(packageId);
        projectVersionEntity.setVersionName(packageVersion);
        projectVersionEntity.setVersionNumReal(newVersionNumReal);
        projectVersionEntity.setAuditStatus(status);
        projectVersionEntity.setCreateUser(createUser);
        projectVersionEntity.setComment(finalComment);
        projectVersionEntity.setCreateTime(new Date());
        int insertCount = projectVersionMapper.insert(projectVersionEntity);
        // 检查插入是否成功以及 ID 是否已生成
        if (insertCount <= 0 || projectVersionEntity.getId() == null) {
            log.error("Failed to insert project version record for project ID [{}], version name [{}].", projectId, packageVersion);
            throw new RuleException("Failed to insert project version record for project ID [" + projectId + "]");
        }
        Long projectVersionId = projectVersionEntity.getId();
        log.info("Storage service: Successfully created project version record with ID [{}] for project ID [{}].", projectVersionId, projectId);

        // 4. 插入项目所有latest版本
//        List<FileVersionEntity> fileVersionEntityList = this.fileVersionMapper.selectLatestFileByProjectId(projectId, SNAPSHOT_VERSION_REAL);
//        List<ProjectVersionMappingEntity> projectVersionMappingEntityList = new ArrayList<>(fileVersionEntityList.size());
//        for (FileVersionEntity fileVersionEntity : fileVersionEntityList) {
//            ProjectVersionMappingEntity projectVersionMappingEntity = new ProjectVersionMappingEntity();
//            projectVersionMappingEntity.setProjectId(projectId);
//            projectVersionMappingEntity.setProjectVersionId(newVersionNumReal);
//            projectVersionMappingEntity.setFileId(fileVersionEntity.getFileId());
//            projectVersionMappingEntity.setFileVersionId(fileVersionEntity.getId());
//            projectVersionMappingEntityList.add(projectVersionMappingEntity);
//        }
//        this.projectVersionMappingMapper.insertBatchSomeColumn(projectVersionMappingEntityList);
        fileVersionMapper.update(null, new LambdaUpdateWrapper<FileVersionEntity>()
                .eq(FileVersionEntity::getProjectId, projectId)
                .eq(FileVersionEntity::getProjectVersionNumReal, SNAPSHOT_VERSION_REAL)
                .set(FileVersionEntity::getProjectVersionNumReal, newVersionNumReal));
        String updateStr = updateSnapshotToRelease(projectId, newVersionNumReal);
        log.info("updateStr {}", updateStr);

        // 5. 返回成功创建的版本名称 (从参数中获取)
        return packageVersion;
    }

    private String updateSnapshotToRelease(Long projectId, Long projectVersion) {
        LambdaQueryWrapper<FileVersionEntity> tmpFileVersionLQW = new LambdaQueryWrapper<FileVersionEntity>()
                .eq(FileVersionEntity::getProjectId, projectId)
                .eq(FileVersionEntity::getVersionNum, SNAPSHOT_VERSION);
        List<FileVersionEntity> tmpFileVersionList = this.fileVersionMapper.selectList(tmpFileVersionLQW);

        StringBuilder sb = new StringBuilder();
        for (FileVersionEntity tmpFile : tmpFileVersionList) {
            // 获取最新的release版本
            LambdaQueryWrapper<FileVersionEntity> latestLQW = new LambdaQueryWrapper<FileVersionEntity>()
                    .eq(FileVersionEntity::getFileId, tmpFile.getFileId())
                    .lt(FileVersionEntity::getVersionNumReal, SNAPSHOT_VERSION_REAL)
                    .orderByDesc(FileVersionEntity::getVersionNumReal)
                    .last("limit 1");
            FileVersionEntity latestReleaseVersion = this.fileVersionMapper.selectOne(latestLQW);

            VersionUtils.incrementVersionFileVersion(latestReleaseVersion, tmpFile);
            LambdaUpdateWrapper<FileVersionEntity> fileLUW = new LambdaUpdateWrapper<FileVersionEntity>()
                    .eq(FileVersionEntity::getId, tmpFile.getId())
                    .set(FileVersionEntity::getVersionNum, tmpFile.getVersionNum())
                    .set(FileVersionEntity::getVersionNumReal, tmpFile.getVersionNumReal())
                    .set(FileVersionEntity::getProjectVersionNumReal, projectVersion)
                    .set(FileVersionEntity::getUpdateTime, new Date());
            int updateRes = this.fileVersionMapper.update(null, fileLUW);

            sb.append(tmpFile.getFilePath()).append(":").append(tmpFile.getVersionNum())
                    .append(",").append("\n");
        }

        return sb.toString();
    }

    /**
     * 获取指定项目下的所有文件版本实体。
     *
     * @param projectId 项目ID
     * @return 文件版本实体列表，如果找不到则返回空列表。
     */
    private List<FileEntity> getAllFilesForProject(Long projectId) {
        if (projectId == null) {
            log.warn("Attempted to get file versions with null project ID.");
            return Collections.emptyList(); // 或者根据需要抛出异常
        }
        log.debug("Fetching all file versions for project ID [{}].", projectId);
        List<FileEntity> fileVersions = fileMapper.selectList(new LambdaQueryWrapper<FileEntity>()
                .eq(FileEntity::getProjectId, projectId));
        return fileVersions == null ? Collections.emptyList() : fileVersions;
    }

    /**
     * 获取指定项目下所有文件的最新版本ID列表。
     *
     * @param projectId 项目ID
     * @return 最新版本ID列表
     */
    private List<FileVersionEntity> getAllLatestFileVersions(Long projectId) {
        if (projectId == null) {
            log.warn("Attempted to get latest file version ids with null project ID.");
            return Collections.emptyList();
        }
        List<FileEntity> files = getAllFilesForProject(projectId);
        if (files.isEmpty()) {
            return Collections.emptyList();
        }
        // 把files里面的latest version id组成要给list，然后用这个去查询所有的version
        List<Long> latestVersionIds = files.stream()
                .map(FileEntity::getLatestVersionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (latestVersionIds.isEmpty()) {
            return Collections.emptyList();
        }
        return fileVersionMapper.selectBatchIds(latestVersionIds);
    }

    @Override
    public boolean exists(String path) throws Exception {
        return false;
    }

    @Override
    public InputStream readFile(String path, String version) throws Exception {
        return null;
    }

    @Override
    public List<ResourcePackage> loadProjectResourcePackages(String project) throws Exception {
        return Collections.emptyList();
    }

    @Override
    public Repository loadRepository(String project, User user, boolean classify, FileType[] types, String searchFileName) throws Exception {
        return null;
    }

    @Override
    public void delete(String path, User user) throws Exception {

    }

    @Override
    public String saveFile(String path, String content, User user, boolean newVersion, String versionComment) throws Exception {
        return "";
    }

    @Override
    public void createDirectory(String path, User user) throws Exception {

    }

    @Override
    public List<String> listProjectVersions(String projectName) throws Exception {
        ProjectEntity project = projectMapper.selectOne(new LambdaQueryWrapper<ProjectEntity>()
                .eq(ProjectEntity::getName, projectName)
                .last("limit 1"));
        if (project == null) {
            throw new RuleException("Project [" + projectName + "] not found.");
        }
        List<ProjectVersionEntity> versions = projectVersionMapper.selectList(new LambdaQueryWrapper<ProjectVersionEntity>()
                .eq(ProjectVersionEntity::getProjectId, project.getId())
                .orderByDesc(ProjectVersionEntity::getCreateTime)); // 按创建时间排序

        return versions.stream().map(ProjectVersionEntity::getVersionName).collect(Collectors.toList());
    }

    @Override
    public Object checkoutProjectVersion(String projectName, String version) throws Exception {
        ProjectEntity project = projectMapper.selectOne(new LambdaQueryWrapper<ProjectEntity>()
                .eq(ProjectEntity::getName, projectName)
                .last("limit 1"));
        if (project == null) {
            throw new RuleException("Project [" + projectName + "] not found.");
        }

        ProjectVersionEntity projectVersion = projectVersionMapper.selectOne(new LambdaQueryWrapper<ProjectVersionEntity>()
                .eq(ProjectVersionEntity::getProjectId, project.getId())
                .eq(ProjectVersionEntity::getVersionName, version)
                .last("limit 1"));
        if (projectVersion == null) {
            throw new RuleException("Project version [" + version + "] for project [" + projectName + "] not found.");
        }

        List<ProjectVersionMappingEntity> mappings = projectVersionMappingMapper.selectList(new LambdaQueryWrapper<ProjectVersionMappingEntity>()
                .eq(ProjectVersionMappingEntity::getProjectVersionId, projectVersion.getId()));

        if (CollectionUtils.isEmpty(mappings)) {
            return Collections.emptyMap(); // 空版本
        }

        List<Long> fileVersionIds = mappings.stream()
                .map(ProjectVersionMappingEntity::getFileVersionId)
                .collect(Collectors.toList());

        // 批量查询文件版本内容
        List<FileVersionEntity> fileVersions = fileVersionMapper.selectBatchIds(fileVersionIds);

        return fileVersions.stream()
                .collect(Collectors.toMap(FileVersionEntity::getFilePath, FileVersionEntity::getFileContent));

    }
}