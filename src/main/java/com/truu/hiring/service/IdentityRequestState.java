package com.truu.hiring.service;

public class IdentityRequestState {

  private final String id;
  private final IdentityRequest.Status status;
  private final String upn;

  public IdentityRequestState(String id, IdentityRequest.Status status, String upn) {
    this.id = id;
    this.status = status;
    this.upn = upn;
  }

  public String getId() {
    return id;
  }

  public IdentityRequest.Status getStatus() {
    return status;
  }

  public String getUpn() {
    return upn;
  }
}
