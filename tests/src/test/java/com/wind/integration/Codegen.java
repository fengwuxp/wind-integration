package com.wind.integration;


import com.google.common.collect.ImmutableMap;
import com.wind.integration.system.services.dal.entities.DictionaryMetadata;
import com.wind.integration.system.services.dal.entities.SystemConfig;
import com.wind.tools.mybatisflex.codegen.CodegenConfiguration;
import com.wind.tools.mybatisflex.codegen.DefaultServiceCodeGenerator;
import org.junit.jupiter.api.Assertions;

import java.io.File;
import java.nio.file.Paths;
import java.util.Map;

/**
 * 通过实体类生成基础的 curd 服务和控制器
 *
 * @author wuxp
 * @date 2023-10-07 13:09
 **/
public class Codegen {

    /**
     * 避免测试阶段执行
     */
    public static void main(String[] args) throws Exception {

        String[] outPaths = {
                // main 方法下为项目根路径
                System.getProperty("user.dir"),
                "tests",
                "target",
                "codegen-result"
        };
        String outBasePath = getPath(outPaths);
        CodegenConfiguration configuration = CodegenConfiguration
                .builder()
                // 业务服务基础包名
                .basePackage("com.wind.integration.system")
                .outDir(outBasePath)
                // 控制器基础路径
                .requestBaseMapping("WindWebConstants.ADMIN_API_V1_PREFIX + \"/system-configs\"")
                .build();
        /**
         * @key 实体说明
         * @value 实体类类型
         */
        Map<String, Class<?>> classes = ImmutableMap.of(
                "系统配置", SystemConfig.class,
                "数据字典", DictionaryMetadata.class
                );
        DefaultServiceCodeGenerator generator = new DefaultServiceCodeGenerator(configuration);
        classes.forEach((desc, clazz) -> generator.gen(clazz, desc));
        Assertions.assertTrue(new File(outBasePath).exists());
    }

    private static String getPath(String[] paths) {
        return Paths.get(String.join(File.separator, paths)).toString();
    }

}
