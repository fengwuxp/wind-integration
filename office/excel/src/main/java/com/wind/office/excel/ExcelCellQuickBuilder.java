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
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.format.Printer;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 通过类类型快速构建 ExcelCellDescriptor 集合
 *
 * @author wuxp
 * @date 2025-01-14 13:25
 **/
public final class ExcelCellQuickBuilder {

    private static final AtomicReference<Function<AnnotatedElement, String>> EXCEL_TITLE_PARSE = new AtomicReference<>(new Swagger3ExcelTitleParser());

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
        ExcelCellBuilder builder = with(clazz);
        getOrderedFields(clazz, orderedFields).forEach(builder::cell);
        return builder.build();
    }

    /**
     * 通过类类型快速构建 ExcelCellDescriptor 集合
     *
     * @param clazz 类类型
     * @return ExcelCellDescriptor 集合
     */
    public static ExcelCellBuilder with(Class<?> clazz) {
        return new ExcelCellBuilder(clazz);
    }

    private static List<String> getOrderedFields(@NotNull Class<?> clazz, @Nullable List<String> orderedFields) {
        if (CollectionUtils.isEmpty(orderedFields)) {
            Method[] getterMethods = WindReflectUtils.getGetterMethods(clazz);
            List<String> result = Arrays.stream(WindReflectUtils.getFields(clazz)).map(Field::getName).collect(Collectors.toList());
            result.addAll(Arrays.stream(getterMethods).map(ExcelCellQuickBuilder::convertGetMethodNameToFieldName).filter(name -> !result.contains(name)).toList());
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

    @Nullable
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
    @Nullable
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

    /**
     * Excel Cell 构建器
     */
    public static class ExcelCellBuilder {

        private final Map<String, Field> fields;

        private final Map<String, Method> getterMethods;

        private final List<ExcelCellDescriptor> cells;

        public ExcelCellBuilder(Class<?> targetClass) {
            this.fields = Arrays.stream(WindReflectUtils.getFields(targetClass)).collect(Collectors.toMap(Field::getName, Function.identity()));
            this.getterMethods = Arrays.stream(WindReflectUtils.getGetterMethods(targetClass)).collect(Collectors.toMap(ExcelCellQuickBuilder::convertGetMethodNameToFieldName,
                    Function.identity()));
            this.cells = new ArrayList<>();
        }

        /**
         * 添加一个字段
         *
         * @param title      标题
         * @param fieldName  字段名称
         * @param customizer 自定义器
         * @return ExcelCellBuilder
         */
        public ExcelCellBuilder cell(@NonNull String title, @NonNull String fieldName, @NonNull Consumer<ExcelCellDescriptor.ExcelCellDescriptorBuilder> customizer) {
            ExcelCellDescriptor.ExcelCellDescriptorBuilder builder = create(title, fieldName);
            customizer.accept(builder);
            return this;
        }

        /**
         * 添加一个字段
         *
         * @param title     标题
         * @param fieldName 字段名称
         * @return ExcelCellBuilder
         */
        public ExcelCellBuilder cell(@NonNull String title, @NonNull String fieldName) {
            this.cells.add(create(title, fieldName).build());
            return this;
        }

        /**
         * 添加一个字段
         *
         * @param fieldName 字段名称
         * @return ExcelCellBuilder
         */
        public ExcelCellBuilder cell(@NonNull String fieldName) {
            this.cells.add(create(null, fieldName).build());
            return this;
        }

        public List<ExcelCellDescriptor> build() {
            return this.cells;
        }

        @NotNull
        private ExcelCellDescriptor.ExcelCellDescriptorBuilder create(@Nullable String title, @NonNull String fieldName) {
            Field field = fields.get(fieldName);
            if (field != null) {
                return buildExcelCellDescriptor(field, title);
            }
            Method method = getterMethods.get(fieldName);
            if (method != null) {
                return buildExcelCellDescriptor(method, title);
            }
            throw BaseException.common("not found name = " + fieldName + " field");
        }

        private ExcelCellDescriptor.ExcelCellDescriptorBuilder buildExcelCellDescriptor(Field field, @Nullable String title) {
            return ExcelCellDescriptor.builder(title == null ? EXCEL_TITLE_PARSE.get().apply(field) : title, field.getName()).printer(ofPrinter(field.getName(), field));
        }

        private ExcelCellDescriptor.ExcelCellDescriptorBuilder buildExcelCellDescriptor(Method method, @Nullable String title) {
            String fieldName = convertGetMethodNameToFieldName(method);
            return ExcelCellDescriptor.builder(title == null ? EXCEL_TITLE_PARSE.get().apply(method) : title, fieldName).printer(ofPrinter(fieldName, method));
        }
    }
}
