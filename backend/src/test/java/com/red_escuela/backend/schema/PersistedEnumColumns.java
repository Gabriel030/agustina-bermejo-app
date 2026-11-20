package com.red_escuela.backend.schema;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.Value;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.type.SqlTypes;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.orm.jpa.hibernate.SpringBeanContainer;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.hibernate.SpringImplicitNamingStrategy;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

final class PersistedEnumColumns {

    private static final String BASE_PACKAGE = "com.red_escuela.backend";

    record EnumColumn(String table, String column, Class<?> enumType) {

        Set<String> values() {
            Set<String> names = new TreeSet<>();
            for (Object constant : enumType.getEnumConstants()) {
                names.add(((Enum<?>) constant).name());
            }
            return names;
        }
    }

    private PersistedEnumColumns() {
    }

    static List<EnumColumn> discover() {
        Metadata metadata = buildMetadata();
        List<EnumColumn> found = new ArrayList<>();
        for (PersistentClass entity : metadata.getEntityBindings()) {
            for (Property property : entity.getProperties()) {
                collect(property.getValue(), found);
            }
            collect(entity.getIdentifierProperty() == null ? null : entity.getIdentifierProperty().getValue(), found);
        }
        for (Collection collection : metadata.getCollectionBindings()) {
            collect(collection.getElement(), found);
        }
        return found.stream().distinct()
                .sorted(Comparator.comparing(EnumColumn::table).thenComparing(EnumColumn::column))
                .toList();
    }

    private static void collect(Value value, List<EnumColumn> found) {
        if (value == null) {
            return;
        }
        if (value instanceof org.hibernate.mapping.Component component) {
            component.getProperties().forEach(nested -> collect(nested.getValue(), found));
            return;
        }
        if (!(value instanceof BasicValue basic)) {
            return;
        }
        if (!basic.getType().getReturnedClass().isEnum()) {
            return;
        }
        boolean string = SqlTypes.isCharacterType(basic.resolve().getJdbcMapping().getJdbcType().getJdbcTypeCode());
        if (!string) {
            return;
        }
        for (var selectable : basic.getColumns()) {
            if (selectable instanceof Column column) {
                found.add(new EnumColumn(basic.getTable().getName(), column.getName(), basic.getType().getReturnedClass()));
            }
        }
    }

    private static boolean isMainClass(Class<?> type) {
        return !type.getProtectionDomain().getCodeSource().getLocation().getPath().contains("test-classes");
    }

    private static ConfigurableListableBeanFactory converterBeanFactory() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("objectMapper", new ObjectMapper());
        return beanFactory;
    }

    private static Metadata buildMetadata() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting(AvailableSettings.BEAN_CONTAINER, new SpringBeanContainer(converterBeanFactory()))
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                .applySetting("hibernate.physical_naming_strategy", CamelCaseToUnderscoresNamingStrategy.class.getName())
                .applySetting("hibernate.implicit_naming_strategy", SpringImplicitNamingStrategy.class.getName())
                .build();
        MetadataSources sources = new MetadataSources(registry);
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Embeddable.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(MappedSuperclass.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(jakarta.persistence.Converter.class));
        for (BeanDefinition definition : scanner.findCandidateComponents(BASE_PACKAGE)) {
            try {
                Class<?> type = Class.forName(definition.getBeanClassName());
                if (isMainClass(type)) {
                    sources.addAnnotatedClass(type);
                }
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
        return sources.buildMetadata();
    }
}
