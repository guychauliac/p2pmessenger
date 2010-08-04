/**
 * Copyright (c) 2010 Axa Holding Belgium, SA. All rights reserved.
 * This software is the confidential and proprietary information of the AXA Group.
 */
package chabernac.protocol.routing;

import java.util.ArrayList;
import java.util.List;

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;

public class RoutingTableModel implements TableModel {
  private RoutingTable myRoutingTable = null;
  private List< TableModelListener > myTableModelListeners = new ArrayList< TableModelListener >();
  
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
    return 3;
  }
    
    

  @Override
  public String getColumnName( int anColumnIndex ) {
    if(anColumnIndex == 0) return "Peer";
    if(anColumnIndex == 1) return "Hop Distance";
    if(anColumnIndex == 2) return "Gateway";
    return "Unkwnown column";
  }

  @Override
  public int getRowCount() {
    return myRoutingTable.getReachableEntriesEntries().size();
  }

  @Override
  public Object getValueAt( int anRowIndex, int anColumnIndex ) {
    RoutingTableEntry theRoutingTableEntry = myRoutingTable.getReachableEntriesEntries().get(anRowIndex);
    if(anColumnIndex == 0) return theRoutingTableEntry.getPeer().toString();
    if(anColumnIndex == 1) return theRoutingTableEntry.getHopDistance();
    if(anColumnIndex == 2) return theRoutingTableEntry.getGateway().toString();
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
  
  private class MyRoutingTableListner implements IRoutingTableListener{
    @Override
    public void routingTableEntryChanged( RoutingTableEntry anEntry ) {
      for(TableModelListener theListener : myTableModelListeners){
        theListener.tableChanged( new TableModelEvent(RoutingTableModel.this) );
      }
    }
  }

}
