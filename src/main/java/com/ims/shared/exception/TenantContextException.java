package com.ims.shared.exception;

public class TenantContextException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public TenantContextException(String msg) {
    super(msg);
  }
}
