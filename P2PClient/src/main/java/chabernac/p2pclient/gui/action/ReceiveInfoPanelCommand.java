/**
 * Copyright (c) 2011 Axa Holding Belgium, SA. All rights reserved.
 * This software is the confidential and proprietary information of the AXA Group.
 */
package chabernac.p2pclient.gui.action;

import chabernac.command.Command;
import chabernac.p2pclient.settings.Settings;
import chabernac.preference.ApplicationPreferences;

public class ReceiveInfoPanelCommand implements Command {

  @Override
  public void execute() {
    ApplicationPreferences.getInstance().setEnumProperty( Settings.ReceiveEnveloppe.INFO_PANEL );
  }

}
