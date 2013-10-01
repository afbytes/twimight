package ch.ethz.twimight;

import fi.tkk.netlab.dtn.scampi.android.SCAMPIApplication;

/**
 * Application context used to manage the AppLib connection between
 * PeopleInHereActivity that runs the Android wrapper for applications 
 * and BackroundService that runs the Router as background process.
 * 
 * This class is used to maintain references to AppLib object, which 
 * is essentially an open socket connection to background service with 
 * some state. The class is needed to maintain object reference, as live 
 * state of an open socket () cannot be persisted otherwise.
 * 
 * @author mikkopitkanen
 */
public class TwimightApplication extends SCAMPIApplication {
	@Override
	public void onCreate() {
		super.onCreate();
	}
}
