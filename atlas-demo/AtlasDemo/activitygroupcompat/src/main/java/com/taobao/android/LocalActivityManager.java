/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.taobao.android;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.taobao.atlas.hack.AndroidHack;
import android.taobao.atlas.hack.AtlasHacks;
import android.taobao.atlas.hack.Hack;
import android.taobao.atlas.runtime.RuntimeVariables;
import android.util.Log;
import android.view.Window;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class LocalActivityManager {
    private static final String TAG = "LocalActivityManager";
    private static final boolean localLOGV = false;

    // Internal token for an Activity being managed by LocalActivityManager.
    private static class LocalActivityRecord extends Binder {
        LocalActivityRecord(String _id, Intent _intent) {
            id = _id;
            intent = _intent;
        }

        final String id;                // Unique name of this record.
        Intent intent;                  // Which activity to run here.
        ActivityInfo activityInfo;      // Package manager info about activity.
        Activity activity;              // Currently instantiated activity.
        Window window;                  // Activity's top-level window.
        Bundle instanceState;           // Last retrieved freeze state.
        int curState = RESTORED;        // Current state the activity is in.
    }

    static final int RESTORED = 0;      // State restored, but no startActivity().
    static final int INITIALIZING = 1;  // Ready to launch (after startActivity()).
    static final int CREATED = 2;       // Created, not started or resumed.
    static final int STARTED = 3;       // Created and started, not resumed.
    static final int RESUMED = 4;       // Created started and resumed.
    static final int DESTROYED = 5;     // No longer with us.

    /** Thread our activities are running in. */
    private final Object mActivityThread;
    /** The containing activity that owns the activities we create. */
    private final Activity mParent;

    /** The activity that is currently resumed. */
    private LocalActivityRecord mResumed;
    /** id -> record of all known activities. */
    private final Map<String, LocalActivityRecord> mActivities
            = new HashMap<String, LocalActivityRecord>();
    /** array of all known activities for easy iterating. */
    private final ArrayList<LocalActivityRecord> mActivityArray
            = new ArrayList<LocalActivityRecord>();

    /** True if only one activity can be resumed at a time */
    private final boolean mSingleMode;

    /** Set to true once we find out the container is finishing. */
    private boolean mFinishing;

    /** Current state the owner (ActivityGroup) is in */
    private int mCurState = INITIALIZING;

    private Hack.HackedClass  NonConfigurationInstances;
    private Hack.HackedMethod ActivityThread_startActivityNow;
    private Hack.HackedMethod ActivityThread_performRestartActivity;
    private Hack.HackedMethod ActivityThread_performDestroyActivity;
    private Hack.HackedMethod Activity_performSaveInstanceState;

    private Hack.HackedMethod ActivityThread_performResumeActivity;
    private Hack.HackedMethod ActivityThread_performStopActivity;
    private Hack.HackedMethod ActivityThread_performPauseActivity;

    private Hack.HackedMethod Activity_onStart;
    private Hack.HackedMethod Activity_onStop;


    /**
     * Create a new LocalActivityManager for holding activities running within
     * the given <var>parent</var>.
     *
     * @param parent the host of the embedded activities
     * @param singleMode True if the LocalActivityManger should keep a maximum
     * of one activity resumed
     */
    public LocalActivityManager(Activity parent, boolean singleMode) throws Throwable{
        defineAndVerify();
        mActivityThread = AndroidHack.getActivityThread();
        mParent = parent;
        mSingleMode = singleMode;
    }

    private void defineAndVerify() throws Hack.HackDeclaration.HackAssertionException{
        NonConfigurationInstances = Hack.into("android.app.Activity$NonConfigurationInstances");
        ActivityThread_startActivityNow = AtlasHacks.ActivityThread.method("startActivityNow",Activity.class,String.class,
                Intent.class,ActivityInfo.class, IBinder.class,Bundle.class,NonConfigurationInstances.getmClass());
        if (Build.VERSION.SDK_INT >= 28) {
            ActivityThread_performRestartActivity = AtlasHacks.ActivityThread.method(
                    "performRestartActivity", IBinder.class, boolean.class);
        } else {
            ActivityThread_performRestartActivity = AtlasHacks.ActivityThread.method(
                    "performRestartActivity", IBinder.class);

        }

        if (Build.VERSION.SDK_INT >= 28) {
            ActivityThread_performDestroyActivity = AtlasHacks.ActivityThread.method(
                    "performDestroyActivity", IBinder.class, boolean.class, int.class,
                    boolean.class, String.class);

        } else {
            ActivityThread_performDestroyActivity = AtlasHacks.ActivityThread.method(
                    "performDestroyActivity", IBinder.class, boolean.class);

        }
        Activity_performSaveInstanceState = Hack.into(Activity.class).method("performSaveInstanceState",Bundle.class);
        Activity_onStart = Hack.into(Activity.class).method("onStart");
        Activity_onStop = Hack.into(Activity.class).method("onStop");
        if(Build.VERSION.SDK_INT<=23) {
            ActivityThread_performResumeActivity = AtlasHacks.ActivityThread.method("performResumeActivity",
                    IBinder.class,boolean.class);
            ActivityThread_performStopActivity = AtlasHacks.ActivityThread.method("performStopActivity",
                    IBinder.class,boolean.class);
            ActivityThread_performPauseActivity = AtlasHacks.ActivityThread.method("performPauseActivity",
                    IBinder.class,boolean.class,boolean.class);
        }else{
            ActivityThread_performResumeActivity = AtlasHacks.ActivityThread.method("performResumeActivity",
                    IBinder.class,boolean.class,String.class);
            ActivityThread_performStopActivity = AtlasHacks.ActivityThread.method("performStopActivity",
                    IBinder.class,boolean.class,String.class);
            if (Build.VERSION.SDK_INT >= 28) {

                try {


                    ActivityThread_performPauseActivity = AtlasHacks.ActivityThread.method(
                            "performPauseActivity",
                            IBinder.class, boolean.class, /*boolean.class,*/ String.class,
                            Class.forName(
                                    "android.app.servertransaction.PendingTransactionActions"));
                    ;
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }

            } else {
                ActivityThread_performPauseActivity = AtlasHacks.ActivityThread.method(
                        "performPauseActivity",
                        IBinder.class, boolean.class, boolean.class, String.class);

            }
        }
    }

    private void moveToState(LocalActivityRecord r, int desiredState) {
        if (r.curState == RESTORED || r.curState == DESTROYED) {
            // startActivity() has not yet been called, so nothing to do.
            return;
        }

        if (r.curState == INITIALIZING) {
            // We need to have always created the activity.
            if (localLOGV) Log.v(TAG, r.id + ": starting " + r.intent);
            if (r.activityInfo == null) {
                r.activityInfo = r.intent.resolveActivityInfo(
                        RuntimeVariables.androidApplication.getPackageManager(), PackageManager.GET_SHARED_LIBRARY_FILES);
            }
            try {
                r.activity = (Activity) ActivityThread_startActivityNow.invoke(mActivityThread,
                        mParent, r.id, r.intent, r.activityInfo, r, r.instanceState, null);
            }catch(InvocationTargetException e){
                throw new RuntimeException(e.getTargetException());
            }
            if (r.activity == null) {
                return;
            }
            r.window = r.activity.getWindow();
            r.instanceState = null;
            r.curState = STARTED;

            if (desiredState == RESUMED) {
                if (localLOGV) Log.v(TAG, r.id + ": resuming");
//                mActivityThread.performResumeActivity(r, true, "moveToState-INITIALIZING");
                try {
                    if(ActivityThread_performResumeActivity.getMethod().getParameterTypes().length==2){
                        ActivityThread_performResumeActivity.invoke(mActivityThread,r,true);
                    }else{
                        ActivityThread_performResumeActivity.invoke(mActivityThread,r,true,"moveToState-INITIALIZING");
                    }
                }catch (InvocationTargetException e){
                    throw new RuntimeException(e.getTargetException());
                }
                r.curState = RESUMED;
            }

            // Don't do anything more here.  There is an important case:
            // if this is being done as part of onCreate() of the group, then
            // the launching of the activity gets its state a little ahead
            // of our own (it is now STARTED, while we are only CREATED).
            // If we just leave things as-is, we'll deal with it as the
            // group's state catches up.
            return;
        }

        switch (r.curState) {
            case CREATED:
                if (desiredState == STARTED) {
                    if (localLOGV) Log.v(TAG, r.id + ": restarting");
                    try {
                        ActivityThread_performRestartActivity.invoke(mActivityThread, r);
                    }catch (InvocationTargetException e){
                        throw new RuntimeException(e.getTargetException());
                    }
                    r.curState = STARTED;
                }
                if (desiredState == RESUMED) {
                    if (localLOGV) Log.v(TAG, r.id + ": restarting and resuming");
                    try {
                        if (Build.VERSION.SDK_INT >= 28) {
                            ActivityThread_performRestartActivity.invoke(mActivityThread, r, false);
                        } else {
                            ActivityThread_performRestartActivity.invoke(mActivityThread, r);

                        }

                        if(ActivityThread_performResumeActivity.getMethod().getParameterTypes().length==2){
                            ActivityThread_performResumeActivity.invoke(mActivityThread,r,true);
                        }else{
                            ActivityThread_performResumeActivity.invoke(mActivityThread,r,true,"moveToState-CREATED");
                        }
                    }catch (InvocationTargetException e){
                        throw new RuntimeException(e.getTargetException());
                    }
//                    mActivityThread.performResumeActivity(r, true, "moveToState-CREATED");
                    r.curState = RESUMED;
                }
                return;

            case STARTED:
                if (desiredState == RESUMED) {
                    // Need to resume it...
                    if (localLOGV) Log.v(TAG, r.id + ": resuming");
//                    mActivityThread.performResumeActivity(r, true, "moveToState-STARTED");
                    try {
                        if(ActivityThread_performResumeActivity.getMethod().getParameterTypes().length==2){
                            ActivityThread_performResumeActivity.invoke(mActivityThread,r,true);
                        }else{
                            ActivityThread_performResumeActivity.invoke(mActivityThread,r,true,"moveToState-STARTED");
                        }
                    }catch (InvocationTargetException e){
                        throw new RuntimeException(e.getTargetException());
                    }
                    r.instanceState = null;
                    r.curState = RESUMED;
                }
                if (desiredState == CREATED) {
                    if (localLOGV) Log.v(TAG, r.id + ": stopping");
//                    mActivityThread.performStopActivity(r, false, "moveToState-STARTED");
                    try {
                        if(ActivityThread_performStopActivity.getMethod().getParameterTypes().length==2){
                            ActivityThread_performStopActivity.invoke(mActivityThread,r,false);
                        }else{
                            ActivityThread_performStopActivity.invoke(mActivityThread,r,false,"moveToState-STARTED");
                        }
                    }catch (InvocationTargetException e){
                        throw new RuntimeException(e.getTargetException());
                    }
                    r.curState = CREATED;
                }
                return;

            case RESUMED:
                if (desiredState == STARTED) {
                    if (localLOGV) Log.v(TAG, r.id + ": pausing");
                    performPause(r, mFinishing);
                    r.curState = STARTED;
                }
                if (desiredState == CREATED) {
                    if (localLOGV) Log.v(TAG, r.id + ": pausing");
                    performPause(r, mFinishing);
                    if (localLOGV) Log.v(TAG, r.id + ": stopping");
//                    mActivityThread.performStopActivity(r, false, "moveToState-RESUMED");
                    try {
                        if(ActivityThread_performStopActivity.getMethod().getParameterTypes().length==2){
                            ActivityThread_performStopActivity.invoke(mActivityThread,r,false);
                        }else{
                            ActivityThread_performStopActivity.invoke(mActivityThread,r,false,"moveToState-RESUMED");
                        }
                    }catch (InvocationTargetException e){
                        throw new RuntimeException(e.getTargetException());
                    }
                    r.curState = CREATED;
                }
                return;
        }
    }

    private void performPause(LocalActivityRecord r, boolean finishing) {
        final boolean needState = r.instanceState == null;
//        final Bundle instanceState = mActivityThread.performPauseActivity(
//                r, finishing, needState, "performPause");
        Bundle instanceState = null;
        try {
            if(ActivityThread_performPauseActivity.getMethod().getParameterTypes().length==3){
                instanceState = (Bundle) ActivityThread_performPauseActivity.invoke(mActivityThread,r,finishing,needState);
            }else{
                if (Build.VERSION.SDK_INT >= 28) {
                    instanceState = (Bundle) ActivityThread_performPauseActivity.invoke(
                            mActivityThread, r, finishing,/*needState,*/"moveToState-RESUMED",
                            null);
                } else {
                    instanceState = (Bundle) ActivityThread_performPauseActivity.invoke(
                            mActivityThread, r, finishing, needState, "moveToState-RESUMED");

                }

            }
        }catch (InvocationTargetException e){
            throw new RuntimeException(e.getTargetException());
        }
        if (needState) {
            r.instanceState = instanceState;
        }
    }

    /**
     * Start a new activity running in the group.  Every activity you start
     * must have a unique string ID associated with it -- this is used to keep
     * track of the activity, so that if you later call startActivity() again
     * on it the same activity object will be retained.
     *
     * <p>When there had previously been an activity started under this id,
     * it may either be destroyed and a new one started, or the current
     * one re-used, based on these conditions, in order:</p>
     *
     * <ul>
     * <li> If the Intent maps to a different activity component than is
     * currently running, the current activity is finished and a new one
     * started.
     * <li> If the current activity uses a non-multiple launch mode (such
     * as singleTop), or the Intent has the
     * {@link Intent#FLAG_ACTIVITY_SINGLE_TOP} flag set, then the current
     * activity will remain running and its
     * {@link Activity#onNewIntent(Intent) Activity.onNewIntent()} method
     * called.
     * <li> If the new Intent is the same (excluding extras) as the previous
     * one, and the new Intent does not have the
     * {@link Intent#FLAG_ACTIVITY_CLEAR_TOP} set, then the current activity
     * will remain running as-is.
     * <li> Otherwise, the current activity will be finished and a new
     * one started.
     * </ul>
     *
     * <p>If the given Intent can not be resolved to an available Activity,
     * this method throws {@link ActivityNotFoundException}.
     *
     * <p>Warning: There is an issue where, if the Intent does not
     * include an explicit component, we can restore the state for a different
     * activity class than was previously running when the state was saved (if
     * the set of available activities changes between those points).
     *
     * @param id Unique identifier of the activity to be started
     * @param intent The Intent describing the activity to be started
     *
     * @return Returns the window of the activity.  The caller needs to take
     * care of adding this window to a view hierarchy, and likewise dealing
     * with removing the old window if the activity has changed.
     *
     * @throws ActivityNotFoundException
     */
    public Window startActivity(String id, Intent intent) {
        if (mCurState == INITIALIZING) {
            throw new IllegalStateException(
                    "Activities can't be added until the containing group has been created.");
        }

        boolean adding = false;
        boolean sameIntent = false;

        ActivityInfo aInfo = null;

        // Already have information about the new activity id?
        LocalActivityRecord r = mActivities.get(id);
        if (r == null) {
            // Need to create it...
            r = new LocalActivityRecord(id, intent);
            adding = true;
        } else if (r.intent != null) {
            sameIntent = r.intent.filterEquals(intent);
            if (sameIntent) {
                // We are starting the same activity.
                aInfo = r.activityInfo;
            }
        }
        if (aInfo == null) {
            aInfo = r.intent.resolveActivityInfo(
                    RuntimeVariables.androidApplication.getPackageManager(), PackageManager.GET_SHARED_LIBRARY_FILES);
            if (aInfo == null) {
                // Throw an exception.
                throw new ActivityNotFoundException(
                        "No Activity found to handle " + intent);
            }
        }

        // Pause the currently running activity if there is one and only a single
        // activity is allowed to be running at a time.
        if (mSingleMode) {
            LocalActivityRecord old = mResumed;

            // If there was a previous activity, and it is not the current
            // activity, we need to stop it.
            if (old != null && old != r && mCurState == RESUMED) {
                moveToState(old, STARTED);
            }
        }

        if (adding) {
            // It's a brand new world.
            mActivities.put(id, r);
            mActivityArray.add(r);
        } else if (r.activityInfo != null) {
            // If the new activity is the same as the current one, then
            // we may be able to reuse it.
            if (aInfo == r.activityInfo ||
                    (aInfo.name.equals(r.activityInfo.name) &&
                            aInfo.packageName.equals(r.activityInfo.packageName))) {
                if (aInfo.launchMode != ActivityInfo.LAUNCH_MULTIPLE ||
                        (intent.getFlags()&Intent.FLAG_ACTIVITY_SINGLE_TOP) != 0) {
                    // The activity wants onNewIntent() called.
                    intent.setExtrasClassLoader(r.activity.getClassLoader());
                    //TODO callActivityOnNewIntent use reflect
                    //callActivityOnNewIntent(r.activity, intent);
                    r.intent = intent;
                    moveToState(r, mCurState);
                    if (mSingleMode) {
                        mResumed = r;
                    }
                    return r.window;
                }
                if (sameIntent &&
                        (intent.getFlags()&Intent.FLAG_ACTIVITY_CLEAR_TOP) == 0) {
                    // We are showing the same thing, so this activity is
                    // just resumed and stays as-is.
                    r.intent = intent;
                    moveToState(r, mCurState);
                    if (mSingleMode) {
                        mResumed = r;
                    }
                    return r.window;
                }
            }

            // The new activity is different than the current one, or it
            // is a multiple launch activity, so we need to destroy what
            // is currently there.
            performDestroy(r, true);
        }

        r.intent = intent;
        r.curState = INITIALIZING;
        r.activityInfo = aInfo;

        moveToState(r, mCurState);

        // When in single mode keep track of the current activity
        if (mSingleMode) {
            mResumed = r;
        }
        return r.window;
    }

    private Window performDestroy(LocalActivityRecord r, boolean finish) {
        Window win;
        win = r.window;
        if (r.curState == RESUMED && !finish) {
            performPause(r, finish);
        }
        if (localLOGV) Log.v(TAG, r.id + ": destroying");
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                ActivityThread_performDestroyActivity.invoke(mActivityThread, r, finish, 0, false,
                        "destroy");
            } else {
                ActivityThread_performDestroyActivity.invoke(mActivityThread, r, finish);

            }

        }catch (InvocationTargetException e){
            throw new RuntimeException(e.getTargetException());
        }
        r.activity = null;
        r.window = null;
        if (finish) {
            r.instanceState = null;
        }
        r.curState = DESTROYED;
        return win;
    }

    /**
     * Destroy the activity associated with a particular id.  This activity
     * will go through the normal lifecycle events and fine onDestroy(), and
     * then the id removed from the group.
     *
     * @param id Unique identifier of the activity to be destroyed
     * @param finish If true, this activity will be finished, so its id and
     * all state are removed from the group.
     *
     * @return Returns the window that was used to display the activity, or
     * null if there was none.
     */
    public Window destroyActivity(String id, boolean finish) {
        LocalActivityRecord r = mActivities.get(id);
        Window win = null;
        if (r != null) {
            win = performDestroy(r, finish);
            if (finish) {
                mActivities.remove(id);
                mActivityArray.remove(r);
            }
        }
        return win;
    }

    /**
     * Retrieve the Activity that is currently running.
     *
     * @return the currently running (resumed) Activity, or null if there is
     *         not one
     *
     * @see #startActivity
     * @see #getCurrentId
     */
    public Activity getCurrentActivity() {
        return mResumed != null ? mResumed.activity : null;
    }

    /**
     * Retrieve the ID of the activity that is currently running.
     *
     * @return the ID of the currently running (resumed) Activity, or null if
     *         there is not one
     *
     * @see #startActivity
     * @see #getCurrentActivity
     */
    public String getCurrentId() {
        return mResumed != null ? mResumed.id : null;
    }

    /**
     * Return the Activity object associated with a string ID.
     *
     * @see #startActivity
     *
     * @return the associated Activity object, or null if the id is unknown or
     *         its activity is not currently instantiated
     */
    public Activity getActivity(String id) {
        LocalActivityRecord r = mActivities.get(id);
        return r != null ? r.activity : null;
    }

    /**
     * Restore a state that was previously returned by {@link #saveInstanceState}.  This
     * adds to the activity group information about all activity IDs that had
     * previously been saved, even if they have not been started yet, so if the
     * user later navigates to them the correct state will be restored.
     *
     * <p>Note: This does <b>not</b> change the current running activity, or
     * start whatever activity was previously running when the state was saved.
     * That is up to the client to do, in whatever way it thinks is best.
     *
     * @param state a previously saved state; does nothing if this is null
     *
     * @see #saveInstanceState
     */
    public void dispatchCreate(Bundle state) {
        if (state != null) {
            for (String id : state.keySet()) {
                try {
                    final Bundle astate = state.getBundle(id);
                    LocalActivityRecord r = mActivities.get(id);
                    if (r != null) {
                        r.instanceState = astate;
                    } else {
                        r = new LocalActivityRecord(id, null);
                        r.instanceState = astate;
                        mActivities.put(id, r);
                        mActivityArray.add(r);
                    }
                } catch (Exception e) {
                    // Recover from -all- app errors.
                    Log.e(TAG, "Exception thrown when restoring LocalActivityManager state", e);
                }
            }
        }

        mCurState = CREATED;
    }

    /**
     * Retrieve the state of all activities known by the group.  For
     * activities that have previously run and are now stopped or finished, the
     * last saved state is used.  For the current running activity, its
     * {@link Activity#onSaveInstanceState} is called to retrieve its current state.
     *
     * @return a Bundle holding the newly created state of all known activities
     *
     * @see #dispatchCreate
     */
    public Bundle saveInstanceState() {
        Bundle state = null;

        // FIXME: child activities will freeze as part of onPaused. Do we
        // need to do this here?
        final int N = mActivityArray.size();
        for (int i=0; i<N; i++) {
            final LocalActivityRecord r = mActivityArray.get(i);
            if (state == null) {
                state = new Bundle();
            }
            if ((r.instanceState != null || r.curState == RESUMED)
                    && r.activity != null) {
                // We need to save the state now, if we don't currently
                // already have it or the activity is currently resumed.
                final Bundle childState = new Bundle();
//                r.activity.performSaveInstanceState(childState);
                try {
                    Activity_performSaveInstanceState.invoke(r.activity, childState);
                }catch (InvocationTargetException e){
                    throw new RuntimeException(e.getTargetException());
                }
                r.instanceState = childState;
            }
            if (r.instanceState != null) {
                state.putBundle(r.id, r.instanceState);
            }
        }

        return state;
    }

    /**
     * Called by the container activity in its {@link Activity#onResume} so
     * that LocalActivityManager can perform the corresponding action on the
     * activities it holds.
     *
     * @see Activity#onResume
     */
    public void dispatchResume() {
        mCurState = RESUMED;
        if (mSingleMode) {
            if (mResumed != null) {
                moveToState(mResumed, RESUMED);
            }
        } else {
            final int N = mActivityArray.size();
            for (int i=0; i<N; i++) {
                moveToState(mActivityArray.get(i), RESUMED);
            }
        }
    }

    public void dispatchStart(){
        if (mSingleMode) {
            if (mResumed != null) {
                try {
                    Activity_onStart.invoke(mResumed.activity);
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
        } else {
            final int N = mActivityArray.size();
            for (int i=0; i<N; i++) {
                try {
                    Activity_onStart.invoke(mActivityArray.get(i).activity);
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void fakeStop(){
        if (mSingleMode) {
            if (mResumed != null) {
                try {
                    Activity_onStop.invoke(mResumed.activity);
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
        } else {
            final int N = mActivityArray.size();
            for (int i=0; i<N; i++) {
                try {
                    Activity_onStop.invoke(mActivityArray.get(i).activity);
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Called by the container activity in its {@link Activity#onPause} so
     * that LocalActivityManager can perform the corresponding action on the
     * activities it holds.
     *
     * @param finishing set to true if the parent activity has been finished;
     *                  this can be determined by calling
     *                  Activity.isFinishing()
     *
     * @see Activity#onPause
     * @see Activity#isFinishing
     */
    public void dispatchPause(boolean finishing) {
        if (finishing) {
            mFinishing = true;
        }
        mCurState = STARTED;
        if (mSingleMode) {
            if (mResumed != null) {
                moveToState(mResumed, STARTED);
            }
        } else {
            final int N = mActivityArray.size();
            for (int i=0; i<N; i++) {
                LocalActivityRecord r = mActivityArray.get(i);
                if (r.curState == RESUMED) {
                    moveToState(r, STARTED);
                }
            }
        }
    }

    /**
     * Called by the container activity in its {@link Activity#onStop} so
     * that LocalActivityManager can perform the corresponding action on the
     * activities it holds.
     *
     * @see Activity#onStop
     */
    public void dispatchStop() {
        mCurState = CREATED;
        final int N = mActivityArray.size();
        for (int i=0; i<N; i++) {
            LocalActivityRecord r = mActivityArray.get(i);
            moveToState(r, CREATED);
        }
    }

    /**
     * Call onRetainNonConfigurationInstance on each child activity and store the
     * results in a HashMap by id.  Only construct the HashMap if there is a non-null
     * object to store.  Note that this does not support nested ActivityGroups.
     *
     * {@hide}
     */
    public HashMap<String,Object> dispatchRetainNonConfigurationInstance() {
        HashMap<String,Object> instanceMap = null;

        final int N = mActivityArray.size();
        for (int i=0; i<N; i++) {
            LocalActivityRecord r = mActivityArray.get(i);
            if ((r != null) && (r.activity != null)) {
                Object instance = r.activity.onRetainNonConfigurationInstance();
                if (instance != null) {
                    if (instanceMap == null) {
                        instanceMap = new HashMap<String,Object>();
                    }
                    instanceMap.put(r.id, instance);
                }
            }
        }
        return instanceMap;
    }

    /**
     * Remove all activities from this LocalActivityManager, performing an
     * {@link Activity#onDestroy} on any that are currently instantiated.
     */
    public void removeAllActivities() {
        dispatchDestroy(true);
    }

    /**
     * Called by the container activity in its {@link Activity#onDestroy} so
     * that LocalActivityManager can perform the corresponding action on the
     * activities it holds.
     *
     * @see Activity#onDestroy
     */
    public void dispatchDestroy(boolean finishing) {
        final int N = mActivityArray.size();
        for (int i=0; i<N; i++) {
            LocalActivityRecord r = mActivityArray.get(i);
            if (localLOGV) Log.v(TAG, r.id + ": destroying");
//            mActivityThread.performDestroyActivity(r, finishing);
            try {
                if (Build.VERSION.SDK_INT >= 28) {
                    ActivityThread_performDestroyActivity.invoke(mActivityThread, r, finishing, 0,
                            false, "destroy");
                } else {
                    ActivityThread_performDestroyActivity.invoke(mActivityThread, r, finishing);

                }

            }catch (InvocationTargetException e){
                throw new RuntimeException(e.getTargetException());
            }
        }
        mActivities.clear();
        mActivityArray.clear();
    }

    public void switchToChildActivity(String key){
        LocalActivityRecord r = mActivities.get(key);
        if(r!=null){
            dispatchPause(false);
            fakeStop();
            mResumed = r;
            dispatchStart();
            dispatchResume();
        }
    }
}
