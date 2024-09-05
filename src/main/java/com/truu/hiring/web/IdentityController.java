package com.truu.hiring.web;

import com.truu.hiring.service.IdentityRequest;
import com.truu.hiring.service.IdentityRequestManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/identity")
public class IdentityController {

  private final IdentityRequestManager identityRequestManager;

  @Autowired
  public IdentityController(IdentityRequestManager identityRequestManager) {
    this.identityRequestManager = identityRequestManager;
  }

  @GetMapping("/create")
  public IdentityRequest create() {
    return identityRequestManager.createRequest();
  }

  @PostMapping("/complete")
  public boolean completeRequest(@RequestParam String requestId, @RequestParam String upn, boolean shouldDelete) {
    return identityRequestManager.completeRequest(requestId, upn, shouldDelete);
  }

  @PostMapping("/reject")
  public boolean rejectRequest(@RequestParam String requestId, @RequestParam String upn) {
    return identityRequestManager.rejectRequest(requestId, upn);
  }

  @GetMapping("/status")
  public IdentityRequest.IdentityRequestState getRequestState(@RequestParam String requestId) {
    return identityRequestManager.getRequestState(requestId);
  }
}

