package com.truu.hiring.web;


import com.truu.hiring.service.IdentityRequest.Status;
import com.truu.hiring.service.IdentityRequestManager;
import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

@Controller
public class WebSocketAuthorizeController {

  protected final ScheduledExecutorService executorService;
  private final SimpMessagingTemplate messagingTemplate;
  private final IdentityRequestManager requestManager;

  protected final Map<IdentityRequestSubscriptionKey, ScheduledFuture<?>> cancelableFutureStorage;

  public WebSocketAuthorizeController(ScheduledExecutorService scheduledExecutorService,
                                      SimpMessagingTemplate messagingTemplate,
                                      IdentityRequestManager requestManager) {
    this.executorService = scheduledExecutorService;
    this.messagingTemplate = messagingTemplate;
    this.requestManager = requestManager;
    this.cancelableFutureStorage = new ConcurrentHashMap<>();
  }

  @EventListener(
      classes = {SessionSubscribeEvent.class},
      condition =
          "event.message.headers.get(T(org.springframework.messaging.simp.SimpMessageHeaderAccessor).DESTINATION_HEADER)" +
              ".endsWith('/user/queue/requestResolved')"
  )
  public void handleSessionSubscribeEvent(SessionSubscribeEvent event) {
    SimpMessageHeaderAccessor simpMessage = StompHeaderAccessor.wrap(event.getMessage());

    try {
      String wsSessionId = simpMessage.getSessionId();
      String wsSubscriptionId = simpMessage.getSubscriptionId();
      String identityRequestId = simpMessage.getMessageHeaders().get("identityRequestId", String.class);
      Principal user = event.getUser();


      var eventSubscriptionKey = new IdentityRequestSubscriptionKey(wsSessionId, wsSubscriptionId, identityRequestId, user.getName());
      startToPublishResolutionEvents(eventSubscriptionKey);
    } catch (Exception e) {

    }
  }

  @EventListener(classes = {SessionUnsubscribeEvent.class})
  public void handleSessionUnSubscribeEvent(SessionUnsubscribeEvent event) {
    try {
      SimpMessageHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
      var toRemove = this.cancelableFutureStorage.keySet().stream().filter(subscriptionKey -> subscriptionKey.wsSubscriptionId.equals(accessor.getSubscriptionId()) && subscriptionKey.wsSessionId.equals(accessor.getSessionId())).collect(Collectors.toSet());

      for (var subscriptionKey : toRemove) {
        ScheduledFuture<?> remove = this.cancelableFutureStorage.remove(subscriptionKey);
        remove.cancel(true);

      }
    } catch (Exception e) {

    }
  }

  @EventListener(classes = {SessionDisconnectEvent.class})
  public void handleSessionDisconnectEvent(SessionDisconnectEvent event) {
    try {
      SimpMessageHeaderAccessor m = StompHeaderAccessor.wrap(event.getMessage());

      var toRemove = this.cancelableFutureStorage.keySet().stream().filter(subscriptionKey -> subscriptionKey.wsSessionId.equals(m.getSessionId()))
          .collect(Collectors.toSet());

      for (var subscriptionKey : toRemove) {
        ScheduledFuture<?> remove = this.cancelableFutureStorage.remove(subscriptionKey);
        remove.cancel(true);
      }
    } catch (Exception e) {
      String msg = "Could not release the resources while disconnect from ws session.";
      throw new IllegalStateException(msg, e);
    }
  }

  public void startToPublishResolutionEvents(IdentityRequestSubscriptionKey eventSubscriptionKey) {
    var identityRequestPublisher = prepareIdentityRequestPublisher(eventSubscriptionKey);
    ScheduledFuture<?> scheduledFuture = executorService.scheduleWithFixedDelay(
        identityRequestPublisher,
        1000,
        500,
        TimeUnit.MILLISECONDS
    );
    this.cancelableFutureStorage.put(eventSubscriptionKey, scheduledFuture);
  }

  public Runnable prepareIdentityRequestPublisher(IdentityRequestSubscriptionKey eventSubscriptionKey) {

    return () -> {
      String identityRequestId = eventSubscriptionKey.identityRequestId;
      String wsUserName = eventSubscriptionKey.wsUserName;

      var identityStatus = requestManager.getRequestState(identityRequestId);

      String upn = identityStatus.getUpn();
      Status status = identityStatus.getStatus();

      this.messagingTemplate.convertAndSendToUser(
          wsUserName,
          "queue/requestResolved",
          identityStatus,
          Map.of("identityRequestId", identityRequestId)
      );
      this.cancelableFutureStorage.remove(eventSubscriptionKey);
    };
  }

  protected static class IdentityRequestSubscriptionKey {

    final String wsSessionId;
    final String wsSubscriptionId;
    final String identityRequestId;
    final String wsUserName;

    public IdentityRequestSubscriptionKey(String wsSessionId,
                                          String wsSubscriptionId,
                                          String wsUserName,
                                          String identityRequestId) {
      this.wsSessionId = wsSessionId;
      this.wsSubscriptionId = wsSubscriptionId;
      this.identityRequestId = identityRequestId;
      this.wsUserName = wsUserName;
    }

    @Override
    public String toString() {
      return wsSessionId + ':' + wsSubscriptionId + ':' + wsUserName + ':' + identityRequestId;
    }
  }
}
