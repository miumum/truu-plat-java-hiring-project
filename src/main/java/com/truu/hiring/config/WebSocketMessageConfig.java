package com.truu.hiring.config;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.converter.GsonMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.config.annotation.web.messaging.MessageSecurityMetadataSourceRegistry;
import org.springframework.security.config.annotation.web.socket.AbstractSecurityWebSocketMessageBrokerConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.messaging.AbstractSubProtocolEvent;

/**
 * Configures the web socket handling
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketMessageConfig extends AbstractSecurityWebSocketMessageBrokerConfigurer {

  public static final int SEND_TIME_LIMIT = 10 * 1000; // 10sec
  private static final int MAX_MESSAGE_SIZE = 1024 * 128; // 128Kb
  // BUFFER has reserved memory and significantly increase memory footprint event we don't fully utilize it.
  private static final int MAX_SEND_BUFFER = 1024 * 1024; // 1Mb

  @Autowired
  private TaskScheduler taskScheduler;

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry
        .setApplicationDestinationPrefixes("/sso")
        .setUserDestinationPrefix("/user");
    registry
        .enableSimpleBroker("/queue", "/topic")
        .setTaskScheduler(taskScheduler);
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/msb").setAllowedOrigins("*");
    registry.addEndpoint("/sso")
        .setAllowedOriginPatterns(
            "http://localhost:[*]",
            "http://*.localhost:[*]"
        )
        .withSockJS();
  }

  @Override
  public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
    registry
        .setMessageSizeLimit(MAX_MESSAGE_SIZE)
        .setSendBufferSizeLimit(MAX_SEND_BUFFER)
        .setSendTimeLimit(SEND_TIME_LIMIT);
  }

  @Override
  public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
    messageConverters.add(new GsonMessageConverter());
    return false;
  }

  @Override
  protected boolean sameOriginDisabled() {
    return true;
  }

}
