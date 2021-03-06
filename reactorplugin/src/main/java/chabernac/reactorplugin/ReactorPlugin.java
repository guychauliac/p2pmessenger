/**
 * Copyright (c) 2011 Axa Holding Belgium, SA. All rights reserved.
 * This software is the confidential and proprietary information of the AXA Group.
 */
package chabernac.reactorplugin;

import java.awt.MenuItem;

import chabernac.gui.tray.SystemTrayMenu;
import chabernac.p2pclient.gui.ChatMediator;
import chabernac.p2pclient.plugin.iP2pClientPlugin;

public class ReactorPlugin implements iP2pClientPlugin{
  
  public ReactorPlugin(){
  }

  @Override
  public void init( ChatMediator aFacade ) {
    SystemTrayMenu theMenu = aFacade.getSystemTrayMenu();
    
    theMenu.getPluginMenu().add( new MenuItem("Reactor") );
  }

  @Override
  public void remove( ChatMediator aFacade ) {
    // TODO Auto-generated method stub
    
  }

}
