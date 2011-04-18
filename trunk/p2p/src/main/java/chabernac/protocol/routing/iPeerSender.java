package chabernac.protocol.routing;

import java.io.IOException;

public interface iPeerSender {
  public String send(String aMessage, SocketPeer aPeer, int aTimeout) throws IOException;
  public String send(String aMessage, AbstractPeer aSource, WebPeer aPeer, int aTimeout) throws IOException;
  public String send( String aMessage, IndirectReachablePeer aIndirectReachablePeer, int aTimeoutInSeconds );
}
