/**
 * Copyright (c) 2010 Axa Holding Belgium, SA. All rights reserved.
 * This software is the confidential and proprietary information of the AXA Group.
 */
package chabernac.gui.tray;

import java.awt.Font;
import java.awt.MenuItem;
import java.awt.SystemTray;
import java.awt.image.BufferedImage;
import java.io.IOException;

import javax.imageio.ImageIO;

import chabernac.io.ClassPathResource;
import chabernac.p2pclient.gui.ChatMediator;
import chabernac.p2pclient.gui.action.ActionFactory;
import chabernac.p2pclient.gui.action.CommandActionListener;
import chabernac.p2pclient.settings.Settings;
import chabernac.preference.ApplicationPreferences;
import chabernac.preference.iApplicationPreferenceListener;

public class ReceiveClosedMenuItem extends MenuItem implements iApplicationPreferenceListener {
  private static final long serialVersionUID = -5224352709920368154L;
  private final BufferedImage myImage;

  public ReceiveClosedMenuItem(ChatMediator aMediator) throws IOException{
    super("Ontvang met gesloten enveloppe");
    addActionListener( new CommandActionListener(aMediator.getActionFactory(), ActionFactory.Action.RECEIVE_CLOSED));
    myImage = ImageIO.read( new ClassPathResource("images/message.png").getInputStream());
    setBold();
    addPreferenceListener();
  }

  private void addPreferenceListener(){
    ApplicationPreferences.getInstance().addApplicationPreferenceListener( this );
  }

  private void setBold(){
    setFont( new Font("Arial", ApplicationPreferences.getInstance().hasEnumProperty(Settings.ReceiveEnveloppe.CLOSED) ? Font.BOLD : Font.PLAIN, 12 ) );
    if(ApplicationPreferences.getInstance().hasEnumProperty(Settings.ReceiveEnveloppe.CLOSED)){
      SystemTray.getSystemTray().getTrayIcons()[0].setImage( myImage );
    }
  }

  @Override
  public void applicationPreferenceChanged( String aKey, String aValue ) {
  }

  @Override
  public void applicationPreferenceChanged( Enum anEnumValue ) {
    setBold();
  }
}
