/**
 * Copyright (c) 2011 Axa Holding Belgium, SA. All rights reserved.
 * This software is the confidential and proprietary information of the AXA Group.
 */
package chabernac.protocol.packet;

import java.io.IOException;
import java.util.List;

public interface iDataPacketPersister {
  public void persistDataPacket(DataPacket aPacket) throws IOException;
  public List<String> listMissingPackets();
  public void close() throws IOException;
  public int getNrOfPackets();
  public boolean isComplete();
}
