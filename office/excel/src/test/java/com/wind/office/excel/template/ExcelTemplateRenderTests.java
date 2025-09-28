package com.wind.office.excel.template;

import com.wind.office.excel.metadata.ExcelCellDescriptor;
import com.wind.office.excel.metadata.ExcelCellPrinter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

/**
 * @author wuxp
 * @date 2025-07-28 13:03
 **/
class ExcelTemplateRenderTests {

    @Test
    void testRender() throws Exception {
        ExcelTemplateRender render = ExcelTemplateRender
                .withFile(Objects.requireNonNull(getClass().getResource("/")).getPath() + "test-template-render.xlsx")
                .defaultPrinter((object, locale) -> String.valueOf(object))
                // 设置 sheet 名称
                .sheets(0, "test-1")
                // 按行批量写入
                .rows()
                // 设置标题和字段取值表达式
                .titles(Arrays.asList(
                        ExcelCellDescriptor.builder("姓名", "name")
                                .printer((ExcelCellPrinter<String>) (cellValue, expression, row, locale) -> cellValue + "-001")
                                .build(),
                        ExcelCellDescriptor.of("年龄", "age")
                ))
                // 分页获取数据
                .data((page, size) -> {
                    if (page >= 2) {
                        return Collections.emptyList();
                    }
                    return Arrays.asList(
                            new Example("张三", 18),
                            new Example("李四", 21)
                    );
                })
                .data(Arrays.asList(
                        new Example("李四1", 21),
                        new Example("李四2", 21),
                        new Example("李四3", 21)
                ))
                .rows()
                .single(Arrays.asList("王五", 16))
                // 结束行批量写入，改为单行写入模式
                .rows()
                // 单行写入
                .data(Collections.singletonList(Arrays.asList("总计", "1000", "2000")))
                .sheets(1, "test-2")
                // 按列单列写入
                .cells()
                .single(Arrays.asList("收益项分类", "提现手续费", "交易手续费", "附加费", "总和"))
                .cells()
                .data(Collections.singletonList(Arrays.asList("总计", "1000", "2000", "3000", "6000")))
                // 按列批量写入
                .cells()
                // 仅设置取值表达式
                .expressions(Arrays.asList("name", "age", "age", "name", "age"))
                // 获取数据
                .data((page, size) -> {
                    if (page >= 2) {
                        return Collections.emptyList();
                    }
                    return Arrays.asList(
                            new Example("张三", 18),
                            new Example("李四", 21)
                    );
                })
                .cells()
                .single(Arrays.asList("王五", 32, "天才", "万事通", "九离紫火大运"))
                .build();

        render.render();
        Assertions.assertNotNull(render.filepath());
//        Assertions.assertTrue(Files.deleteIfExists(render.getFilepath()));
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class Example {

        private String name;

        private Integer age;
    }
}
