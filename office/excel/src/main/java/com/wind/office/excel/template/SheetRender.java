package com.wind.office.excel.template;

import com.alibaba.excel.EasyExcelFactory;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.wind.common.WindConstants;
import com.wind.common.exception.AssertUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * sheet 渲染器
 *
 * @author wuxp
 * @date 2025-07-28 13:32
 **/
class SheetRender {

    private final int index;

    private final String sheetName;

    private final List<SheetDataSupplier> suppliers;

    SheetRender(int index, String sheetName, List<SheetDataSupplier> suppliers) {
        this.index = index;
        this.sheetName = sheetName;
        this.suppliers = suppliers;
    }

    static SheetRenderBuilder builder(int index, String sheetName) {
        return new SheetRenderBuilder(index, sheetName);
    }

    void render(ExcelWriter excelWriter) {
        List<List<String>> data = suppliers.stream()
                .map(SheetDataSupplier::get)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        if (CollectionUtils.firstElement(suppliers) instanceof SheetDataSupplier.CellSupplier) {
            // 列写入模式，列转行
            data = convertColumnsToRows(data);
        }
        WriteSheet sheet = EasyExcelFactory.writerSheet(sheetName).sheetNo(index).build();
        excelWriter.write(data, sheet);
    }

    /**
     * 列数据转行数据
     *
     * @param columns 按列的数据
     * @return 行数据
     */
    List<List<String>> convertColumnsToRows(List<List<String>> columns) {
        int maxColumns = columns.stream().mapToInt(List::size).max().orElse(0);
        List<List<String>> result = new ArrayList<>();
        for (int col = 0; col < maxColumns; col++) {
            List<String> column = new ArrayList<>();
            for (List<String> values : columns) {
                column.add(col < values.size() ? values.get(col) : WindConstants.EMPTY);
            }
            result.add(column);
        }

        return result;
    }


    static class SheetRenderBuilder {

        private final int index;

        private final String sheetName;

        private final List<SheetDataSupplier.SheetDataSupplierBuilder> builders = new ArrayList<>();

        SheetRenderBuilder(int index, String sheetName) {
            this.index = index;
            this.sheetName = sheetName;
        }

        void addDataSupplier(SheetDataSupplier.SheetDataSupplierBuilder builder) {
            this.builders.add(builder);
        }

        @NotNull
        SheetDataSupplier.SheetDataSupplierBuilder latestRender() {
            SheetDataSupplier.SheetDataSupplierBuilder result = CollectionUtils.lastElement(builders);
            AssertUtils.notNull(result, "lasest SheetDataSupplierBuilder must not null");
            return result;
        }

        SheetRender build(int fetchSize) {
            List<SheetDataSupplier> dataSuppliers = builders.stream()
                    .map(b -> b.build(fetchSize))
                    .collect(Collectors.toList());
            Set<Class<?>> classes = dataSuppliers.stream()
                    .map(SheetDataSupplier::getClass)
                    .collect(Collectors.toSet());
            AssertUtils.isTrue(classes.size() == 1, () -> "SheetDataSupplier uses either row mode or column mode. Mixing the two is not allowed.");
            return new SheetRender(index, sheetName, dataSuppliers);
        }
    }

}
