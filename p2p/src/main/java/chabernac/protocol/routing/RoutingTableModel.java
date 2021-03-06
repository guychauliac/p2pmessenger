/**
 * Copyright (c) 2010 Axa Holding Belgium, SA. All rights reserved.
 * This software is the confidential and proprietary information of the AXA Group.
 */
package chabernac.protocol.routing;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;

public class RoutingTableModel implements TableModel {
  private RoutingTable myRoutingTable = null;
  private List< TableModelListener > myTableModelListeners = new ArrayList< TableModelListener >();
  private List<RoutingTableEntry> myLocalCopy;
  
  private static final SimpleDateFormat FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
  
  public RoutingTableModel(RoutingTable aTable){
    myRoutingTable = aTable;
    myRoutingTable.addRoutingTableListener( new MyRoutingTableListner() );
  }

  @Override
  public void addTableModelListener( TableModelListener aTableModelListener ) {
    myTableModelListeners.add(aTableModelListener);
  }

  @Override
  public Class< ? > getColumnClass( int anColumnIndex ) {
    if(anColumnIndex == 1) return Integer.class;
    return String.class;
    
  }

  @Override
  public int getColumnCount() {
    return 8;
  }
    
    

  @Override
  public String getColumnName( int anColumnIndex ) {
    if(anColumnIndex == 0) return "Peer";
    if(anColumnIndex == 1) return "Hop Distance";
    if(anColumnIndex == 2) return "Gateway";
    if(anColumnIndex == 3) return "Last online time";
    if(anColumnIndex == 4) return "Supported protocols";
    if(anColumnIndex == 5) return "Temporary peer";
    if(anColumnIndex == 6) return "Test peer";
    if(anColumnIndex == 7) return "Interface";
    return "Unkwnown column";
  }

  @Override
  public int getRowCount() {
    myLocalCopy = new ArrayList<RoutingTableEntry>(myRoutingTable.getEntries());
    return myLocalCopy.size();
  }
  
  public RoutingTableEntry getRoutingTableEntryAtRow(int aRow){
    return  myLocalCopy.get(aRow);
  }

  @Override
  public Object getValueAt( int anRowIndex, int anColumnIndex ) {
    RoutingTableEntry theRoutingTableEntry = getRoutingTableEntryAtRow( anRowIndex );
    if(anColumnIndex == 0) return theRoutingTableEntry.getPeer().toString();
    if(anColumnIndex == 1) return theRoutingTableEntry.getHopDistance();
    if(anColumnIndex == 2) return theRoutingTableEntry.getGateway().toString();
    if(anColumnIndex == 3) {
      Date theDate = new Date();
      theDate.setTime( theRoutingTableEntry.getLastOnlineTime() );
      return FORMAT.format( theDate );
    }
    if(anColumnIndex == 4) {
      StringBuilder theProtocols = new StringBuilder();
      for(String theProt : theRoutingTableEntry.getPeer().getSupportedProtocols()){
        theProtocols.append( theProt );
        theProtocols.append( ";" );
      }
      return theProtocols.toString();
      
    }
    if(anColumnIndex == 5) return theRoutingTableEntry.getPeer().isTemporaryPeer();
    
    if(anColumnIndex == 6) return theRoutingTableEntry.getPeer().isTestPeer();
    
    if(anColumnIndex == 7) return theRoutingTableEntry.getLocalNetworkInterface() == null ? "" : theRoutingTableEntry.getLocalNetworkInterface().getName();
    return "";
  }
  

  @Override
  public boolean isCellEditable( int anRowIndex, int anColumnIndex ) {
    return false;
  }

  @Override
  public void removeTableModelListener( TableModelListener aListener ) {
    myTableModelListeners.remove( aListener );
  }

  @Override
  public void setValueAt( Object aValue, int anRowIndex, int anColumnIndex ) {
    
  }
  
  private void refresh(){
    for(TableModelListener theListener : myTableModelListeners){
      theListener.tableChanged( new TableModelEvent(RoutingTableModel.this) );
    }  
  }
  
  private class MyRoutingTableListner implements IRoutingTableListener{
    @Override
    public void routingTableEntryChanged( RoutingTableEntry anEntry ) {
      refresh();
    }

    @Override
    public void routingTableEntryRemoved( RoutingTableEntry anEntry ) {
      refresh();
    }
  }

}
