package com.wind.office.excel.export;

import com.wind.common.WindConstants;
import com.wind.office.excel.metadata.ExcelCellDescriptor;
import com.wind.office.excel.metadata.ExcelCellPrinter;
import com.wind.script.spring.SpringExpressionEvaluator;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.format.Printer;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Spring Expression 数据格式化
 *
 * @author wuxp
 * @date 2025-07-28 20:34
 **/
@AllArgsConstructor
public class SpringExpressionRowDataFormatter {

    @Getter
    private final List<ExcelCellDescriptor> cellDescriptors;

    public static SpringExpressionRowDataFormatter of(List<ExcelCellDescriptor> cellDescriptors) {
        return new SpringExpressionRowDataFormatter(cellDescriptors);
    }

    public List<String> formatRows(Object row) {
        List<String> result = new ArrayList<>();
        if (row instanceof Collection<?>) {
            // 集合
            return ((Collection<?>) row).stream().map(String::valueOf).collect(Collectors.toList());
        } else {
            EvaluationContext context = new StandardEvaluationContext(row);
            for (ExcelCellDescriptor descriptor : cellDescriptors) {
                String expression = descriptor.getExpression();
                Object cellValue = StringUtils.hasText(expression) ? SpringExpressionEvaluator.DEFAULT.eval(expression, context) : row;
                result.add(formatCellValue(descriptor, row, cellValue));
            }
            return result;
        }
    }

    private String formatCellValue(ExcelCellDescriptor descriptor, Object row, Object cellValue) {
        if (cellValue == null) {
            return WindConstants.EMPTY;
        }
        Printer<Object> printer = descriptor.getPrinter();
        if (printer instanceof ExcelCellPrinter) {
            return ((ExcelCellPrinter<Object>) printer).print(cellValue, descriptor.getExpression(), row, Locale.getDefault());
        }
        return printer.print(cellValue, Locale.getDefault());
    }
}
