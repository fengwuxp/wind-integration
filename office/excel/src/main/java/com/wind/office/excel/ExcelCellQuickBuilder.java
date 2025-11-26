package com.wind.office.excel;

import com.wind.common.WindConstants;
import com.wind.common.WindDateFormatPatterns;
import com.wind.common.enums.DescriptiveEnum;
import com.wind.common.exception.BaseException;
import com.wind.common.util.WindReflectUtils;
import com.wind.office.core.formatter.DefaultFormatterFactory;
import com.wind.office.excel.metadata.ExcelCellDescriptor;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.Printer;
import org.jspecify.annotations.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 通过类类型快速构建 ExcelCellDescriptor 集合
 *
 * @author wuxp
 * @date 2025-01-14 13:25
 **/
public final class ExcelCellQuickBuilder {

    private static final AtomicReference<Function<AnnotatedElement, String>> EXCEL_TITLE_PARSE =
            new AtomicReference<>(new Swagger3ExcelTitleParser());

    private static final AtomicReference<BiFunction<String, Member, Printer<?>>> EXCEL_CELL_PRINTER = new AtomicReference<>((name, member) -> null);

    private ExcelCellQuickBuilder() {
        throw new AssertionError();
    }

    @NotNull
    public static List<ExcelCellDescriptor> forClass(@NotNull Class<?> clazz) {
        return forClass(clazz, null);
    }

    @NotNull
    public static List<ExcelCellDescriptor> forClass(@NotNull Class<?> clazz, @Nullable List<String> orderedFields) {
        Map<String, Field> fields = Arrays.stream(WindReflectUtils.getFields(clazz))
                .collect(Collectors.toMap(Field::getName, Function.identity()));
        Map<String, Method> getterMethods = Arrays.stream(WindReflectUtils.getGetterMethods(clazz))
                .collect(Collectors.toMap(ExcelCellQuickBuilder::convertGetMethodNameToFieldName, Function.identity()));
        return getOrderedFields(clazz, orderedFields)
                .stream()
                .map(filedName -> {
                    Field field = fields.get(filedName);
                    if (field != null) {
                        return buildExcelCellDescriptor(field);
                    }
                    Method method = getterMethods.get(filedName);
                    if (method != null) {
                        return buildExcelCellDescriptor(method);
                    }
                    throw BaseException.common("not found name = " + filedName + " field");
                })
                .toList();
    }

    private static ExcelCellDescriptor buildExcelCellDescriptor(Field field) {
        return ExcelCellDescriptor
                .builder(EXCEL_TITLE_PARSE.get().apply(field), field.getName())
                .printer(ofPrinter(field.getName(), field))
                .build();
    }

    private static ExcelCellDescriptor buildExcelCellDescriptor(Method method) {
        String fieldName = convertGetMethodNameToFieldName(method);
        return ExcelCellDescriptor
                .builder(EXCEL_TITLE_PARSE.get().apply(method), fieldName)
                .printer(ofPrinter(fieldName, method))
                .build();
    }

    private static List<String> getOrderedFields(@NotNull Class<?> clazz, @Nullable List<String> orderedFields) {
        if (CollectionUtils.isEmpty(orderedFields)) {
            Method[] getterMethods = WindReflectUtils.getGetterMethods(clazz);
            List<String> result = Arrays.stream(WindReflectUtils.getFields(clazz)).map(Field::getName).collect(Collectors.toList());
            result.addAll(Arrays.stream(getterMethods)
                    .map(ExcelCellQuickBuilder::convertGetMethodNameToFieldName)
                    .filter(name -> !result.contains(name))
                    .toList());
            return result;
        }
        return orderedFields;
    }

    private static String convertGetMethodNameToFieldName(Method method) {
        String methodName = method.getName();
        if (methodName.startsWith("get")) {
            methodName = methodName.substring(3);
        } else {
            methodName = methodName.substring(2);
        }
        return methodName.substring(0, 1).toLowerCase() + methodName.substring(1);
    }

    private static Printer<?> ofPrinter(String name, Member member) {
        Printer<?> printer = EXCEL_CELL_PRINTER.get().apply(name, member);
        if (member instanceof Field field) {
            return printer == null ? createDefaultPrinterByClass(field.getType()) : printer;
        }
        if (member instanceof Method method) {
            return printer == null ? createDefaultPrinterByClass(method.getReturnType()) : printer;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Printer<?> createDefaultPrinterByClass(Class<?> clazz) {
        if (clazz.isAssignableFrom(DescriptiveEnum.class)) {
            return DefaultFormatterFactory.ofEnum((Class<? extends DescriptiveEnum>) clazz);
        }
        if (Objects.equals(clazz, Boolean.class) || Objects.equals(clazz, boolean.class)) {
            return DefaultFormatterFactory.ofBool("是", "否");
        }
        if (Objects.equals(clazz, Date.class) || Objects.equals(clazz, LocalDateTime.class)) {
            return DefaultFormatterFactory.ofDateTime(WindDateFormatPatterns.YYYY_MM_DD_HH_MM_SS);
        }
        if (Objects.equals(clazz, LocalDate.class)) {
            return DefaultFormatterFactory.ofDateTime(WindDateFormatPatterns.YYYY_MM_DD);
        }
        if (clazz.isArray()) {
            return DefaultFormatterFactory.ofArray();
        }
        if (clazz.isAssignableFrom(Collection.class)) {
            return DefaultFormatterFactory.ofCollection();
        }
        return null;
    }

    private static class Swagger3ExcelTitleParser implements Function<AnnotatedElement, String> {

        @Override
        public String apply(AnnotatedElement annotatedElement) {
            Schema annotation = annotatedElement.getAnnotation(Schema.class);
            if (annotation == null) {
                return switch (annotatedElement) {
                    case Field field -> field.getName();
                    case Method method -> convertGetMethodNameToFieldName(method);
                    default -> WindConstants.EMPTY;
                };
            }
            String result = annotation.description();
            return StringUtils.hasText(result) ? result : annotation.name();
        }
    }

    public static void configureParser(Function<AnnotatedElement, String> parser) {
        EXCEL_TITLE_PARSE.set(parser);
    }

    public static void configurePrinter(BiFunction<String, Member, Printer<?>> printer) {
        EXCEL_CELL_PRINTER.set(printer);
    }
}
