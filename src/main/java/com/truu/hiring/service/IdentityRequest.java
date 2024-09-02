package com.truu.hiring.service;

import java.time.LocalDateTime;

public class IdentityRequest {

  public enum Status {
    IN_PROGRESS, COMPLETE, REJECTED, EXPIRED
  }

  private final String id;
  private final LocalDateTime created;
  private Status status;
  private String upn;

  public IdentityRequest(String id) {
    this.id = id;
    this.created = LocalDateTime.now();
  }

  public String getId() {
    return id;
  }

  public Status getStatus() {
    return status;
  }

  protected void setStatus(Status status) {
    this.status = status;
  }

  public LocalDateTime getCreated() {
    return created;
  }

  public String getUpn() {
    return upn;
  }

  protected void setUpn(String upn) {
    this.upn = upn;
  }
}
