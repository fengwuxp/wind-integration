package com.wind.integration.operator;

import com.wind.common.WindConstants;
import com.wind.security.core.WindSecurityAccessOperations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

class WindOperatorTests {

    private String previousApplicationName;

    @BeforeEach
    void setup() {
        previousApplicationName = System.getProperty(WindConstants.SPRING_APPLICATION_NAME);
        System.setProperty(WindConstants.SPRING_APPLICATION_NAME, "current-app");
        new WindOperatorFactory(createSecurityAccessOperations());
    }

    @AfterEach
    void cleanup() {
        if (previousApplicationName == null) {
            System.clearProperty(WindConstants.SPRING_APPLICATION_NAME);
            return;
        }
        System.setProperty(WindConstants.SPRING_APPLICATION_NAME, previousApplicationName);
    }

    @Test
    void testCreateCurrentApplicationOperator() {
        WindOperator operator = WindOperatorFactory.current(1L, "测试用户", OperationActorType.END_USER);

        Assertions.assertEquals(1L, (Long) operator.getOperatorId());
        Assertions.assertEquals("测试用户", operator.getOperatorName());
        Assertions.assertEquals("current-app", operator.getAppName());
        Assertions.assertEquals(OperationActorType.END_USER, operator.getActorType());
        Assertions.assertTrue(operator.isEndUser());
        Assertions.assertFalse(operator.isSystem());
        Assertions.assertTrue(operator.hasAuthority("ORDER_APPROVE"));
        Assertions.assertTrue(operator.hasRole("TENANT_ADMIN"));
        Assertions.assertTrue(operator.isSuperAdmin());
    }

    @Test
    void testCreatePlatformOperator() {
        WindOperator unnamedOperator = WindOperatorFactory.platformOperator(1L);
        WindOperator namedOperator = WindOperatorFactory.platformOperator(2L, "平台运营人员");

        Assertions.assertEquals(OperationActorType.PLATFORM_OPERATOR, unnamedOperator.getActorType());
        Assertions.assertEquals("1", unnamedOperator.getOperatorName());
        Assertions.assertEquals("current-app", unnamedOperator.getAppName());
        Assertions.assertEquals(OperationActorType.PLATFORM_OPERATOR, namedOperator.getActorType());
        Assertions.assertEquals("平台运营人员", namedOperator.getOperatorName());
        Assertions.assertEquals("current-app", namedOperator.getAppName());
    }

    @Test
    void testCreateEndUser() {
        WindOperator unnamedOperator = WindOperatorFactory.endUser(1L);
        WindOperator namedOperator = WindOperatorFactory.endUser(2L, "终端用户");

        Assertions.assertEquals(OperationActorType.END_USER, unnamedOperator.getActorType());
        Assertions.assertEquals("1", unnamedOperator.getOperatorName());
        Assertions.assertEquals("current-app", unnamedOperator.getAppName());
        Assertions.assertEquals(OperationActorType.END_USER, namedOperator.getActorType());
        Assertions.assertEquals("终端用户", namedOperator.getOperatorName());
        Assertions.assertEquals("current-app", namedOperator.getAppName());
    }

    @Test
    void testCreateOperatorFromSourceApplication() {
        WindOperator operator = WindOperatorFactory.fromApplication(
                1L, "测试用户", "openapi-app", OperationActorType.PLATFORM_OPERATOR);

        Assertions.assertEquals("openapi-app", operator.getAppName());
        Assertions.assertTrue(operator.isApp("openapi-app"));
        Assertions.assertFalse(operator.isApp("current-app"));
    }

    @Test
    void testCreateSystemAndRiskEngineOperator() {
        WindOperator system = WindOperatorFactory.system();
        WindOperator riskEngine = WindOperatorFactory.riskEngine(2L);

        Assertions.assertEquals("current-app", system.getAppName());
        Assertions.assertEquals(OperationActorType.SYSTEM, system.getActorType());
        Assertions.assertTrue(system.isSystem());
        Assertions.assertEquals("current-app", riskEngine.getAppName());
        Assertions.assertEquals(OperationActorType.RISK_ENGINE, riskEngine.getActorType());
        Assertions.assertFalse(riskEngine.isSystem());
    }

    @Test
    void testIdentifySystemActorType() {
        Assertions.assertTrue(OperationActorType.SYSTEM.isSystem());
        Assertions.assertFalse(OperationActorType.RISK_ENGINE.isSystem());
        Assertions.assertFalse(OperationActorType.PLATFORM_OPERATOR.isSystem());
        Assertions.assertFalse(OperationActorType.TENANT_OPERATOR.isSystem());
        Assertions.assertFalse(OperationActorType.TENANT_API_CLIENT.isSystem());
        Assertions.assertFalse(OperationActorType.END_USER.isSystem());
    }

    private static WindSecurityAccessOperations createSecurityAccessOperations() {
        return new WindSecurityAccessOperations() {
            @Override
            public boolean hasAnyAuthority(String... authorities) {
                return Arrays.asList(authorities).contains("ORDER_APPROVE");
            }

            @Override
            public boolean hasAnyRole(String... roles) {
                return Arrays.asList(roles).contains("TENANT_ADMIN");
            }

            @Override
            public boolean isSuperAdmin() {
                return true;
            }
        };
    }
}
