package com.truu.hiring.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Component
public class IdentityRequestManager {

  private static final Duration EXPIRES_AFTER = Duration.ofMinutes(10);
  private static final Duration DELETE_AFTER = Duration.ofMinutes(60);

  private Map<String, IdentityRequest> identityRequests = new HashMap<>();

  public IdentityRequest createRequest() {
    IdentityRequest identityRequest = new IdentityRequest(UUID.randomUUID().toString());
    identityRequests.put(identityRequest.getId(), identityRequest);
    return identityRequest;
  }

  public boolean completeRequest(String requestId, String upn, boolean shouldDelete) {
    IdentityRequest identityRequest = identityRequests.get(requestId);
    if (identityRequest != null) {
      identityRequest.setStatus(IdentityRequest.Status.COMPLETE);
      identityRequest.setUpn(upn);
      return true;
    }
    return false;
  }

  public boolean rejectRequest(String requestId, String upn) {
    IdentityRequest identityRequest = identityRequests.get(requestId);
    if (identityRequest != null) {
      identityRequest.setStatus(IdentityRequest.Status.REJECTED);
      identityRequest.setUpn(upn);
      return true;
    }
    return false;
  }

  public IdentityRequest.IdentityRequestState getRequestState(String requestId) {
    IdentityRequest identityRequest = identityRequests.get(requestId);
    if (identityRequest != null) {
       return new IdentityRequest.IdentityRequestState(
           identityRequest.getId(),
           identityRequest.getStatus(),
           identityRequest.getUpn()
       );
    } else {
      return null;
    }
  }

  @Scheduled(fixedDelay = 60000)
  protected void processRequests() {
    LocalDateTime now = LocalDateTime.now();
    Iterator<IdentityRequest> iterator = identityRequests.values().iterator();
    for (IdentityRequest identityRequest : identityRequests.values()) {
      if (identityRequest.getStatus() == IdentityRequest.Status.IN_PROGRESS && identityRequest.getCreated().plus(EXPIRES_AFTER).isBefore(now)) {
        identityRequest.setStatus(IdentityRequest.Status.EXPIRED);
      }
      if (identityRequest.getCreated().plus(DELETE_AFTER).isBefore(now)) {
        iterator.remove();
      }
    }
  }

}
