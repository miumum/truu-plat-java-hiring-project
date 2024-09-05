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

  protected final ScheduledExecutorService s;
  private final SimpMessagingTemplate t;
  private final IdentityRequestManager m;

  protected final CancelableFutureStorage<IdentityRequestSubscriptionKey> map;

  public WebSocketAuthorizeController(ScheduledExecutorService scheduledExecutorService, SimpMessagingTemplate t, IdentityRequestManager requestManager) {
    this.s = scheduledExecutorService;
    this.t = t;
    this.m = requestManager;
    this.map = new CancelableFutureStorage<>();
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
      String msg = "Could not subscribe to queue/requestResolved";
      throw new IllegalStateException(msg, e);
    }
  }

  @EventListener(classes = {SessionUnsubscribeEvent.class})
  public void handleSessionUnSubscribeEvent(SessionUnsubscribeEvent event) {
    try {
      SimpMessageHeaderAccessor m3 = StompHeaderAccessor.wrap(event.getMessage());
      var toRemove = map.keySet().stream().filter(subscriptionKey -> subscriptionKey.wsSubscriptionId.equals(m3.getSubscriptionId()) && subscriptionKey.wsSessionId.equals(m3.getSessionId())).collect(Collectors.toSet());

      for (var subscriptionKey : toRemove) {
        map.remove(subscriptionKey);
      }
    } catch (Exception e) {
      String msg = "Could not release the resources while unsubscribe from queue/requestResolved";
      throw new IllegalStateException(msg, e);
    }
  }

  @EventListener(classes = {SessionDisconnectEvent.class})
  public void handleSessionDisconnectEvent(SessionDisconnectEvent event) {
    try {
      SimpMessageHeaderAccessor m = StompHeaderAccessor.wrap(event.getMessage());

      var toRemove = map.keySet().stream().filter(subscriptionKey -> subscriptionKey.wsSessionId.equals(m.getSessionId()))
          .collect(Collectors.toSet());

      for (var subscriptionKey : toRemove) {
        this.map.remove(subscriptionKey);
      }
    } catch (Exception e) {
      String msg = "Could not release the resources while disconnect from ws session.";
      throw new IllegalStateException(msg, e);
    }
  }

  public void startToPublishResolutionEvents(IdentityRequestSubscriptionKey eventSubscriptionKey) {
    var identityRequestPublisher = prepareIdentityRequestPublisher(eventSubscriptionKey);
    ScheduledFuture<?> scheduledFuture = s.scheduleWithFixedDelay(
        identityRequestPublisher,
        1000,
        500,
        TimeUnit.MILLISECONDS
    );
    map.put(eventSubscriptionKey, scheduledFuture);
  }

  public Runnable prepareIdentityRequestPublisher(IdentityRequestSubscriptionKey eventSubscriptionKey) {

    return () -> {
      String identityRequestId = eventSubscriptionKey.identityRequestId;
      String wsUserName = eventSubscriptionKey.wsUserName;

      var identityStatus = m.getRequestState(identityRequestId);

      String upn = identityStatus.getUpn();
      Status status = identityStatus.getStatus();

      t.convertAndSendToUser(
          wsUserName,
          "queue/requestResolved",
          identityStatus,
          Map.of("identityRequestId", identityRequestId)
      );
      map.remove(eventSubscriptionKey);
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

  protected static class CancelableFutureStorage<X> extends ConcurrentHashMap<X, ScheduledFuture<?>> {

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
    public ScheduledFuture<?> replace(X x, ScheduledFuture<?> value) {
      var removed = super.replace(x, value);
      if (removed != null) {
        removed.cancel(true);
      }
      return removed;
    }
  }
}
