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
  public boolean completeRequest(@RequestParam String requestId, @RequestParam String upn) {
    return identityRequestManager.completeRequest(requestId, upn);
  }

  @PostMapping("/reject")
  public boolean rejectRequest(@RequestParam String requestId, @RequestParam String upn) {
    return identityRequestManager.rejectRequest(requestId, upn);
  }

  @GetMapping("/status")
  public IdentityRequestState getRequestState(@RequestParam String requestId) {
    return identityRequestManager.getRequestState(requestId);
  }
}

