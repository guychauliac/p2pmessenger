/**
 * Copyright (c) 2011 Axa Holding Belgium, SA. All rights reserved.
 * This software is the confidential and proprietary information of the AXA Group.
 */
package chabernac.protocol.packet;

public interface iPacketTransferListener {
  public void transferUpdated(PacketTransferState aPacketTransferState);
}
