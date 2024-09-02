package com.truu.hiring.web;

import com.truu.hiring.service.IdentityRequestManager;
import com.truu.hiring.service.IdentityRequestState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/identity")
public class IdentityController {

  private final IdentityRequestManager identityRequestManager;

  @Autowired
  public IdentityController(IdentityRequestManager identityRequestManager) {
    this.identityRequestManager = identityRequestManager;
  }

  @PostMapping("/complete")
  public void completeRequest(@RequestParam String requestId, @RequestParam String upn) {
    boolean completed = identityRequestManager.completeRequest(requestId, upn);
    if (!completed) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found");
    }
  }

  @PostMapping("/reject")
  public void rejectRequest(@RequestParam String requestId, @RequestParam String upn) {
    boolean completed = identityRequestManager.rejectRequest(requestId, upn);
    if (!completed) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found");
    }
  }

  @GetMapping("/status")
  public IdentityRequestState getRequestState(@RequestParam String requestId) {
    var state = identityRequestManager.getRequestState(requestId);
    if (state == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found");
    } else {
      return state;
    }
  }
}

