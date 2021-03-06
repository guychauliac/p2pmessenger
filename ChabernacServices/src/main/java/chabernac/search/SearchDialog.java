/**
 * Copyright (c) 2011 Axa Holding Belgium, SA. All rights reserved.
 * This software is the confidential and proprietary information of the AXA Group.
 */
package chabernac.search;

import java.awt.BorderLayout;

import javax.swing.JDialog;
import javax.swing.JFrame;

public class SearchDialog extends JDialog {
  private static final long serialVersionUID = -5330892007311302470L;
  @SuppressWarnings( "unchecked" )
  private final AbstractSearchProvider mySearchProvider;
  private SearchPanel mySearchPanel;
  
  @SuppressWarnings( "unchecked" )
  public SearchDialog(JFrame aParent, AbstractSearchProvider aSearchProvider, boolean isModal){
    super( aParent, isModal);
    setLocationRelativeTo( aParent );
    
    mySearchProvider = aSearchProvider;
    
    buildGUI();
  }
  
  private void buildGUI(){
    setTitle( "Search" );
    
    setLayout( new BorderLayout() );
    
    mySearchPanel = new SearchPanel( mySearchProvider ); 
    add(mySearchPanel, BorderLayout.CENTER);
    
    setSize( 350, 100 );
    
    setResizable( false );
  }
  
  public void setVisible(boolean isVisible){
    super.setVisible( isVisible );
    mySearchPanel.requestFocus();
  }
}
