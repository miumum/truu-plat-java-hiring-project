package com.truu.hiring.config;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;

/**
 * This class is the spring configuration class for the application.
 **/
@Configuration
@EnableWebSocketMessageBroker
public class ApplicationConfig {

  @Bean
  public ScheduledExecutorService scheduledExecutorService() {
    CustomizableThreadFactory threadFactory = new CustomizableThreadFactory("identity-request-status-checking-");
    ScheduledThreadPoolExecutor executorService = new ScheduledThreadPoolExecutor(1, threadFactory);
    executorService.setRemoveOnCancelPolicy(true);
    return executorService;
  }
}
