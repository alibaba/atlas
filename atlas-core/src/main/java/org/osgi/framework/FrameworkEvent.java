

package org.osgi.framework;

/**
 * A general Framework event.
 * 
 * <p>
 * <tt>FrameworkEvent</tt> is the event class used when notifying listeners of
 * general events occuring within the OSGI environment. A type code is used to
 * identify the event type for future extendability.
 * 
 * <p>
 * OSGi reserves the right to extend the set of event types.
 * 
 * @version $Revision: 1.1 $
 * @author Open Services Gateway Initiative
 */

public class FrameworkEvent {

	public static final int STARTED = 0x00000001;

	public static final int ERROR = 0x00000002;


	public static final int PACKAGES_REFRESHED = 0x00000004;


	public static final int STARTLEVEL_CHANGED = 0x00000008;

	public int state;

	public FrameworkEvent(int state){
		this.state = state;
	}

	public int getType(){
		return state;
	}

}
