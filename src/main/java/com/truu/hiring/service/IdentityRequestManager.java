package com.truu.hiring.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The IdentityRequestManager class manages the creation, completion, rejection, and retrieval of identity verification requests.
 * Identity verification requests are represented by the IdentityRequest class.
 * It's automatically processing of requests using, which runs at a fixed delay specified in milliseconds.
 *
 * @see IdentityRequest
 */
@Component
public class IdentityRequestManager {

  private static final Duration EXPIRES_AFTER = Duration.ofMinutes(10);
  private static final Duration DELETE_AFTER = Duration.ofMinutes(60);

  private Map<String, IdentityRequest> identityRequests = new ConcurrentHashMap<>();

  public IdentityRequest createRequest() {
    IdentityRequest identityRequest = new IdentityRequest(UUID.randomUUID().toString());
    identityRequests.put(identityRequest.getId(), identityRequest);
    return identityRequest;
  }

  public boolean completeRequest(String requestId, String upn) {
    return updateRequestStatus(requestId, IdentityRequest.Status.COMPLETE, upn);
  }

  public boolean rejectRequest(String requestId, String upn) {
    return updateRequestStatus(requestId, IdentityRequest.Status.REJECTED, upn);
  }

  public IdentityRequestState getRequestState(String requestId) {
    IdentityRequest identityRequest = identityRequests.get(requestId);
    if (identityRequest != null) {
       return new IdentityRequestState(
           identityRequest.getId(),
           identityRequest.getStatus(),
           identityRequest.getUpn()
       );
    } else {
      return null;
    }
  }

  private boolean updateRequestStatus(String requestId, IdentityRequest.Status newStatus, String upn) {
    IdentityRequest identityRequest = identityRequests.get(requestId);
    if (identityRequest != null) {
      synchronized (identityRequest) {
        identityRequest.setStatus(newStatus);
        identityRequest.setUpn(upn);
        return true;
      }
    }
    return false;
  }

  @Scheduled(fixedDelay = 60000)
  protected void processRequests() {
    LocalDateTime now = LocalDateTime.now();
    Iterator<IdentityRequest> iterator = identityRequests.values().iterator();
    while (iterator.hasNext()) {
      IdentityRequest identityRequest = iterator.next();
      if (identityRequest.getStatus() == IdentityRequest.Status.IN_PROGRESS && identityRequest.getCreated().plus(EXPIRES_AFTER).isBefore(now)) {
        updateRequestStatus(identityRequest.getId(), IdentityRequest.Status.EXPIRED, null);
      }
      if (identityRequest.getCreated().plus(DELETE_AFTER).isBefore(now)) {
        iterator.remove();
      }
    }
  }

}
