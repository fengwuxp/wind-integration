package com.wind.office.excel.metadata;

import com.wind.common.exception.BaseException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.format.Printer;

import java.util.Locale;

/**
 * excel cell printer
 * 传入了当前行的数据，以满足更复杂的场景
 *
 * @author wuxp
 * @date 2025-05-14 14:06
 **/
public interface ExcelCellPrinter<T> extends Printer<T> {

    @Override
    default @NotNull String print(@Nullable T object, @NotNull Locale locale) {
        throw new BaseException("unsupported, please use: String print(T cellValue, String expression, Object row, Locale locale)");
    }

    /**
     * Print the object of type T for display.
     *
     * @param cellValue  excel cell data
     * @param expression getter cell value expression
     * @param row        excel row data
     * @param locale     the current user locale
     * @return the printed text string
     */
    String print(@Nullable T cellValue, @NotNull String expression, @NotNull Object row, @NotNull Locale locale);
}
