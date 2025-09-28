package com.wind.office.excel.template;

import com.alibaba.excel.EasyExcelFactory;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.builder.ExcelWriterBuilder;
import com.alibaba.excel.write.handler.WriteHandler;
import com.alibaba.excel.write.style.column.SimpleColumnWidthStyleStrategy;
import com.alibaba.excel.write.style.row.SimpleRowHeightStyleStrategy;
import com.wind.common.exception.AssertUtils;
import com.wind.common.exception.BaseException;
import com.wind.common.exception.DefaultExceptionCode;
import com.wind.office.excel.ExportExcelDataFetcher;
import com.wind.office.excel.metadata.ExcelCellDescriptor;
import org.jetbrains.annotations.NotNull;
import org.springframework.format.Printer;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Excel 模板渲染器，基于 EasyExcel 实现。
 *
 * <p>支持通过 {@link ExcelTemplateRenderBuilder} 构建器模式定义 Excel 渲染规则。</p>
 * <ul>
 *   <li>支持渲染模式：
 *     <ul>
 *       <li>{@link ExcelTemplateRenderBuilder#rows()} 行模式：以行为单位渲染数据</li>
 *       <li>{@link ExcelTemplateRenderBuilder#cells()} 单元格模式：按单元格自由渲染</li>
 *     </ul>
 *   </li>
 *   <li>支持样式策略设置：列宽、行高</li>
 *   <li>支持静态数据或懒加载数据绑定</li>
 *   <li>支持设置标题、字段表达式</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * ExcelTemplateRender render = ExcelTemplateRender
 *     .withTemp("export-template")
 *     .sheets(0, "Sheet1")
 *     .rows()
 *     .headers(headers)
 *     .expressions(List.of("name", "age"))
 *     .data(dataList)
 *     .build();
 * render.render();
 * }</pre>
 *
 * @param filepath     excel 文件保存路径
 * @param sheetRenders excel sheet render
 * @author wuxp
 * @date 2025-07-28 13:02
 */
public record ExcelTemplateRender(Path filepath, List<WriteHandler> writeHandlers, List<SheetRender> sheetRenders) {

    /**
     * 渲染 excel
     */
    public void render() {
        ExcelWriterBuilder builder = EasyExcelFactory.write(newOutputStream());
        for (WriteHandler writeHandler : writeHandlers) {
            builder.registerWriteHandler(writeHandler);
        }
        try (ExcelWriter writer = builder.build()) {
            for (SheetRender render : sheetRenders) {
                render.render(writer);
            }
            writer.finish();
        }
    }

    private OutputStream newOutputStream() {
        try {
            Files.deleteIfExists(filepath);
            File file = new File(filepath.toString());
            // 创建文件
            AssertUtils.isTrue(file.createNewFile(), () -> "create file = " + filepath + " failure");
            return Files.newOutputStream(filepath);
        } catch (IOException e) {
            throw new BaseException(DefaultExceptionCode.COMMON_ERROR, "new file outstream exception, filepath = " + filepath, e);
        }
    }

    public static ExcelTemplateRenderBuilder withTemp(String filename) {
        try {
            return withPath(Files.createTempFile(filename, ".xlsx"));
        } catch (IOException e) {
            throw new BaseException(DefaultExceptionCode.COMMON_ERROR, "create temp file exception , file = " + filename, e);
        }
    }

    public static ExcelTemplateRenderBuilder withFile(String filepath) {
        return withPath(Paths.get(filepath));
    }

    public static ExcelTemplateRenderBuilder withPath(Path filepath) {
        return new ExcelTemplateRenderBuilder(filepath);
    }

    public static ExcelTemplateRenderBuilder builder(Path filepath) {
        return new ExcelTemplateRenderBuilder(filepath);
    }

    public static class ExcelTemplateRenderBuilder {

        private final Path filepath;

        private final AtomicInteger fetchSize = new AtomicInteger(500);

        private final List<WriteHandler> writeHandlers = new ArrayList<>();

        private final List<SheetRender.SheetRenderBuilder> sheetRenders = new ArrayList<>();

        private Printer<?> defaultPrinter;

        private ExcelTemplateRenderBuilder(Path filepath) {
            this.filepath = filepath;
        }

        public ExcelTemplateRenderBuilder sheets(int index, String name) {
            sheetRenders.add(SheetRender.builder(index, name));
            return style(18, 25);
        }

        public ExcelTemplateRenderBuilder style(int columnWidth, int rowHeight) {
            writeHandlers.removeIf(writeHandler -> writeHandler instanceof SimpleColumnWidthStyleStrategy || writeHandler instanceof SimpleRowHeightStyleStrategy);
            writeHandlers.add(new SimpleRowHeightStyleStrategy((short) rowHeight, (short) rowHeight));
            writeHandlers.add(new SimpleColumnWidthStyleStrategy(columnWidth));
            return this;
        }

        public ExcelTemplateRenderBuilder rows() {
            latestSheet().addDataSupplier(SheetDataSupplier.row());
            return this;
        }

        public ExcelTemplateRenderBuilder cells() {
            latestSheet().addDataSupplier(SheetDataSupplier.cel());
            return this;
        }

        public ExcelTemplateRenderBuilder defaultPrinter(Printer<?> defaultPrinter) {
            this.defaultPrinter = defaultPrinter;
            return this;
        }

        public ExcelTemplateRenderBuilder titles(List<ExcelCellDescriptor> titles) {
            if (defaultPrinter != null) {
                titles = titles.stream()
                        .map(descriptor -> {
                            if (descriptor.hasPrinter()) {
                                return descriptor;
                            }
                            // 设置默认的 printer
                            return ExcelCellDescriptor.builder(descriptor.getTitle(), descriptor.getExpression())
                                    .attributes(descriptor.getAttributes().values())
                                    .printer(defaultPrinter)
                                    .build();

                        }).collect(Collectors.toList());
            }
            latestSheet().latestRender().titles(titles);
            return this;
        }

        /**
         * 字段取值表达式，一般是字段名称
         *
         * @param expressions 表达式
         * @return this
         */
        public ExcelTemplateRenderBuilder expressions(List<String> expressions) {
            return titles(expressions.stream()
                    .map(ExcelCellDescriptor::expression)
                    .collect(Collectors.toList()));
        }

        public ExcelTemplateRenderBuilder data(List<?> data) {
            latestSheet().latestRender().data(data);
            return this;
        }

        public ExcelTemplateRenderBuilder single(List<Object> row) {
            latestSheet().latestRender().data(Collections.singletonList(row));
            return this;
        }

        public <T> ExcelTemplateRenderBuilder data(ExportExcelDataFetcher<T> fetcher) {
            latestSheet().latestRender().data(fetcher);
            return this;
        }

        public ExcelTemplateRenderBuilder fetchSize(int fetchSize) {
            this.fetchSize.set(fetchSize);
            return this;
        }

        @NotNull
        private SheetRender.SheetRenderBuilder latestSheet() {
            SheetRender.SheetRenderBuilder result = CollectionUtils.lastElement(sheetRenders);
            AssertUtils.notNull(result, "lasest SheetRenderBuilder must not null");
            return result;
        }

        public ExcelTemplateRender build() {
            List<SheetRender> renders = sheetRenders.stream()
                    .map(b -> b.build(fetchSize.get()))
                    .collect(Collectors.toList());
            return new ExcelTemplateRender(filepath, writeHandlers, renders);
        }
    }

}
