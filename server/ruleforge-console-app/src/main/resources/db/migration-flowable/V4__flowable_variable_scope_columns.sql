-- Flowable 8.0.0 variable service 需要 scope 列
--
-- 背景: flowable-variable-service-8.0.0 的 VariableInstance.xml mapper 在
--   SELECT/UPDATE 时引用 SCOPE_ID_ / SUB_SCOPE_ID_ / SCOPE_TYPE_ 三列:
--     select * from ACT_RU_VARIABLE
--       where SUB_SCOPE_ID_ = ? and SCOPE_TYPE_ in (?, ?, ?)
--   HistoricVariableInstance.xml 同样引用:
--     insert into ACT_HI_VARINST (... SCOPE_ID_, SUB_SCOPE_ID_, SCOPE_TYPE_, ...)
--
-- V1 (flowable_engine_mysql.sql) 只创建了老版 6.x/7.x 的 ACT_RU_VARIABLE /
-- ACT_HI_VARINST,缺这三列。结果:任何 /api/loan/evaluate 触发的
-- runtimeService.startProcessInstanceByKey() 在第一次
-- variableService.createVariableInstance() 就报
-- "Unknown column 'SUB_SCOPE_ID_' in 'where clause'",生产决策路径 100% 跑不起来。
--
-- 同模块还可能涉及其他 service 拆出来的表(entitylink / identitylink / task),
-- 后续如果再有 "Unknown column 'X_' in 'where clause'" 同类错误,沿用此模式补列。
--
-- 历史: V1 把 schema.version 标记成 8.0.0.0,但这张表用的是 6.x 时代的列,
--       这是 V1 抄错 jar 里老 engine.sql 的遗留 bug。

-- runtime 表
alter table ACT_RU_VARIABLE
    add column SCOPE_ID_ varchar(64) default null,
    add column SUB_SCOPE_ID_ varchar(64) default null,
    add column SCOPE_TYPE_ varchar(255) default null,
    -- Flowable 8.0.0 加的 bigint / meta 列(mappers 强制要求,跟 DOUBLE_/TEXT_ 一起承载变量值)
    add column LONG_ bigint default null,
    add column META_INFO_ varchar(4000) default null;

create index ACT_IDX_VAR_SCOPE on ACT_RU_VARIABLE(SCOPE_ID_, SCOPE_TYPE_);
create index ACT_IDX_VAR_SUB_SCOPE on ACT_RU_VARIABLE(SUB_SCOPE_ID_);

-- history 表
alter table ACT_HI_VARINST
    add column SCOPE_ID_ varchar(64) default null,
    add column SUB_SCOPE_ID_ varchar(64) default null,
    add column SCOPE_TYPE_ varchar(255) default null,
    add column LONG_ bigint default null,
    add column META_INFO_ varchar(4000) default null;

create index ACT_IDX_HI_VAR_SCOPE on ACT_HI_VARINST(SCOPE_ID_, SCOPE_TYPE_);
create index ACT_IDX_HI_VAR_SUB_SCOPE on ACT_HI_VARINST(SUB_SCOPE_ID_);
