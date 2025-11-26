package com.wind.office.excel.read;

import com.wind.common.exception.AssertUtils;
import com.wind.common.exception.BaseException;
import com.wind.common.exception.DefaultExceptionCode;
import com.wind.common.util.WindReflectUtils;
import com.wind.office.excel.ExcelCellQuickBuilder;
import com.wind.office.excel.convert.ExcelRowToObjectConverter;
import com.wind.office.excel.metadata.ExcelCellDescriptor;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.format.Parser;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.text.ParseException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 基于类字段名称的行数据 convert
 *
 * @author wuxp
 * @date 2025-10-15 09:21
 **/
public record BasedFieldNameRowToObjectConverter<T>(Class<T> objectType, List<ExcelCellDescriptor> cellDescriptors, ConversionService conversionService)
        implements ExcelRowToObjectConverter<T> {

    public BasedFieldNameRowToObjectConverter(Class<T> objectType, List<ExcelCellDescriptor> cellDescriptors) {
        this(objectType, cellDescriptors, DefaultConversionService.getSharedInstance());
    }

    public static <T> BasedFieldNameRowToObjectConverter<T> of(Class<T> objectType, List<ExcelCellDescriptor> cellDescriptors) {
        return new BasedFieldNameRowToObjectConverter<>(objectType, cellDescriptors);
    }

    public static <T> BasedFieldNameRowToObjectConverter<T> of(Class<T> objectType) {
        return of(objectType, ExcelCellQuickBuilder.forClass(objectType));
    }

    @Override
    public T convert(@NonNull List<String> row) {
        return WindReflectUtils.newInstance(objectType, parseFieldValues(row));
    }

    private Map<String, Object> parseFieldValues(List<String> data) {
        Map<String, @NotNull Field> fields = Arrays.stream(WindReflectUtils.getFields(objectType)).collect(Collectors.toMap(Field::getName, Function.identity()));
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < data.size(); i++) {
            ExcelCellDescriptor descriptor = cellDescriptors.get(i);
            String text = data.get(i);
            if (StringUtils.hasText(text)) {
                Field field = fields.get(descriptor.getExpression());
                AssertUtils.notNull(field, "field not found, fieldName = {}", descriptor.getExpression());
                result.put(descriptor.getExpression(), parserValue(text, field.getType(), descriptor.getParser()));
            }
        }
        return result;
    }

    private Object parserValue(String value, Class<?> valueType, Parser<Object> parser) {
        if (parser == null) {
            return conversionService.convert(value, valueType);
        }
        try {
            return parser.parse(value, Locale.getDefault());
        } catch (ParseException e) {
            throw new BaseException(DefaultExceptionCode.COMMON_FRIENDLY_ERROR, "parse value error, value = {}", e);
        }
    }
}
