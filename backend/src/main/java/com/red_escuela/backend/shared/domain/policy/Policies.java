package com.red_escuela.backend.shared.domain.policy;

import java.util.List;

public final class Policies {

    private Policies() {
    }

    @SafeVarargs
    public static <C> Policy<C> allOf(String name, Policy<C>... policies) {
        List<Policy<C>> list = List.of(policies);
        return new Policy<>() {
            public String name() {
                return name;
            }

            public PolicyVerdict evaluate(C context) {
                for (Policy<C> policy : list) {
                    PolicyVerdict verdict = policy.evaluate(context);
                    if (!verdict.allowed()) {
                        return verdict;
                    }
                }
                return PolicyVerdict.allow(name);
            }
        };
    }

    @SafeVarargs
    public static <C> Policy<C> anyOf(String name, Policy<C>... policies) {
        List<Policy<C>> list = List.of(policies);
        return new Policy<>() {
            public String name() {
                return name;
            }

            public PolicyVerdict evaluate(C context) {
                PolicyVerdict last = null;
                for (Policy<C> policy : list) {
                    PolicyVerdict verdict = policy.evaluate(context);
                    if (verdict.allowed()) {
                        return PolicyVerdict.allow(name);
                    }
                    last = verdict;
                }
                return last == null ? PolicyVerdict.allow(name) : PolicyVerdict.deny(name, last.code(), last.reason());
            }
        };
    }

    public static <C> Policy<C> not(String name, String code, String reason, Policy<C> policy) {
        return new Policy<>() {
            public String name() {
                return name;
            }

            public PolicyVerdict evaluate(C context) {
                return policy.evaluate(context).allowed() ? PolicyVerdict.deny(name, code, reason) : PolicyVerdict.allow(name);
            }
        };
    }
}
