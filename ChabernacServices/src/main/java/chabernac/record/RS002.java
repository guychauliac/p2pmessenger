package chabernac.record;

import chabernac.synchro.SynchronizedEvent;
import chabernac.synchro.SynchronizedRecord;
import chabernac.synchro.event.FireEvent;

/**
 * record that contains screen location information
 *
 * @version v1.0.0      Mar 27, 2008
 *<pre><u><i>Version History</u></i>
 *
 * v1.0.0 Mar 27, 2008 - initial release       - Guy Chauliac
 *
 *</pre>
 *
 * @author <a href="mailto:Guy.Chauliac@axa.be"> Guy Chauliac </a>
 */

public class RS002 extends SynchronizedRecord{
  public static final int EVENT_MOVED = 1; 
  public static final int EVENT_FIRE = 2;

  public void defineFields(){
    setField("PLAYER", 2, NUMERIC);
    setField("X", 4, NUMERIC);
    setField("Y", 4, NUMERIC);
    setField("FIRING", 1, NUMERIC);

  }

  public SynchronizedEvent getEvent() {
    return new FireEvent(getIntValue("PLAYER"), getIntValue("X"), getIntValue("Y"), getBooleanValue("FIRING"));
  }
}
