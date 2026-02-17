package com.mekom.eip.dhis2;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.jms.ConnectionFactory;

/**
 * Configuration for Apache ActiveMQ Artemis JMS connectivity.
 * Sets up the connection factory for Camel JMS routes and Dead Letter Queue.
 */
@Configuration
public class ArtemisConfiguration {
    
    private static final Logger log = LoggerFactory.getLogger(ArtemisConfiguration.class);
    
    @Value("${artemis.broker.url:tcp://artemis:61616}")
    private String brokerUrl;
    
    @Value("${artemis.username:artemis}")
    private String username;
    
    @Value("${artemis.password:artemis}")
    private String password;
    
    @Value("${artemis.pool-size:10}")
    private Integer poolSize;
    
    /**
     * Create and configure the Artemis connection factory bean.
     * This factory is used by Camel JMS routes for Dead Letter Queue operations.
     * 
     * @return Configured ActiveMQConnectionFactory
     */
    @Bean(name = "connectionFactory")
    public ConnectionFactory connectionFactory() {
        log.info("Configuring ActiveMQ Artemis connection factory");
        log.info("Broker URL: {}", brokerUrl);
        log.info("Username: {}", username);
        log.info("Pool size: {}", poolSize);
        
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
        factory.setUser(username);
        factory.setPassword(password);
        factory.setMaxRetryInterval(5000);
        factory.setRetryInterval(1000);
        factory.setReconnectAttempts(-1); // Retry indefinitely
        
        log.info("ActiveMQ Artemis connection factory configured successfully");
        return factory;
    }
}
