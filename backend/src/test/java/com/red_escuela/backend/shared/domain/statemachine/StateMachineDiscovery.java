package com.red_escuela.backend.shared.domain.statemachine;

import com.red_escuela.backend.shared.time.RawNowRule;
import com.tngtech.archunit.core.domain.JavaClass;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.TreeMap;

public final class StateMachineDiscovery {

    private StateMachineDiscovery() {
    }

    public static Map<String, StateTransitionTable<?, ?>> findTablesDeclaredInMain() {
        Map<String, StateTransitionTable<?, ?>> tables = new TreeMap<>();
        try {
            for (JavaClass javaClass : RawNowRule.importMainClasses()) {
                for (Field field : javaClass.reflect().getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) && field.getType() == StateTransitionTable.class) {
                        field.setAccessible(true);
                        tables.put(javaClass.getName(), (StateTransitionTable<?, ?>) field.get(null));
                    }
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        return tables;
    }
}
