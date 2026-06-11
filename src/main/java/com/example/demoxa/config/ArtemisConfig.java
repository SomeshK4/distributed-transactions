package com.example.demoxa.config;

import com.atomikos.jms.AtomikosConnectionFactoryBean;
import jakarta.jms.ConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQXAConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.transaction.jta.JtaTransactionManager;

/**
 * Wraps the Artemis {@link ActiveMQXAConnectionFactory} in an Atomikos XA
 * pool so that every JMS send/receive enlists in the active JTA transaction
 * managed by {@link JtaConfig}.
 *
 * <p>Both producer ({@link JmsTemplate}) and consumer
 * ({@link DefaultJmsListenerContainerFactory}) use {@code sessionTransacted=true}
 * + the {@link JtaTransactionManager}, so message processing is part of the
 * same global XA transaction as JDBC work.
 */
@Configuration
public class ArtemisConfig {


    @Value("${spring.artemis.broker-url}")
    private String brokerUrl;

    @Value("${spring.artemis.user}")
    private String user;

    @Value("${spring.artemis.password}")
    private String password;

    @Value("${jms.provider}")
    private String jmsProvider;

    @Bean(initMethod = "init", destroyMethod = "close")
    @DependsOn("atomikosTransactionManager")
    @Primary
    public ConnectionFactory connectionFactory() {

        if("artemis".equals(jmsProvider)){
            ActiveMQXAConnectionFactory xaFactory =
                    new ActiveMQXAConnectionFactory(
                            brokerUrl, user, password);

            AtomikosConnectionFactoryBean bean = new AtomikosConnectionFactoryBean();
            bean.setUniqueResourceName("artemis-xa");
            bean.setXaConnectionFactory(xaFactory);
            bean.setMaxPoolSize(10);
            return bean;
        }
        throw new IllegalStateException("Unsupported JMS provider: " + jmsProvider);

    }

    @Bean
    public JmsTemplate jmsTemplate(ConnectionFactory connectionFactory) {
        JmsTemplate template = new JmsTemplate(connectionFactory);
        // Session must be transacted to join the JTA branch.
        template.setSessionTransacted(true);
        return template;
    }

    /**
     * Listener container factory used by {@code @JmsListener}. By passing
     * the {@link JtaTransactionManager}, every inbound message is consumed
     * inside the same XA transaction the handler enlists in.
     */
    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(
            ConnectionFactory connectionFactory,
            JtaTransactionManager transactionManager) {

        DefaultJmsListenerContainerFactory factory =
                new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setTransactionManager(transactionManager);
        factory.setSessionTransacted(true);
        factory.setConcurrency("1-3");
        return factory;
    }
}

