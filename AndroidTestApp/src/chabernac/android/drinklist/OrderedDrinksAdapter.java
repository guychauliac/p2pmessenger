/**
 * Copyright (c) 2011 Axa Holding Belgium, SA. All rights reserved.
 * This software is the confidential and proprietary information of the AXA Group.
 */
package chabernac.android.drinklist;

import android.database.DataSetObserver;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import chabernac.android.quickaction.DrinkListQuickActionWindow;
import chabernac.android.tools.Tools;

public class OrderedDrinksAdapter extends BaseAdapter{
  private final DrinkList myDrinkList;
  private final OrderDrinkActivity myContext;
  private final int FONT_SIZE = 20;
  
  public OrderedDrinksAdapter( OrderDrinkActivity aContext, DrinkList aDrinkList ) {
    myContext = aContext;
    myDrinkList = aDrinkList;
    myDrinkList.registerObserver( new DrinkListObserver() );
  }

  @Override
  public int getCount() {
    return myDrinkList.getList().size();
  }

  public Object getItem(int position) {
    return null;
  }

  public long getItemId(int position) {
    return 0;
  }

  // create a new ImageView for each item referenced by the Adapter
  public View getView(int position, View convertView, ViewGroup parent) {
    LinearLayout theLinerLayout = new LinearLayout( myContext );
    theLinerLayout.setOrientation( LinearLayout.HORIZONTAL );
    AbsListView.LayoutParams theParams = new AbsListView.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    theLinerLayout.setLayoutParams( theParams );

    DrinkOrder theDrink = myDrinkList.getDrinkAt( position );

    LinearLayout.LayoutParams theLParams; 

    TextView theTextView = new TextView(myContext);
    theTextView.setTextColor(Color.BLACK);
    theTextView.setTypeface( Typeface.SERIF, Typeface.NORMAL );
    theTextView.setTextSize( FONT_SIZE );
    theTextView.setText( Tools.translate( myContext, theDrink.getName() ));
    theLParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    theLParams.weight = 2;
    theTextView.setLayoutParams( theLParams );
    theLinerLayout.addView( theTextView );

    theTextView = new TextView(myContext);
    theTextView.setTextColor(Color.BLACK);
    theTextView.setTypeface( Typeface.SERIF, Typeface.NORMAL );
    theTextView.setTextSize( FONT_SIZE );
    theTextView.setText( Integer.toString( theDrink.getNumberOfDrinks() ));
    theLParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    theLParams.width = 20;
    theTextView.setLayoutParams( theLParams );
    theLinerLayout.addView( theTextView );

    SelectDrinkOrderAction theListener = new SelectDrinkOrderAction(theDrink);
    theLinerLayout.setOnClickListener( theListener );
    return theLinerLayout;
  }

  private class SelectDrinkOrderAction implements OnClickListener{
    private final DrinkOrder myDrinkOrder;

    public SelectDrinkOrderAction(DrinkOrder aDrinkOrder){
      myDrinkOrder = aDrinkOrder;
    }

    @Override
    public void onClick( View aView ) {
      if(myContext.isDrinkListPanel()) {
        DrinkListQuickActionWindow theCurrentPopupWindow = new DrinkListQuickActionWindow(aView, myDrinkOrder, myDrinkList);
        theCurrentPopupWindow.showLikeQuickAction(0, 0);
      }
    }
  }

  public class DrinkListObserver extends DataSetObserver {
    public void onChanged(){
      notifyDataSetChanged();
    }

  }
}
