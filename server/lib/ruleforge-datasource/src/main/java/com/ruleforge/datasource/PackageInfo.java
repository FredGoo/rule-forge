package com.ruleforge.datasource;

/**
 * V5.23 — Data source abstraction module.
 *
 * <p>This module provides the runtime infrastructure for executing third-party API data sources
 * inside the decision flow. It is shared by {@code app/ruleforge-console-app} (LLM-generated
 * data sources are compiled and registered here) and {@code app/ruleforge-executor-app} (data
 * sources are loaded from compiled artifacts at startup).
 *
 * <h2>What this module provides</h2>
 * <ul>
 *   <li>{@link BaseApiDataSource} — abstract class LLM-generated data sources extend</li>
 *   <li>{@link DataSourceRegistry} — Spring bean registry; decision engine calls {@code fetch(name, vars)}</li>
 *   <li>{@code JavaSourceCompiler} — pure function {@code compile(javaCode) -> byte[]}</li>
 *   <li>{@code ClassLoaderPool} — load .class bytes into isolated {@code URLClassLoader}</li>
 *   <li>{@code DataSourceAuditLog} — interface; each app implements to write its own audit table</li>
 * </ul>
 *
 * <h2>What this module does NOT do</h2>
 * <ul>
 *   <li>Does NOT connect to MySQL / Postgres / any database (lib has no DB drivers in pom)</li>
 *   <li>Does NOT manage third-party API credentials (each app stores in its own way)</li>
 *   <li>Does NOT integrate with LLM / V5.22 draft flow (that lives in console-app)</li>
 *   <li>Does NOT perform HTTP calls directly (subclasses use {@code RestTemplate} via Spring DI)</li>
 * </ul>
 *
 * <h2>Module boundary</h2>
 * <p>Persistence is always the responsibility of the calling app. Console-app and executor-app each
 * implement {@code DataSourceAuditLog} against their own datasource (e.g. {@code app_db} for
 * nd_data_source_call). The lib exposes the contract, the app implements it.
 */
public final class PackageInfo {
    private PackageInfo() {}
}
