package com.red_escuela.backend.shared.mail;

import com.red_escuela.backend.shared.resilience.BulkheadFactory;
import com.red_escuela.backend.shared.resilience.CircuitBreakerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class MailCircuitBeanPostProcessor implements BeanPostProcessor {
    private final ObjectProvider<CircuitBreakerFactory> circuitBreakerFactory;
    private final ObjectProvider<BulkheadFactory> bulkheadFactory;

    public MailCircuitBeanPostProcessor(ObjectProvider<CircuitBreakerFactory> circuitBreakerFactory,
                                        ObjectProvider<BulkheadFactory> bulkheadFactory) {
        this.circuitBreakerFactory = circuitBreakerFactory;
        this.bulkheadFactory = bulkheadFactory;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof JavaMailSender sender && !(bean instanceof CircuitBreakingMailSender)) {
            return new CircuitBreakingMailSender(sender, circuitBreakerFactory.getObject().breakerNamed(MailCircuit.NAME),
                    bulkheadFactory.getObject().bulkheadNamed(MailCircuit.NAME));
        }
        return bean;
    }
}
