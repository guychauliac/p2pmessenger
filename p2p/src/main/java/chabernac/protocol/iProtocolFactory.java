/**
 * Copyright (c) 2010 Axa Holding Belgium, SA. All rights reserved.
 * This software is the confidential and proprietary information of the AXA Group.
 */
package chabernac.protocol;

public interface iProtocolFactory {
  public Protocol createProtocol(String aProtocolId) throws ProtocolException;
}
