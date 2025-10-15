package com.wind.office.excel.template;

import com.wind.office.excel.ExportExcelDataFetcher;
import com.wind.office.excel.export.SpringExpressionObjectToRowConverter;
import com.wind.office.excel.metadata.ExcelCellDescriptor;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * sheet 数据 提供者
 *
 * @author wuxp
 * @date 2025-07-28 13:44
 **/
@Getter
abstract class SheetDataSupplier implements Supplier<List<List<String>>> {

    @NotNull
    private final List<ExcelCellDescriptor> titles;

    @NotNull
    private final List<ExportExcelDataFetcher<?>> fetchers;

    private final int fetchSize;

    private final SpringExpressionObjectToRowConverter rowConverter;

    protected SheetDataSupplier(@NotNull List<ExcelCellDescriptor> titles, @NotNull List<ExportExcelDataFetcher<?>> fetchers, int fetchSize) {
        this.titles = titles;
        this.fetchers = fetchers;
        this.fetchSize = fetchSize;
        this.rowConverter = SpringExpressionObjectToRowConverter.of(titles);
    }

    @Override
    public List<List<String>> get() {
        List<List<String>> result = new ArrayList<>();
        // titles
        List<String> titleRows = titles.stream()
                .map(ExcelCellDescriptor::getTitle)
                .filter(StringUtils::hasText)
                .toList();
        if (!titleRows.isEmpty()) {
            result.add(titleRows);
        }
        fetchers.forEach(fetcher -> {
            int queryPage = 1;
            while (true) {
                List<?> records = fetcher.fetch(queryPage, fetchSize);
                for (Object row : records) {
                    result.add(rowConverter.convert(row));
                }
                if (records.size() < fetchSize) {
                    break;
                }
                queryPage++;
            }
        });
        return result;
    }

    static SheetDataSupplierBuilder row() {
        return new SheetDataSupplierBuilder(true);
    }

    static SheetDataSupplierBuilder cel() {
        return new SheetDataSupplierBuilder(false);
    }


    static class SheetDataSupplierBuilder {

        private final boolean rowMode;

        private final List<ExcelCellDescriptor> titles = new ArrayList<>();

        private final List<ExportExcelDataFetcher<?>> fetchers = new ArrayList<>();

        SheetDataSupplierBuilder(boolean rowMode) {
            this.rowMode = rowMode;
        }

        SheetDataSupplierBuilder titles(List<ExcelCellDescriptor> titles) {
            this.titles.addAll(titles);
            return this;
        }


        SheetDataSupplierBuilder data(List<?> data) {
            return this.data(new SplitExcelDataFetcherWrapper(data));
        }

        <T> SheetDataSupplierBuilder data(ExportExcelDataFetcher<T> fetcher) {
            this.fetchers.removeIf(e -> !(e instanceof SplitExcelDataFetcherWrapper));
            this.fetchers.add(fetcher);
            return this;
        }

        SheetDataSupplier build(int fetchSize) {
            return rowMode ? new RowSupplier(titles, fetchers, fetchSize) : new CellSupplier(titles, fetchers, fetchSize);
        }
    }

    static class RowSupplier extends SheetDataSupplier {

        public RowSupplier(@NotNull List<ExcelCellDescriptor> titles, @NotNull List<ExportExcelDataFetcher<?>> fetchers, int fetchSize) {
            super(titles, fetchers, fetchSize);
        }
    }

    static class CellSupplier extends SheetDataSupplier {

        public CellSupplier(@NotNull List<ExcelCellDescriptor> titles, @NotNull List<ExportExcelDataFetcher<?>> fetchers, int fetchSize) {
            super(titles, fetchers, fetchSize);
        }
    }

    @AllArgsConstructor
    private static class SplitExcelDataFetcherWrapper implements ExportExcelDataFetcher<Object> {

        private final List<?> data;

        @Override
        @SuppressWarnings({"rawtypes", "unchecked"})
        public List fetch(int page, int size) {
            int fromIndex = (page - 1) * size;
            int toIndex = Math.min(page * size, data.size());
            return data.subList(fromIndex, toIndex);
        }
    }

}
