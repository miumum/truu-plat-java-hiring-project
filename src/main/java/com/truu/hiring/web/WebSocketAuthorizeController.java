package com.truu.hiring.web;


import com.truu.hiring.service.IdentityRequest.Status;
import com.truu.hiring.service.IdentityRequestManager;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  public static final String RESOLVED_REQUEST_QUEUE = "/queue" + "/requestResolved";

  protected final ScheduledExecutorService scheduledExecutorService;
  private final SimpMessagingTemplate wsMessagingTemplate;
  private final IdentityRequestManager requestManager;

  protected final CancelableFutureStorage<IdentityRequestSubscriptionKey> listeningIdentityRequests;

  public WebSocketAuthorizeController(ScheduledExecutorService scheduledExecutorService,
                                      SimpMessagingTemplate wsMessagingTemplate,
                                      IdentityRequestManager requestManager) {
    this.scheduledExecutorService = scheduledExecutorService;
    this.wsMessagingTemplate = wsMessagingTemplate;
    this.requestManager = requestManager;
    this.listeningIdentityRequests = new CancelableFutureStorage<>();
  }

  @EventListener(
      classes = {SessionSubscribeEvent.class},
      condition =
          "event.message.headers.get(T(org.springframework.messaging.simp.SimpMessageHeaderAccessor).DESTINATION_HEADER)" +
              ".endsWith('/user" + RESOLVED_REQUEST_QUEUE + "')"
  )
  public void handleSessionSubscribeEvent(SessionSubscribeEvent event) {
    SimpMessageHeaderAccessor simpMessage = StompHeaderAccessor.wrap(event.getMessage());

    try {
      String wsSessionId = simpMessage.getSessionId();
      String wsSubscriptionId = simpMessage.getSubscriptionId();
      String identityRequestId = simpMessage.getMessageHeaders().get("identityRequestId", String.class);
      Principal user = event.getUser();


      var eventSubscriptionKey = new IdentityRequestSubscriptionKey(
          wsSessionId,
          wsSubscriptionId,
          identityRequestId,
          user.getName()
      );
      startToPublishResolutionEvents(eventSubscriptionKey);
    } catch (Exception e) {
      String msg = "Could not subscribe to " + RESOLVED_REQUEST_QUEUE;
      throw new IllegalStateException(msg, e);
    }
  }

  @EventListener(classes = {SessionUnsubscribeEvent.class})
  public void handleSessionUnSubscribeEvent(SessionUnsubscribeEvent event) {
    try {
      SimpMessageHeaderAccessor simpMessage = StompHeaderAccessor.wrap(event.getMessage());
      String wsSessionId = simpMessage.getSessionId();
      String wsSubscriptionId = simpMessage.getSubscriptionId();

      var toRemove = listeningIdentityRequests.keySet().stream()
          .filter(subscriptionKey ->
              subscriptionKey.wsSubscriptionId.equals(wsSubscriptionId) &&
                  subscriptionKey.wsSessionId.equals(wsSessionId))
          .collect(Collectors.toSet());

      for (var subscriptionKey : toRemove) {
        listeningIdentityRequests.remove(subscriptionKey);
      }
    } catch (Exception e) {
      String msg = "Could not release the resources while unsubscribe from " + RESOLVED_REQUEST_QUEUE;
      throw new IllegalStateException(msg, e);
    }
  }

  @EventListener(classes = {SessionDisconnectEvent.class})
  public void handleSessionDisconnectEvent(SessionDisconnectEvent event) {
    try {
      SimpMessageHeaderAccessor simpMessage = StompHeaderAccessor.wrap(event.getMessage());
      String wsSessionId = simpMessage.getSessionId();

      var toRemove = listeningIdentityRequests.keySet().stream()
          .filter(subscriptionKey -> subscriptionKey.wsSessionId.equals(wsSessionId))
          .collect(Collectors.toSet());

      for (var subscriptionKey : toRemove) {
        this.listeningIdentityRequests.remove(subscriptionKey);
      }
    } catch (Exception e) {
      String msg = "Could not release the resources while disconnect from ws session.";
      throw new IllegalStateException(msg, e);
    }
  }

  private void startToPublishResolutionEvents(IdentityRequestSubscriptionKey eventSubscriptionKey) {

    var identityRequestPublisher = prepareIdentityRequestPublisher(eventSubscriptionKey);
    ScheduledFuture<?> scheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(
        identityRequestPublisher,
        1000,
        500,
        TimeUnit.MILLISECONDS
    );
    listeningIdentityRequests.put(eventSubscriptionKey, scheduledFuture);
  }

  private Runnable prepareIdentityRequestPublisher(IdentityRequestSubscriptionKey eventSubscriptionKey) {

    return () -> {
      String identityRequestId = eventSubscriptionKey.identityRequestId;
      String wsUserName = eventSubscriptionKey.wsUserName;

      var identityStatus = requestManager.getRequestState(identityRequestId);

      String upn = identityStatus.getUpn();
      Status status = identityStatus.getStatus();

      wsMessagingTemplate.convertAndSendToUser(
          wsUserName,
          RESOLVED_REQUEST_QUEUE,
          identityStatus,
          Map.of("identityRequestId", identityRequestId)
      );
      listeningIdentityRequests.remove(eventSubscriptionKey);
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
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      IdentityRequestSubscriptionKey that = (IdentityRequestSubscriptionKey) o;
      return Objects.equals(wsSessionId, that.wsSessionId) &&
          Objects.equals(wsSubscriptionId, that.wsSubscriptionId) &&
          Objects.equals(wsUserName, that.wsUserName) &&
          Objects.equals(identityRequestId, that.identityRequestId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(wsSessionId, wsSubscriptionId, wsUserName, identityRequestId);
    }

    @Override
    public String toString() {
      return wsSessionId + ':' + wsSubscriptionId + ':' + wsUserName + ':' + identityRequestId;
    }
  }

  protected static class CancelableFutureStorage<KEY> extends ConcurrentHashMap<KEY, ScheduledFuture<?>> {

    @Override
    public ScheduledFuture<?> remove(Object key) {
      return remove(key, true);
    }

    public ScheduledFuture<?> remove(Object key, boolean mayInterruptIfRunning) {
      var removed = super.get(key);
      if (removed != null) {
        if (removed.isDone()) {
          return super.remove(key);
        } else {
          boolean canceled = removed.cancel(mayInterruptIfRunning);
          if (canceled) {
            return super.remove(key);
          } else {
            return null;
          }
        }
      } else {
        return null;
      }
    }

    @Override
    public ScheduledFuture<?> replace(KEY key, ScheduledFuture<?> value) {
      var removed = super.replace(key, value);
      if (removed != null) {
        removed.cancel(true);
      }
      return removed;
    }
  }
}
