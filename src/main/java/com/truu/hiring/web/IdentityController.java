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

  private final IdentityRequestManager m;

  @Autowired
  public IdentityController(IdentityRequestManager identityRequestManager) {
    this.m = identityRequestManager;
  }

  @GetMapping("/create")
  public IdentityRequest create() {
    return m.createRequest();
  }

  @PostMapping("/complete")
  public boolean completeRequest(@RequestParam String requestId, @RequestParam String upn, boolean shouldDelete) {
    return this.m.completeRequest(requestId, upn, shouldDelete);
  }

  @PostMapping("/reject")
  public boolean rejectRequest(@RequestParam String requestId, @RequestParam String upn) {
    return this.m.rejectRequest(requestId, upn);
  }

  @GetMapping("/status")
  public IdentityRequest.IdentityRequestState getRequestState(@RequestParam String requestId) {
    return this.m.getRequestState(requestId);
  }
}

