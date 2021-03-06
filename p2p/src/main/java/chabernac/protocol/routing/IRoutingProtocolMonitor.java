/**
 * Copyright (c) 2010 Axa Holding Belgium, SA. All rights reserved.
 * This software is the confidential and proprietary information of the AXA Group.
 */
package chabernac.protocol.routing;

public interface IRoutingProtocolMonitor {
  public void scanStarted(AbstractPeer aPeer);
  public void peerFoundWithScan(AbstractPeer aPeer);
  public void localSystemScanStarted();
  public void remoteSystemScanStarted();
  public void exchangingRoutingTables();
  public void sendingUDPAnnouncement();
  public void detectingRemoteSystemStarted();
  public void scanningSuperNodes();
}
