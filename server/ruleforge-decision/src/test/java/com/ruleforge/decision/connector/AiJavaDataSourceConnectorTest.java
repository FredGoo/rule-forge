package com.ruleforge.decision.connector;

import com.ruleforge.decision.datasource.JavaSourceCompiler;
import com.ruleforge.decision.entity.Datasource;
import com.ruleforge.decision.entity.DatasourceLog;
import com.ruleforge.decision.repository.DatasourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/**
 * V5.23 — AiJavaDataSourceConnector 行为规范。
 *
 * <p>用真 javac 跑(Phase 1 JavaSourceCompiler 已测过),
 * mock 掉 DatasourceRepository 让 audit log 可断言。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AiJavaDataSourceConnector — AI Java 数据源连接器")
class AiJavaDataSourceConnectorTest {

    private static final String SOURCE = """
            package com.ruleforge.user;
            import com.ruleforge.decision.datasource.IJavaDataSource;
            import java.util.Map;
            public class Credit implements IJavaDataSource {
                @Override public String getName() { return "credit"; }
                @Override public Object fetchField(String entityId, String fieldName, Map<String, String> ctx) {
                    if ("score".equals(fieldName)) return 720;
                    if ("decision".equals(fieldName)) return "APPROVE";
                    return null;
                }
            }
            """;

    @Mock private DatasourceRepository datasourceRepository;

    private AiJavaDataSourceConnector connector;
    private byte[] classBytes;
    private String base64;
    private String configJson;

    @BeforeEach
    void setUp() throws Exception {
        connector = new AiJavaDataSourceConnector(datasourceRepository);
        JavaSourceCompiler.CompileResult cr = new JavaSourceCompiler().compile(SOURCE);
        assertThat(cr.success).as("compile err=" + cr.error).isTrue();
        classBytes = cr.classBytes;
        base64 = Base64.getEncoder().encodeToString(classBytes);
        configJson = "{\"className\":\"" + cr.fqcn
            + "\",\"classBytesBase64\":\"" + base64 + "\"}";
    }

    private Datasource ds(String config) {
        Datasource d = new Datasource();
        d.setId(42L);
        d.setName("phase7_credit");
        d.setType("AI_JAVA");
        d.setConfigJson(config);
        d.setEnabled(true);
        return d;
    }

    @Nested
    @DisplayName("Scenario: 基础协议")
    class BasicProtocol {

        @Test
        @DisplayName("getConnectorType 返 'AI_JAVA'")
        void shouldReturnAiJavaType() {
            assertThat(connector.getConnectorType()).isEqualTo("AI_JAVA");
        }
    }

    @Nested
    @DisplayName("Scenario: 合法 fetch")
    class FetchValid {

        @Test
        @DisplayName("Given 合法 config + score 字段 When fetchFieldValue Then 返 720 + 审计 SUCCESS")
        void shouldFetchAndAuditSuccess() {
            Datasource d = ds(configJson);
            Object v = connector.fetchFieldValue(d, "u1", "MyClass", "score", Map.of());

            assertThat(v).isEqualTo(720);

            ArgumentCaptor<DatasourceLog> cap = ArgumentCaptor.forClass(DatasourceLog.class);
            verify(datasourceRepository).insertDatasourceLog(cap.capture());
            DatasourceLog log = cap.getValue();
            assertThat(log.getDatasourceId()).isEqualTo(42L);
            assertThat(log.getDataSource()).isEqualTo("AI_JAVA");
            assertThat(log.getApiEndpoint()).isEqualTo("score");
            assertThat(log.getStatus()).isEqualTo("SUCCESS");
            assertThat(log.getUserId()).isEqualTo("u1");
        }

        @Test
        @DisplayName("Given fetchField 返 null When fetchFieldValue Then 返 null + 审计仍记 SUCCESS(不是 ERROR)")
        void shouldAuditSuccessOnNullResult() {
            Datasource d = ds(configJson);
            Object v = connector.fetchFieldValue(d, "u1", "MyClass", "unknownField", Map.of());
            assertThat(v).isNull();

            ArgumentCaptor<DatasourceLog> cap = ArgumentCaptor.forClass(DatasourceLog.class);
            verify(datasourceRepository).insertDatasourceLog(cap.capture());
            assertThat(cap.getValue().getStatus()).isEqualTo("SUCCESS");
        }
    }

    @Nested
    @DisplayName("Scenario: 错误路径")
    class ErrorPaths {

        @Test
        @DisplayName("Given config_json 缺 className When fetch Then 返 null + 审计 ERROR 含 'missing'")
        void shouldErrorOnMissingClassName() {
            Datasource d = ds("{\"classBytesBase64\":\"" + base64 + "\"}");
            Object v = connector.fetchFieldValue(d, "u1", "MyClass", "score", Map.of());
            assertThat(v).isNull();

            ArgumentCaptor<DatasourceLog> cap = ArgumentCaptor.forClass(DatasourceLog.class);
            verify(datasourceRepository).insertDatasourceLog(cap.capture());
            DatasourceLog log = cap.getValue();
            assertThat(log.getStatus()).isEqualTo("ERROR");
            assertThat(log.getErrorMessage()).contains("missing");
        }

        @Test
        @DisplayName("Given config_json 缺 classBytesBase64 When fetch Then 返 null + 审计 ERROR")
        void shouldErrorOnMissingBytes() {
            Datasource d = ds("{\"className\":\"com.ruleforge.user.Credit\"}");
            Object v = connector.fetchFieldValue(d, "u1", "MyClass", "score", Map.of());
            assertThat(v).isNull();

            ArgumentCaptor<DatasourceLog> cap = ArgumentCaptor.forClass(DatasourceLog.class);
            verify(datasourceRepository).insertDatasourceLog(cap.capture());
            assertThat(cap.getValue().getStatus()).isEqualTo("ERROR");
        }

        @Test
        @DisplayName("Given classBytesBase64 是非法 base64 When fetch Then 返 null + 审计 ERROR 含 'invalid base64'")
        void shouldErrorOnInvalidBase64() {
            Datasource d = ds("{\"className\":\"com.ruleforge.user.Credit\",\"classBytesBase64\":\"!!!not-base64!!!\"}");
            Object v = connector.fetchFieldValue(d, "u1", "MyClass", "score", Map.of());
            assertThat(v).isNull();

            ArgumentCaptor<DatasourceLog> cap = ArgumentCaptor.forClass(DatasourceLog.class);
            verify(datasourceRepository).insertDatasourceLog(cap.capture());
            assertThat(cap.getValue().getStatus()).isEqualTo("ERROR");
            assertThat(cap.getValue().getErrorMessage()).contains("invalid base64");
        }

        @Test
        @DisplayName("Given classBytesBase64 是合法 base64 但内容不是 .class When fetch Then 返 null + 审计 ERROR 含 'magic'")
        void shouldErrorOnBadMagicBytes() {
            byte[] garbage = "not-a-class-file".getBytes();
            String b64 = Base64.getEncoder().encodeToString(garbage);
            Datasource d = ds("{\"className\":\"com.ruleforge.user.Credit\",\"classBytesBase64\":\"" + b64 + "\"}");
            Object v = connector.fetchFieldValue(d, "u1", "MyClass", "score", Map.of());
            assertThat(v).isNull();

            ArgumentCaptor<DatasourceLog> cap = ArgumentCaptor.forClass(DatasourceLog.class);
            verify(datasourceRepository).insertDatasourceLog(cap.capture());
            assertThat(cap.getValue().getStatus()).isEqualTo("ERROR");
            assertThat(cap.getValue().getErrorMessage()).contains("magic");
        }
    }

    @Nested
    @DisplayName("Scenario: testConnection")
    class TestConnection {

        @Test
        @DisplayName("Given 合法 config When testConnection Then 返 true")
        void shouldReturnTrueForValidConfig() {
            Datasource d = ds(configJson);
            assertThat(connector.testConnection(d)).isTrue();
        }

        @Test
        @DisplayName("Given 缺 className When testConnection Then 返 false")
        void shouldReturnFalseForMissingFields() {
            Datasource d = ds("{}");
            assertThat(connector.testConnection(d)).isFalse();
        }
    }
}
