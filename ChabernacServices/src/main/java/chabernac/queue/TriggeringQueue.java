/*
 * Copyright (c) 2003 Axa Holding Belgium, SA. All rights reserved.
 * This software is the confidential and proprietary information of the AXA Group.
 */

package chabernac.queue;

import java.util.Vector;

/**
 * 
 * 
 *
 * @version v1.0.0      Sep 20, 2005
 *<pre><u><i>Version History</u></i>
 *
 * v1.0.0 Sep 20, 2005 - initial release       - Guy Chauliac
 *
 *</pre>
 *
 * @author <a href="mailto:guy.chauliac@axa.be"> Guy Chauliac </a>
 */


public class TriggeringQueue implements iQueue {
	private iQueue myQueue = null;
	private Vector myListeners = null;

	public TriggeringQueue(iQueue aQueue){
		myQueue = aQueue;
		myListeners = new Vector();
	}

	public Object get() {
		return myQueue.get();
	}

	public void put(Object anObject) {
		myQueue.put(anObject);
		if(myListeners.size() > 0){
			notifyListeners();
		}
	}

	private void notifyListeners(){
		Listener theListener = null;
		for(int i=0;i<myListeners.size();i++){
			theListener = (Listener)myListeners.elementAt(i);
			if(size() >= theListener.triggerLimit){
				theListener.listener.trigger();
			}
		}
	}

	public int size(){
		return myQueue.size();
	}

	public void addQueueListener(iQueueListener aListener, int aTriggeringLimit){
		Listener theListener = new Listener(aListener, aTriggeringLimit);
		myListeners.add(theListener);
	}

	public void removeQueueListener(iQueueListener aListener){
		myListeners.remove(new Listener(aListener, 0));
	}

	private class Listener{
		iQueueListener listener = null;
		int triggerLimit = 1;

		public Listener(iQueueListener aListener, int aTriggerLimit){
			listener = aListener;
			triggerLimit = aTriggerLimit;
		}

		public boolean equals(Object anObject){
			if(!(anObject instanceof Listener))return false;
			if( ((Listener)anObject).listener != listener ) return false;
			return true;
		}
	}

	public void clear() {
		myQueue.clear();
	}

	public Object peek() {
		return myQueue.peek();
	}

}
