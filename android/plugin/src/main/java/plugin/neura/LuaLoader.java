package plugin.neura;

//import com.ansca.corona.CoronaActivity;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import com.ansca.corona.CoronaActivity;
import com.ansca.corona.CoronaEnvironment;
import com.ansca.corona.CoronaLua;
import com.ansca.corona.CoronaRuntime;
import com.ansca.corona.CoronaRuntimeListener;
import com.ansca.corona.CoronaRuntimeTask;
import com.ansca.corona.CoronaRuntimeTaskDispatcher;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.iid.FirebaseInstanceId;
import com.naef.jnlua.JavaFunction;
import com.naef.jnlua.LuaState;
import com.naef.jnlua.NamedJavaFunction;

import com.neura.resources.data.PickerCallback;
import com.neura.resources.device.Capability;
import com.neura.resources.device.DevicesRequestCallback;
import com.neura.resources.device.DevicesResponseData;
import com.neura.resources.insights.DailySummaryCallbacks;
import com.neura.resources.insights.DailySummaryData;
import com.neura.resources.insights.SleepProfileCallbacks;
import com.neura.resources.insights.SleepProfileData;
import com.neura.resources.place.PlaceNode;
import com.neura.resources.situation.SituationCallbacks;
import com.neura.resources.situation.SituationData;
import com.neura.resources.user.UserDetails;
import com.neura.resources.user.UserDetailsCallbacks;
import com.neura.resources.user.UserPhone;
import com.neura.resources.user.UserPhoneCallbacks;
import com.neura.sdk.callbacks.GetPermissionsRequestCallbacks;
import com.neura.sdk.object.AppSubscription;
import com.neura.sdk.object.AuthenticationRequest;
import com.neura.sdk.object.Permission;
import com.neura.sdk.service.GetSubscriptionsCallbacks;
import com.neura.standalonesdk.service.NeuraApiClient;
import com.neura.standalonesdk.util.Builder;
import com.neura.resources.authentication.AuthenticateCallback;
import com.neura.resources.authentication.AuthenticateData;
import com.neura.sdk.service.SubscriptionRequestCallbacks;
import com.neura.sdk.object.EventDefinition;
import com.neura.standalonesdk.util.SDKUtils;


import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;


import java.util.Calendar;
import android.app.AlarmManager;
import com.ansca.corona.storage.ResourceServices;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.content.Intent;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.app.Notification;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

@SuppressWarnings("WeakerAccess")
public class LuaLoader implements JavaFunction, CoronaRuntimeListener {
	// Neura
	private NeuraApiClient mNeuraApiClient;

	static private int fListener;

	static private CoronaRuntimeTaskDispatcher fDispatcher;

	/** This corresponds to the event name, e.g. [Lua] event.name */
	private static final String PLUGIN_NAME = "neura";
	public static final String PLUGIN_VERSION = "1.0.4";

    public static final String ACTION_1 = "pressOK";
    public static final String ACTION_2 = "pressSnooze";



    //Neura alarm types
    /*	1-3 = actual alarms
		4-6 = snooze alarms
		7-9 = temp alarms

		go together like so: 1/4/7,  2/5/8,  3/6/9 
		//alarm 1 being snoozed would activate alarm 4, with alarm 7 being used if a 3rd alarm is ever needed for that particular notification
    */

	@SuppressWarnings("unused")
	public LuaLoader() {
		// Initialize member variables.
		fListener = CoronaLua.REFNIL;

		// Set up this plugin to listen for Corona runtime events to be received by methods
		// onLoaded(), onStarted(), onSuspended(), onResumed(), and onExiting().
		CoronaEnvironment.addRuntimeListener(this);
	}

	@Override
	public int invoke(LuaState L) {
		fDispatcher = new CoronaRuntimeTaskDispatcher( L );
		// Register this plugin into Lua with the following functions.
		NamedJavaFunction[] luaFunctions = new NamedJavaFunction[] {
				new ConnectWrapper(),
				new DisconnectWrapper(),
				new AuthenticateWrapper(),
				new AddDeviceWrapper(),
				new AddPlaceWrapper(),
				new EnableAutomaticallySyncLogsWrapper(),
				new EnableNeuraHandingStateAlertMessagesWrapper(),
				new ForgetMeWrapper(),
				new GetAppPermissionsWrapper(),
				new GetDailySummaryWrapper(),
				new GetKnownCapabilitiesWrapper(),
				new GetKnownDevicesWrapper(),
				new GetLocationBasedEventsWrapper(),
				new GetMissingDataForEventWrapper(),
				new GetPermissionStatusWrapper(),
				new GetSdkVersionWrapper(),
				new GetSleepProfileWrapper(),
				new GetSubscriptionsWrapper(),
				new GetUserDetailsWrapper(),
				new GetUserPhoneWrapper(),
				new GetUserPlaceByLabelTypeWrapper(),
				new GetUserSituationWrapper(),
				new HasDeviceWithCapabilityWrapper(),
				new IsLoggedInWrapper(),
				new IsMissingDataForEventWrapper(),
				new RegisterFirebaseTokenWrapper(),
				new RemoveSubscriptionWrapper(),
				new SendFeedbackOnEventWrapper(),
				new ShouldSubscribeToEventWrapper(),
				new SimulateAnEventWrapper(),
				new SubscribeToEventWrapper(),
				new RegisterNotificationForEventWrapper(),
				new UnregisterNotificationForEventWrapper(),
				new SetReminderWrapper(),
				new CancelReminderWrapper(),
				new SnoozeReminderWrapper(),
				new ClearNotificationWrapper(),
				new GetPluginVersionWrapper(),


		};
		String libName = L.toString( 1 );
		L.register(libName, luaFunctions);

		// Returning 1 indicates that the Lua require() function will return the above Lua library.
		return 1;
	}

	/*public boolean doesAlarmExist(int id){
		boolean doesExist = false;

		CoronaActivity activity = CoronaEnvironment.getCoronaActivity();
		if (activity != null){
			Context context = activity.getApplicationContext();
			Intent intent = new Intent(context, NeuraAlarm.class);
			//intent.setAction(Intent.ACTION_VIEW);
			PendingIntent test = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_NO_CREATE);
			if (test != null){
				doesExist = true;
			}
		}
		return doesExist;
	}*/


	public static class NeuraAlarmService extends IntentService
	{

		/** 
		* A constructor is required, and must call the super IntentService(String)
		* constructor with a name for the worker thread.
		*/

		public NeuraAlarmService() {
		  super("NeuraAlarmService");
		}

		/**
		* The IntentService calls this method from the default worker thread with
		* the intent that started the service. When this method returns, IntentService
		* stops the service, as appropriate.
		*/

		@Override
		protected void onHandleIntent(Intent theIntent) {

			int notificationCode= theIntent.getIntExtra("notificationCode", 1);
       		String notificationType = theIntent.getStringExtra("notificationType");
       		int repeatingDays = theIntent.getIntExtra("repeatingDays", 0);
       		int isSnooze = theIntent.getIntExtra("isSnooze", 0);

       		Context context = getApplicationContext();

       		SharedPreferences mPrefs = context.getSharedPreferences("neuraplugin", 0);
       		SharedPreferences.Editor mEditor = mPrefs.edit();
			boolean isDebug = mPrefs.getBoolean("isDebug", false);

			Calendar now = Calendar.getInstance();
	    	now.setTimeInMillis(System.currentTimeMillis());

	    	long lastShown = mPrefs.getLong(notificationType+"LastShownTime", System.currentTimeMillis()-86400000); //86400000 = milliseconds in one day
	    	Calendar lastShownCalendar = Calendar.getInstance();
	    	lastShownCalendar.setTimeInMillis(lastShown);

			
       		if (notificationCode == 2 || notificationCode == 3)
       		{					
				mEditor.putBoolean("canCheckAlarm"+notificationCode, true);
       		}
       		else
       		{

       			boolean canShow = false;

       			if (notificationType == "pill"){
       				if (isDebug == true){
	       				Log.d("Corona", "pill reminder type can always be shown");
	       			}
       				canShow = true;
       			}
       			else if(isSnooze > 0 ){
       				if (isDebug == true){
	       				Log.d("Corona", notificationType + ": this is a snoozed alarm so can always be shown");
	       			}
       				canShow = true;
       			}
       			else if (lastShown.get(Calendar.YEAR) != now.get(Calendar.YEAR) || lastShown.get(Calendar.DAY_OF_YEAR) != now.get(Calendar.DAY_OF_YEAR)) {
       				canShow = true;	
       				if (isDebug == true){
	       				Log.d("Corona", notificationType + " reminder type has not been shown today, so can show now");
	       			}
       			}
       			else {
       				canShow = false;

       				if (isDebug == true){
	       				Log.d("Corona", notificationType + " reminder type has already been shown today, and is not a snooze reminder");
	       			}
       			}

       			if (canShow == true){

					NotificationCompat.Builder builder = new NotificationCompat.Builder(context);

					ResourceServices resourceServices = new ResourceServices(context);
					Intent action1Intent = new Intent(context, NotificationActionService.class)
					    .setAction(ACTION_1+notificationType);
					Bundle intent1Bundle = new Bundle();            
					intent1Bundle.putInt("clickIndex", 1);
					intent1Bundle.putInt("notificationCode", notificationCode);
					intent1Bundle.putString("notificationType", notificationType);
					action1Intent.putExtras(intent1Bundle);

					PendingIntent action1PendingIntent = PendingIntent.getService(context, 0,
					        action1Intent, PendingIntent.FLAG_UPDATE_CURRENT);

					Intent action2Intent = new Intent(context, NotificationActionService.class)
					    .setAction(ACTION_2+notificationType);

					Bundle intent2Bundle = new Bundle();            
					intent2Bundle.putInt("clickIndex", 2);
					intent2Bundle.putInt("notificationCode", notificationCode);
					intent2Bundle.putString("notificationType", notificationType);
					action2Intent.putExtras(intent2Bundle);

					PendingIntent action2PendingIntent = PendingIntent.getService(context, 0,
					        action2Intent, PendingIntent.FLAG_UPDATE_CURRENT);

					String bodyText = "";
					String button1Text = "Took Pill";

					String username = mPrefs.getString("username", "you");

					//make first letter of notification type upper case
					String notificationTitle = notificationType.substring(0,1).toUpperCase() + notificationType.substring(1).toLowerCase();
					int numDays = repeatingDays; 

					if (notificationType.equals("period")){
						numDays = mPrefs.getInt("periodRepeatingDays", 0);

						mEditor.putLong("periodLastShownTime", System.currentTimeMillis());

					} else if (notificationType.equals("ovulation")){
						numDays = mPrefs.getInt("ovulationRepeatingDays", 0);

						mEditor.putLong("ovulationLastShownTime", System.currentTimeMillis());
					}


			

					String timetext = "in "+numDays+" days";
					if (numDays == 1){
						timetext = "tomorrow";
					}else if (numDays == 0){
						timetext = "today";
					}

					/*“Period is expected to start in %N% days for %user%”
						If1day=“in%N%days”>“tomorrow”
						If0days=“in%N%days”>“today”*/

					/*	
						“Ovulation is expected to start in %N% days for %user%”
						 If1day=“in%N%days”>“tomorrow” o If0days=“in%N%days”>“today”
					*/


					if (notificationType.equals("pill")) {
						bodyText = "Time for " + username + " to take a pill";

						mEditor.putBoolean("isSnooze1", false);
						mEditor.putLong("snoozeStartTime1", 0);

						//NeuraEventsService.snoozeArray[0] = false;
						//NeuraEventsService.snoozeStartTime[0] = 0;
					} else if (notificationType.equals("period")) {
						bodyText = "Period is expected to start "+ timetext + " for " + username;
						button1Text = "Dismiss";
						mEditor.putBoolean("isSnooze2", false);
						mEditor.putLong("snoozeStartTime2", 0);

						//NeuraEventsService.snoozeArray[1] = false;
						//NeuraEventsService.snoozeStartTime[1] = 0;
					} else if (notificationType.equals("ovulation")) {
						bodyText = "Ovulation is expected to start "+ timetext + " for " + username;
						button1Text = "Dismiss";

						mEditor.putBoolean("isSnooze3", false);
						mEditor.putLong("snoozeStartTime3", 0);

						//NeuraEventsService.snoozeArray[2] = false;
						//NeuraEventsService.snoozeStartTime[2] = 0;
					}


					Intent deleteIntent = new Intent(context, NotificationDeleteService.class);

					Bundle deleteIntentBundle = new Bundle();            
					deleteIntentBundle.putInt("clickIndex", 2);
					deleteIntentBundle.putInt("notificationCode", notificationCode);
					deleteIntentBundle.putString("notificationType", notificationType);
					deleteIntent.putExtras(deleteIntentBundle);

					PendingIntent deletePendingIntent = PendingIntent.getService(context, 0, deleteIntent, PendingIntent.FLAG_UPDATE_CURRENT);

					
					//Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
					//.setSound(alarmSound) 

					NotificationCompat.Builder notificationBuilder =
					        new NotificationCompat.Builder(context)
					                .setSmallIcon(resourceServices.getDrawableResourceId("corona_statusbar_icon_default"))
					                .setContentTitle(notificationTitle + " reminder")
					                .setPriority(Notification.PRIORITY_MAX)
					                .setContentText(bodyText)
					                .setTicker(bodyText)
					                .setDefaults(Notification.DEFAULT_SOUND)
					                .setAutoCancel(true)
					                .setDeleteIntent(deletePendingIntent)
					                .addAction(new NotificationCompat.Action(resourceServices.getDrawableResourceId("neura_sdk_symbol"), button1Text, action1PendingIntent))
					               	.addAction(new NotificationCompat.Action(resourceServices.getDrawableResourceId("corona_statusbar_icon_default"), "Snooze", action2PendingIntent))
					               	.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

					

					NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
					Notification notif = notificationBuilder.build();
		    		notif.flags |= Notification.FLAG_AUTO_CANCEL;
					notificationManager.notify(notificationCode, notif);


					HashMap<String, Object> params = new HashMap<>();
					params.put("type", "Success");
					Hashtable<String, String> eventData = new Hashtable<String, String>();
					eventData.put("eventName", "notificationTriggered");
					eventData.put("notificationType", notificationType);
					params.put("event", eventData);
					dispatch(params, "notificationTriggered", fListener);

					/*Intent alarmIntent = new Intent(this, NeuraAlarm.class);
					long scTime = 60*1000;//mins
					PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, alarmIntent, 0);
					AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
					alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + scTime, pendingIntent);*/

					//wake the screen for 3 seconds when notification is received
			        int seconds = 3;
			        PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
			        boolean isScreenOn = pm.isScreenOn();

			        if( !isScreenOn )
			        {
				        WakeLock wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK |PowerManager.ACQUIRE_CAUSES_WAKEUP |PowerManager.ON_AFTER_RELEASE,"MyLock");
				        wl.acquire(seconds*1000);
				        WakeLock wl_cpu = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"MyCpuLock");
				        wl_cpu.acquire(seconds*1000);
			        }
			    }
			}

			mEditor.commit();

			stopService(theIntent);
		}
    }

	public static class NeuraAlarm extends BroadcastReceiver
    {
    	public NeuraAlarm() {
		  //super(NeuraAlarm.class.getSimpleName());
		}

       	@Override
       	public void onReceive(Context context, Intent alarmIntent) {


       		int notificationCode= alarmIntent.getIntExtra("notificationCode", 1);
       		String notificationType = alarmIntent.getStringExtra("notificationType");

       		//String isSnooze = alarmIntent.getStringExtra("isSnooze");
       		int repeatingDays = alarmIntent.getIntExtra("repeatingDays", 0);

       		//Context applicationContext = context.getApplicationContext();
       		SharedPreferences mPrefs = context.getSharedPreferences("neuraplugin", 0);
       		SharedPreferences.Editor mEditor = mPrefs.edit();
			boolean isDebug = mPrefs.getBoolean("isDebug", false);


			if (notificationType.equals("period")){
				mEditor.putInt("periodRepeatingDays", repeatingDays);

			} else if (notificationType.equals("ovulation")){
				mEditor.putInt("ovulationRepeatingDays", repeatingDays);
			}
	
        	//when repeatingDays = -1 then it is to be repeated indefinitely
        	if (repeatingDays < 0){
        		Calendar calendar = Calendar.getInstance();
				calendar.setTimeInMillis(System.currentTimeMillis());
				calendar.add(Calendar.DATE, 1);

				Intent repeatAlarmIntent = new Intent(context, NeuraAlarm.class);
				repeatAlarmIntent.putExtra("notificationCode",notificationCode);
				repeatAlarmIntent.putExtra("notificationType",notificationType);
				//repeatAlarmIntent.putExtra("isSnooze",isSnooze);
				repeatAlarmIntent.putExtra("repeatingDays", repeatingDays);
			    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, notificationCode, repeatAlarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
			    AlarmManager alarmManager = (AlarmManager) context.getSystemService(context.ALARM_SERVICE);

			    alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);

        	} else if (repeatingDays > 0){
        		//when repeatingDays > 0 set a reminder for the next day and decrease repeatingDays by 1

        		repeatingDays = repeatingDays - 1;
        		Calendar calendar = Calendar.getInstance();
				calendar.setTimeInMillis(System.currentTimeMillis());

				/*if (isDebug == true){
					calendar.add(Calendar.MINUTE, 1);
				}
				else {*/
					calendar.add(Calendar.DATE, 1);//add one day
				//}

				
				Intent repeatAlarmIntent = new Intent(context, NeuraAlarm.class);
				repeatAlarmIntent.putExtra("notificationCode",notificationCode);
				repeatAlarmIntent.putExtra("notificationType",notificationType);
				//repeatAlarmIntent.putExtra("isSnooze",isSnooze);
				repeatAlarmIntent.putExtra("repeatingDays", repeatingDays);
			    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, notificationCode, repeatAlarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
			    AlarmManager alarmManager = (AlarmManager) context.getSystemService(context.ALARM_SERVICE);

			    alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
        	}



			mEditor.commit();

        	Intent i = new Intent(context, NeuraAlarmService.class);
        	i.putExtra("notificationCode",notificationCode);
			i.putExtra("notificationType",notificationType);
			i.putExtra("repeatingDays",repeatingDays);
        	context.startService(i);
       	}
    }

    public static class NotificationDeleteService extends IntentService {
        public NotificationDeleteService() {
            super(NotificationDeleteService.class.getSimpleName());
        }

        @Override
        protected void onHandleIntent(Intent notificationIntent) {

            String action = notificationIntent.getAction();
            int notificationCode= notificationIntent.getIntExtra("notificationCode", 1);
       		String notificationType = notificationIntent.getStringExtra("notificationType");

       		HashMap<String, Object> params = new HashMap<>();
			params.put("type", "Success");
			Hashtable<String, String> eventData = new Hashtable<String, String>();
			eventData.put("eventName", "recordNotificationInteraction");
			eventData.put("notificationType", notificationType);
			eventData.put("interactionType", "dismissed");
			params.put("event", eventData);
			dispatch(params, "recordNotificationInteraction", fListener);
 
    	}
	}


    public static class NotificationActionService extends IntentService {
        public NotificationActionService() {
            super(NotificationActionService.class.getSimpleName());
        }

        @Override
        protected void onHandleIntent(Intent notificationIntent) {

            String action = notificationIntent.getAction();
            int notificationCode= notificationIntent.getIntExtra("notificationCode", 1);
       		String notificationType = notificationIntent.getStringExtra("notificationType");

            int id_to_cancel = 0;

            if (action.equals(ACTION_1+"pill")) {
            	NotificationManagerCompat.from(this).cancel(1);//actual alarm notification
            	NotificationManagerCompat.from(this).cancel(4);//snooze notification
       			if (notificationType.equals("pill")){

       				HashMap<String, Object> params = new HashMap<>();
					params.put("type", "Success");
					Hashtable<String, String> eventData = new Hashtable<String, String>();
					eventData.put("eventName", "recordNotificationInteraction");
					eventData.put("notificationType", notificationType);
					eventData.put("interactionType", "clickedOK");
					params.put("event", eventData);
					dispatch(params, "recordNotificationInteraction", fListener);

	                Intent coronaIntent = new Intent(this, com.ansca.corona.CoronaActivity.class);
					coronaIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

					Bundle coronaIntentBundle = new Bundle();  
					coronaIntentBundle.putInt("clickIndex", 1);
					coronaIntentBundle.putInt("notificationCode", notificationCode);
					coronaIntentBundle.putString("notificationType", notificationType);          
				    coronaIntent.putExtras(coronaIntentBundle);

					startActivity(coronaIntent);
				}
				else
				{
					Log.d("Corona", "Dismissed Notification");
				}
            } else if (action.equals(ACTION_1+"period")){

            	HashMap<String, Object> params = new HashMap<>();
				params.put("type", "Success");
				Hashtable<String, String> eventData = new Hashtable<String, String>();
				eventData.put("eventName", "recordNotificationInteraction");
				eventData.put("notificationType", notificationType);
				eventData.put("interactionType", "clickedOK");
				params.put("event", eventData);
				dispatch(params, "recordNotificationInteraction", fListener);

            	NotificationManagerCompat.from(this).cancel(2);//actual alarm notification
            	NotificationManagerCompat.from(this).cancel(5);//snooze notification
            } else if (action.equals(ACTION_1+"ovulation")){

            	HashMap<String, Object> params = new HashMap<>();
				params.put("type", "Success");
				Hashtable<String, String> eventData = new Hashtable<String, String>();
				eventData.put("eventName", "recordNotificationInteraction");
				eventData.put("notificationType", notificationType);
				eventData.put("interactionType", "clickedOK");
				params.put("event", eventData);
				dispatch(params, "recordNotificationInteraction", fListener);

            	NotificationManagerCompat.from(this).cancel(3);//actual alarm notification
            	NotificationManagerCompat.from(this).cancel(6);//snooze notification

            }else{
            	String newNotificationType = action.replace(ACTION_2, "");

				Context context = this;//activity.getApplicationContext();

				SharedPreferences mPrefs = context.getSharedPreferences("neuraplugin", 0);
				SharedPreferences.Editor mEditor = mPrefs.edit();

				HashMap<String, Object> params = new HashMap<>();
				params.put("type", "Success");
				Hashtable<String, String> eventData = new Hashtable<String, String>();
				eventData.put("eventName", "recordNotificationInteraction");
				eventData.put("notificationType", newNotificationType);
				eventData.put("interactionType", "snooze");
				params.put("event", eventData);
				dispatch(params, "recordNotificationInteraction", fListener);


            	final int alarm_broadcast_ID;
				if (newNotificationType.equals("pill")){
					NotificationManagerCompat.from(this).cancel(1);//actual alarm notification
            		NotificationManagerCompat.from(this).cancel(4);//snooze notification
            		//NeuraEventsService.snoozeArray[0] = true;
            		//NeuraEventsService.snoozeStartTime[0] = System.currentTimeMillis();
					mEditor.putBoolean("isSnooze1", true);
					mEditor.putLong("snoozeStartTime1", System.currentTimeMillis());

				    alarm_broadcast_ID = 4;
				} else if (newNotificationType.equals("period")){
					NotificationManagerCompat.from(this).cancel(2);//actual alarm notification
            		NotificationManagerCompat.from(this).cancel(5);//snooze notification
            		//NeuraEventsService.snoozeArray[1] = true;
            		//NeuraEventsService.snoozeStartTime[1] = System.currentTimeMillis();
					mEditor.putBoolean("isSnooze2", true);
					mEditor.putLong("snoozeStartTime2", System.currentTimeMillis());

					alarm_broadcast_ID = 5;
				} else if (newNotificationType.equals("ovulation")){
					alarm_broadcast_ID = 6;
					NotificationManagerCompat.from(this).cancel(3);//actual alarm notification
            		NotificationManagerCompat.from(this).cancel(6);//snooze notification
            		//NeuraEventsService.snoozeArray[2] = true;
            		//NeuraEventsService.snoozeStartTime[2] = System.currentTimeMillis();
					mEditor.putBoolean("isSnooze3", true);
					mEditor.putLong("snoozeStartTime3", System.currentTimeMillis());
				} else {
				    alarm_broadcast_ID = 0;
				}
				//cal.set(Calendar.SECOND, 0);
		    	//cal.set(Calendar.MILLISECOND, 0);

				if (alarm_broadcast_ID > 0)
				{

					Intent alarmIntent = new Intent(this, NeuraAlarm.class);
					alarmIntent.putExtra("notificationCode",alarm_broadcast_ID);
					alarmIntent.putExtra("notificationType",newNotificationType);
					alarmIntent.putExtra("snoozeStartTime", System.currentTimeMillis());
					//alarmIntent.putExtra("isSnooze","yes");
				    PendingIntent pendingIntent = PendingIntent.getBroadcast(this, alarm_broadcast_ID, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
				    AlarmManager alarmManager = (AlarmManager) this.getSystemService(this.ALARM_SERVICE);

			    	Calendar calendar = Calendar.getInstance();
			    	calendar.setTimeInMillis(System.currentTimeMillis());

					// add 25/60 mins to the calendar object
					if (alarm_broadcast_ID < 5)
					{
						calendar.add(Calendar.MINUTE, 25);	
					}
					else
					{
						calendar.add(Calendar.MINUTE, 60);	
					}


					

					//only set the next snooze alarm if it will happen on the same day, i.e. will not be later than 23:59 on the day the snooze is set
					Calendar now = Calendar.getInstance();
			    	now.setTimeInMillis(System.currentTimeMillis());
					if (calendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) && calendar.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)) {
			    		alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
			    	}
			    }

			    mEditor.commit();
            }
    	}

    	/*@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
		public static void cancelSnooze(int i){
			Context context = getActivity().getApplicationContext();
			Intent alarmIntent = new Intent(context, NeuraAlarm.class);
		    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, i, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		    AlarmManager alarmManager = (AlarmManager) context.getSystemService(context.ALARM_SERVICE);
	    	alarmManager.cancel(pendingIntent);
		}*/
	}


    


	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int setReminder(LuaState L) {

		int hour = 8;
		int minute = 0;
		int second = 0;
		int day = Calendar.getInstance().get(Calendar.DATE);
		int month = Calendar.getInstance().get(Calendar.MONTH);
		int year = Calendar.getInstance().get(Calendar.YEAR);
		boolean repeatDaily = false;
		String reminderType = "";
		int repeatingDays = 0;
		String username = "you";
		boolean isDebug = false;


        // If an options table has been passed
        if ( L.isTable( -1 ) )
        {
            // Get the app key
            L.getField( -1, "reminderType" );
            if ( L.isString( -1 ) )
            {
                reminderType = L.checkString( -1 );
            }
            else
            {
                System.out.println( "Error: reminderType expected, got " + L.typeName( -1 ) );
            }
            L.pop( 1 );

            // Get the app key
            L.getField( -1, "username" );
            if ( L.isString( -1 ) )
            {
                username = L.checkString( -1 );
            }
            L.pop( 1 );

            // Get the app key
            L.getField( -1, "repeatDaily" );
            if ( L.isBoolean( -1 ) )
            {
                repeatDaily = L.checkBoolean( -1 );
            }
            L.pop( 1 );

            L.getField( -1, "repeatingDays" );
            if ( L.isNumber( -1 ) )
            {
                repeatingDays = (int) L.checkNumber( -1 );
            }
            L.pop( 1 );

            // Get the app key
            L.getField( -1, "isDebug" );
            if ( L.isBoolean( -1 ) )
            {
                isDebug = L.checkBoolean( -1 );
            }
            L.pop( 1 );

            // Get the app key
            L.getField( -1, "hour" );
            if ( L.isNumber( -1 ) )
            {
                hour = (int) L.checkNumber( -1 );
            }
            L.pop( 1 );

            // Get the app key
            L.getField( -1, "minute" );
            if ( L.isNumber( -1 ) )
            {
                minute = (int) L.checkNumber( -1 );
            }
            L.pop( 1 );

            // Get the app key
            L.getField( -1, "second" );
            if ( L.isNumber( -1 ) )
            {
                second = (int) L.checkNumber( -1 );
            }
            L.pop( 1 );

            // Get the app key
            L.getField( -1, "day" );
            if ( L.isNumber( -1 ) )
            {
                day = (int) L.checkNumber( -1 );
            }
            L.pop( 1 );

            // Get the app key
            L.getField( -1, "month" );
            if ( L.isNumber( -1 ) )
            {
                month = (int) L.checkNumber( -1 );
            }
            L.pop( 1 );

            // Get the app key
            L.getField( -1, "year" );
            if ( L.isNumber( -1 ) )
            {
                year = (int) L.checkNumber( -1 );
            }
            L.pop( 1 );

        }

		if (reminderType.equals("")){
			Log.e("Corona", "neura.setReminder() takes table as first argument with reminderType as required key.");
			return 0;
		}

		final int alarm_broadcast_ID;
		String isSuccess = "Success";
		if (reminderType.equals("pill")){
		    alarm_broadcast_ID = 1;
		} else if (reminderType.equals("period")){
			alarm_broadcast_ID = 2;
		} else if (reminderType.equals("ovulation")){
			alarm_broadcast_ID = 3;
		} else {
		    alarm_broadcast_ID = 0;
		    isSuccess = "Error";
		}

		if (alarm_broadcast_ID > 0)
		{
	    	Calendar calendar = Calendar.getInstance();
			calendar.setTimeInMillis(System.currentTimeMillis());
			calendar.set(Calendar.DATE, day);
			calendar.set(Calendar.MONTH, month);
			calendar.set(Calendar.YEAR, year);
			calendar.set(Calendar.HOUR_OF_DAY, hour);
			calendar.set(Calendar.MINUTE, minute);
			calendar.set(Calendar.SECOND, second);

			Calendar now = Calendar.getInstance();
			now.setTimeInMillis(System.currentTimeMillis());

			/*if (isDebug == true)
			{
				long diff = calendar.getTimeInMillis() - now.getTimeInMillis();
				Log.d("Corona", "alarm will be activated in " + diff + "ms");
			}*/
			//if today's alarm time has already passed, set it for tomorrow instead
			//not needed for period and ovulation reminder, since they are initially triggered by neura events rather than a fixed time
			
			if (calendar.getTimeInMillis() < now.getTimeInMillis() && reminderType.equals("pill")){

				calendar.add(Calendar.DATE, 1);

				/*if (isDebug == true)
				{
					Log.d("Corona", "Already passed alarm time, set for tomorrow instead");
				}*/
			}
			


			CoronaActivity activity = CoronaEnvironment.getCoronaActivity();
			Context context = activity.getApplicationContext();

			SharedPreferences mPrefs = context.getSharedPreferences("neuraplugin", 0);
			SharedPreferences.Editor mEditor = mPrefs.edit();
			mEditor.putString("username", username);
			mEditor.putBoolean("isDebug", isDebug);

			if (reminderType.equals("period")){
				mEditor.putInt("periodRepeatingDays", repeatingDays);
			} else if (reminderType.equals("ovulation")){
				mEditor.putInt("ovulationRepeatingDays", repeatingDays);
			}

			Intent alarmIntent = new Intent(context, NeuraAlarm.class);
			alarmIntent.putExtra("notificationCode",alarm_broadcast_ID);
			alarmIntent.putExtra("notificationType",reminderType);
			//alarmIntent.putExtra("isSnooze","no");
			alarmIntent.putExtra("repeatingDays", repeatingDays);

		    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, alarm_broadcast_ID, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		    AlarmManager alarmManager = (AlarmManager) context.getSystemService(context.ALARM_SERVICE);

		    if (repeatDaily)
		    {
		    	//set once, will be set again when it goes off. only way to have the alarm go off at a fixed time, 
		    	//otherwise android will trigger the alarm any time between the target time and the interval period until next alarm.
		    	alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
		    }
		    else
		    {
		    	alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
		    }

		    mEditor.commit();
		}
		else
		{
			//Log.d("Corona", "alarm_broadcast_ID is less than 1");
		}
	    
	    

		HashMap<String, Object> params = new HashMap<>();
		params.put("type", isSuccess);
		Hashtable<String, String> eventData = new Hashtable<String, String>();
		eventData.put("eventName", "setReminder");
		eventData.put("eventIdentifier", "");
		params.put("event", eventData);
		dispatch(params, "setReminder", fListener);

		return 0;
	}

	@SuppressWarnings("unused")
	private class SetReminderWrapper implements NamedJavaFunction {

		@Override
		public String getName() {
			return "setReminder";
		}

		@Override
		public int invoke(LuaState L) {
			return setReminder(L);
		}
	}

	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int cancelReminder(LuaState L) {

		String reminderType = "";


        // If an options table has been passed
        if ( L.isTable( -1 ) )
        {
            // Get the app key
            L.getField( -1, "reminderType" );
            if ( L.isString( -1 ) )
            {
                reminderType = L.checkString( -1 );
            }
            else
            {
                System.out.println( "Error: reminderType expected, got " + L.typeName( -1 ) );
            }
            L.pop( 1 );
        }

		if (reminderType.equals("")){
			Log.e("Corona", "neura.cancelReminder() takes table as first argument with reminderType as required key.");
			return 0;
		}

		String isSuccess = "Success";
		CoronaActivity activity = CoronaEnvironment.getCoronaActivity();
		Context context = activity.getApplicationContext();
		Intent alarmIntent = new Intent(context, NeuraAlarm.class);
		AlarmManager alarmManager = (AlarmManager) context.getSystemService(context.ALARM_SERVICE);

		SharedPreferences mPrefs = context.getSharedPreferences("neuraplugin", 0);
		SharedPreferences.Editor mEditor = mPrefs.edit();


		if (reminderType.equals("pill")){
		    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 1, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
	    	alarmManager.cancel(pendingIntent);
	    	PendingIntent pendingIntentSnooze = PendingIntent.getBroadcast(context, 4, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
	    	alarmManager.cancel(pendingIntentSnooze);
			mEditor.putBoolean("isSnooze1", false);
			mEditor.putBoolean("canCheckAlarm1", false);
			mEditor.putLong("snoozeStartTime1", System.currentTimeMillis());


		} else if (reminderType.equals("period")){
			PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 2, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
	    	alarmManager.cancel(pendingIntent);
	    	PendingIntent pendingIntentSnooze = PendingIntent.getBroadcast(context, 5, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
	    	alarmManager.cancel(pendingIntentSnooze);
	    	mEditor.putBoolean("isSnooze2", false);
			mEditor.putBoolean("canCheckAlarm2", false);
			mEditor.putLong("snoozeStartTime2", System.currentTimeMillis());
			mEditor.putInt("periodRepeatingDays", 0);

		} else if (reminderType.equals("ovulation")){
			PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 3, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
	    	alarmManager.cancel(pendingIntent);
	    	PendingIntent pendingIntentSnooze = PendingIntent.getBroadcast(context, 6, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
	    	alarmManager.cancel(pendingIntentSnooze);
	    	mEditor.putBoolean("isSnooze3", false);
			mEditor.putBoolean("canCheckAlarm3", false);
			mEditor.putLong("snoozeStartTime3", System.currentTimeMillis());
			mEditor.putInt("ovulationRepeatingDays", 0);

		} else {
		    isSuccess = "Error";
		}

		mEditor.commit();


		HashMap<String, Object> params = new HashMap<>();
		params.put("type", isSuccess);
		Hashtable<String, String> eventData = new Hashtable<String, String>();
		eventData.put("eventName", "cancelReminder");
		eventData.put("eventIdentifier", "");
		eventData.put("reminderType", reminderType);
		params.put("event", eventData);
		dispatch(params, "cancelReminder", fListener);

		return 0;
	}


	@SuppressWarnings("unused")
	private class CancelReminderWrapper implements NamedJavaFunction {

		@Override
		public String getName() {
			return "cancelReminder";
		}

		@Override
		public int invoke(LuaState L) {
			return cancelReminder(L);
		}
	}


	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int snoozeReminder(LuaState L) {

		String reminderType = "";


        // If an options table has been passed
        if ( L.isTable( -1 ) )
        {
            // Get the app key
            L.getField( -1, "reminderType" );
            if ( L.isString( -1 ) )
            {
                reminderType = L.checkString( -1 );
            }
            else
            {
                System.out.println( "Error: reminderType expected, got " + L.typeName( -1 ) );
            }
            L.pop( 1 );
        }

		if (reminderType.equals("")){
			Log.e("Corona", "neura.snoozeReminder() takes table as first argument with reminderType as required key.");
			return 0;
		}

		String isSuccess = "Success";
		CoronaActivity activity = CoronaEnvironment.getCoronaActivity();
		Context context = activity.getApplicationContext();

		SharedPreferences mPrefs = context.getSharedPreferences("neuraplugin", 0);
		SharedPreferences.Editor mEditor = mPrefs.edit();

    	final int alarm_broadcast_ID;
		if (reminderType.equals("pill")){
			NotificationManagerCompat.from(context).cancel(1);//actual alarm notification
    		NotificationManagerCompat.from(context).cancel(4);//snooze notification
    		//NeuraEventsService.snoozeArray[0] = true;
    		//NeuraEventsService.snoozeStartTime[0] = System.currentTimeMillis();
			mEditor.putBoolean("isSnooze1", true);
			mEditor.putLong("snoozeStartTime1", System.currentTimeMillis());

		    alarm_broadcast_ID = 4;
		} else if (reminderType.equals("period")){
			NotificationManagerCompat.from(context).cancel(2);//actual alarm notification
    		NotificationManagerCompat.from(context).cancel(5);//snooze notification
    		//NeuraEventsService.snoozeArray[1] = true;
    		//NeuraEventsService.snoozeStartTime[1] = System.currentTimeMillis();
			mEditor.putBoolean("isSnooze2", true);
			mEditor.putLong("snoozeStartTime2", System.currentTimeMillis());

			alarm_broadcast_ID = 5;
		} else if (reminderType.equals("ovulation")){
			alarm_broadcast_ID = 6;
			NotificationManagerCompat.from(context).cancel(3);//actual alarm notification
    		NotificationManagerCompat.from(context).cancel(6);//snooze notification
    		//NeuraEventsService.snoozeArray[2] = true;
    		//NeuraEventsService.snoozeStartTime[2] = System.currentTimeMillis();
			mEditor.putBoolean("isSnooze3", true);
			mEditor.putLong("snoozeStartTime3", System.currentTimeMillis());
		} else {
		    alarm_broadcast_ID = 0;
		}
		//cal.set(Calendar.SECOND, 0);
    	//cal.set(Calendar.MILLISECOND, 0);

		if (alarm_broadcast_ID > 0)
		{

			Intent alarmIntent = new Intent(context, NeuraAlarm.class);
			alarmIntent.putExtra("notificationCode",alarm_broadcast_ID);
			alarmIntent.putExtra("notificationType",reminderType);
			alarmIntent.putExtra("snoozeStartTime", System.currentTimeMillis());
			//alarmIntent.putExtra("isSnooze","yes");
		    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, alarm_broadcast_ID, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		    AlarmManager alarmManager = (AlarmManager) context.getSystemService(context.ALARM_SERVICE);

	    	Calendar calendar = Calendar.getInstance();
	    	calendar.setTimeInMillis(System.currentTimeMillis());

			// add 25/60 mins to the calendar object
			if (alarm_broadcast_ID < 5)
			{
				calendar.add(Calendar.MINUTE, 25);	
			}
			else
			{
				calendar.add(Calendar.MINUTE, 60);	
			}


			

			//only set the next snooze alarm if it will happen on the same day, i.e. will not be later than 23:59 on the day the snooze is set
			Calendar now = Calendar.getInstance();
	    	now.setTimeInMillis(System.currentTimeMillis());
			if (calendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) && calendar.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)) {
	    		alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
	    	}
	    }

	    mEditor.commit();


		HashMap<String, Object> params = new HashMap<>();
		params.put("type", isSuccess);
		Hashtable<String, String> eventData = new Hashtable<String, String>();
		eventData.put("eventName", "snoozeReminder");
		eventData.put("eventIdentifier", "");
		eventData.put("reminderType", reminderType);
		params.put("event", eventData);
		dispatch(params, "snoozeReminder", fListener);

		return 0;
	}


	@SuppressWarnings("unused")
	private class SnoozeReminderWrapper implements NamedJavaFunction {

		@Override
		public String getName() {
			return "snoozeReminder";
		}

		@Override
		public int invoke(LuaState L) {
			return snoozeReminder(L);
		}
	}


	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int clearNotification(LuaState L) {

		String reminderType = "";


        // If an options table has been passed
        if ( L.isTable( -1 ) )
        {
            // Get the app key
            L.getField( -1, "reminderType" );
            if ( L.isString( -1 ) )
            {
                reminderType = L.checkString( -1 );
            }
            else
            {
                System.out.println( "Error: reminderType expected, got " + L.typeName( -1 ) );
            }
            L.pop( 1 );
        }

		if (reminderType.equals("")){
			Log.e("Corona", "neura.clearNotification() takes table as first argument with reminderType as required key.");
			return 0;
		}

		String isSuccess = "Success";
		CoronaActivity activity = CoronaEnvironment.getCoronaActivity();
		Context context = activity.getApplicationContext();

		SharedPreferences mPrefs = context.getSharedPreferences("neuraplugin", 0);
		SharedPreferences.Editor mEditor = mPrefs.edit();

		if (reminderType.equals("pill")){
			NotificationManagerCompat.from(context).cancel(1);//actual alarm notification
    		NotificationManagerCompat.from(context).cancel(4);//snooze notification
    		
		} else if (reminderType.equals("period")){
			NotificationManagerCompat.from(context).cancel(2);//actual alarm notification
    		NotificationManagerCompat.from(context).cancel(5);//snooze notification
    		
		} else if (reminderType.equals("ovulation")){
			NotificationManagerCompat.from(context).cancel(3);//actual alarm notification
    		NotificationManagerCompat.from(context).cancel(6);//snooze notification
    		
		}

		HashMap<String, Object> params = new HashMap<>();
		params.put("type", isSuccess);
		Hashtable<String, String> eventData = new Hashtable<String, String>();
		eventData.put("eventName", "clearNotification");
		eventData.put("eventIdentifier", "");
		eventData.put("reminderType", reminderType);
		params.put("event", eventData);
		dispatch(params, "clearNotification", fListener);

		return 0;
	}


	@SuppressWarnings("unused")
	private class ClearNotificationWrapper implements NamedJavaFunction {

		@Override
		public String getName() {
			return "clearNotification";
		}

		@Override
		public int invoke(LuaState L) {
			return clearNotification(L);
		}
	}



	@Override
	public void onLoaded(CoronaRuntime runtime) {
	}

	@Override
	public void onStarted(CoronaRuntime runtime) {
	}

	@Override
	public void onSuspended(CoronaRuntime runtime) {
	}

	@Override
	public void onResumed(CoronaRuntime runtime) {
	}

	@Override
	public void onExiting(CoronaRuntime runtime) {
		// Remove the Lua listener reference.
		CoronaLua.deleteRef( runtime.getLuaState(), fListener );
		fListener = CoronaLua.REFNIL;
	}

	public static void dispatch(final Map<String, Object> params, final String name, int listener) {
		if (listener == CoronaLua.REFNIL) {
			listener = fListener;
		}
		final int finalListener = listener;

		CoronaRuntimeTask task = new CoronaRuntimeTask() {
			@Override
			public void executeUsing(CoronaRuntime runtime) {
				try {
					LuaState luaState = runtime.getLuaState();
					CoronaLua.newEvent(luaState, name);

					CoronaLua.pushValue(luaState, PLUGIN_NAME);
					luaState.setField(-2, "provider");

					for (String key : params.keySet()) {
						CoronaLua.pushValue(luaState, params.get(key));
						luaState.setField(-2, key);
					}
					if (finalListener > 0) {
						CoronaLua.dispatchEvent(luaState, finalListener, 0);
					}
				} catch (Exception exception) {
					Log.e("Corona", "Unable to dispatch event " + name + " with params: " + params.toString() + ". " + exception.toString());
				}
			}
		};
		if (fDispatcher != null && fDispatcher.isRuntimeAvailable()){
			fDispatcher.send(task);
		}
		else
		{
			//sometimes the runtime is not available, e.g. if this function is call as a result of a push message while the app is closed
			Log.e("Corona", "Unable to dispatch event ");
		}
	}

	public static void dispatchOnFailure(Bundle bundle, int errorCode, String eventName, int listener){
		HashMap<String, Object> params = new HashMap<>();
		params.put("type", "Failure");
		params.put("isError", true);
		params.put("response", ""+errorCode);
		params.put("data", SDKUtils.errorCodeToString(errorCode));

		dispatch(params, eventName, listener);
	}

	public static Hashtable<Object, Object> jsonToHashTable(LuaState L, String json){
		Hashtable<Object, Object> ht;
		L.getGlobal("require");
		L.pushString("json");
		L.call( 1, 1 );
		L.getField(-1, "decode");
		L.pushString(json);
		L.call( 1, 1 );

		ht = CoronaLua.toHashtable(L, -1);
		L.pop(2);

		return ht;
	}

	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int connect(LuaState L) {
		if (!L.isTable(1)){
			Log.e("Corona", "neura.connect() takes table as first argument.");
			return 0;
		}
		if ( CoronaLua.isListener( L, -1, "" ) ) {
			fListener = CoronaLua.newRef(L, -1);
		}
		Hashtable<Object, Object> args = CoronaLua.toHashtable(L, 1);
		String appUid = args.get("appUid").toString();
		String appSecret = args.get("appSecret").toString();

		boolean usingCustomReminders = false;
		if (L.isTable(2)){
			Hashtable<Object, Object> hiddenArgs = CoronaLua.toHashtable(L, 2);
			usingCustomReminders = (boolean) hiddenArgs.get("usingCustomReminders");
		}


		CoronaActivity activity = CoronaEnvironment.getCoronaActivity();
		Context context = activity.getApplicationContext();

		SharedPreferences mPrefs = context.getSharedPreferences("neuraplugin", 0);
		SharedPreferences.Editor mEditor = mPrefs.edit();
		mEditor.putBoolean("usingCustomReminders", usingCustomReminders);
		mEditor.commit();

		if (appSecret.equals("") || appUid.equals("")){
			Log.e("Corona", "neura.connect() takes table as first argument with appUid and appSecret as required keys.");
			return 0;
		}

		Builder builder = new Builder(activity.getApplicationContext());
		mNeuraApiClient = builder.build();
		mNeuraApiClient.setAppUid(appUid);
		mNeuraApiClient.setAppSecret(appSecret);
		activity.runOnUiThread(new Runnable() {
			@Override

			public void run() {
				mNeuraApiClient.connect();
			}
		});

		try {
			FirebaseApp.getInstance();
		} catch (IllegalStateException ex) {
			if (args.get("firebase") != null){
				Hashtable<Object, Object> firebaseParams = (Hashtable<Object, Object>)args.get("firebase");
				FirebaseOptions.Builder firebaseBuilder = new FirebaseOptions.Builder();
				firebaseBuilder.setApiKey(firebaseParams.get("apiKey").toString());
				firebaseBuilder.setApplicationId(firebaseParams.get("applicationId").toString());
//				firebaseBuilder.setDatabaseUrl(firebaseParams.get("databaseUrl").toString());
				firebaseBuilder.setGcmSenderId(firebaseParams.get("gcmSenderId").toString());
//				firebaseBuilder.setStorageBucket(firebaseParams.get("storageBucket").toString());


				FirebaseApp.initializeApp(activity, firebaseBuilder.build());
			}

		}

		HashMap<String, Object> params = new HashMap<>();
		params.put("type", "Success");
		Hashtable<String, String> eventData = new Hashtable<String, String>();
		eventData.put("eventName", "connect");
		eventData.put("eventIdentifier", "");
		params.put("event", eventData);
		dispatch(params, "connect", fListener);

		return 0;
	}

	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int disconnect(LuaState L) {
		mNeuraApiClient.disconnect();

		return 0;
	}


	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int authenticate(final LuaState L) {
		int listener = fListener;
		if ( CoronaLua.isListener( L, -1, "" ) ) {
			listener = CoronaLua.newRef(L, -1);
		}
		final int finalListener = listener;
		final CoronaActivity activity = CoronaEnvironment.getCoronaActivity();
		AuthenticationRequest request = new AuthenticationRequest();
		if (L.isTable(1)) {
			Hashtable<Object, Object> args = CoronaLua.toHashtable(L, 1);
			if (args.containsKey("phone")) {
				request.setPhone(args.get("phone").toString());
			}
			if (args.containsKey("appId")) {
				request.setAppId(args.get("appId").toString());
			}
			if (args.containsKey("appSecret")) {
				request.setAppSecret(args.get("appSecret").toString());
			}
		}
//		String str1 = activity.getApplicationContext().getString(activity.getApplicationContext().getApplicationInfo().labelRes);
//		Log.d("Corona", "App name 1: " +str1);
//		String str2 = activity.getString(activity.getApplicationContext().getApplicationInfo().labelRes);
//		Log.d("Corona", "App name 2: " +str2);
//		String str3 = activity.getString(activity.getApplicationInfo().labelRes);
//		Log.d("Corona", "App name 3: " +str3);
		boolean result = mNeuraApiClient.authenticate(request, new AuthenticateCallback() {
			@Override
			public void onSuccess(AuthenticateData authenticateData) {
				HashMap<String, Object> params = new HashMap<>();
				params.put("type", "Success");
				Hashtable<String, Object> data = new Hashtable<>();
				data.put("neuraUserId", authenticateData.getNeuraUserId());
				data.put("accessToken", authenticateData.getAccessToken());
				ArrayList<EventDefinition> events = authenticateData.getEvents();
				Hashtable<Integer, Object> eventsJson = new Hashtable<Integer, Object>();
				for (EventDefinition event : events){
					Hashtable<Object, Object> ht = jsonToHashTable(L, event.toJson().toString());
					eventsJson.put(eventsJson.size()+1, ht);
				}
				data.put("events", eventsJson);
				params.put("data", data);

				dispatch(params, "authenticate", finalListener);

			}

			@Override
			public void onFailure(int i) {
				HashMap<String, Object> params = new HashMap<>();
				params.put("type", "Failure");
				params.put("isError", true);
				params.put("response", ""+i);
				params.put("data", SDKUtils.errorCodeToString(i));

				dispatch(params, "authenticate", finalListener);

			}
		});
		L.pushBoolean(result);
		return 1;
	}

	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int subscribeToEvent(LuaState L) {
		if (!L.isString(1) || !L.isString(2)){
			Log.e("Corona", "neura.subscribeToEvent(eventName, eventIdentifier) takes strings as the first two arguments.");
			return 0;
		}

		String eventName = L.toString(1);
		String eventIdentifier = L.toString(2);
		int listener = fListener;
		if ( CoronaLua.isListener( L, -1, "" ) ) {
			listener = CoronaLua.newRef(L, -1);
		}


		final int finalListener = listener;
		mNeuraApiClient.subscribeToEvent(eventName, eventIdentifier,
				new SubscriptionRequestCallbacks() {
					@Override
					public void onSuccess(String eventName, Bundle bundle, String eventIdentifier) {
						HashMap<String, Object> params = new HashMap<>();
						params.put("type", "Success");
						Hashtable<String, String> eventData = new Hashtable<String, String>();
						eventData.put("eventName", eventName);
						eventData.put("eventIdentifier", eventIdentifier);
						params.put("event", eventData);

						dispatch(params, "subscribeToEvent", finalListener);
					}

					@Override
					public void onFailure(String eventName, Bundle bundle, int i) {
						HashMap<String, Object> params = new HashMap<>();
						params.put("type", "Failure");
						Hashtable<String, String> eventData = new Hashtable<String, String>();
						eventData.put("eventName", eventName);
						params.put("event", eventData);

						params.put("isError", true);
						params.put("response", ""+i);
						params.put("data", SDKUtils.errorCodeToString(i));

						dispatch(params, "subscribeToEvent", finalListener);
					}
				});

		return 0;
	}

	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int addDevice(LuaState L) {

		int listener = fListener;
		if ( CoronaLua.isListener( L, -1, "" ) ) {
			listener = CoronaLua.newRef(L, -1);
		}
		final int finalListener = listener;

		PickerCallback callback = new PickerCallback() {
			@Override
			public void onResult(boolean success) {
				HashMap<String, Object> params = new HashMap<>();
				params.put("response", success);

				dispatch(params, "addDevice", finalListener);
			}
		};

		boolean result = false;
		if (L.isTable(1)){
			Hashtable<Object, Object> params = CoronaLua.toHashtable(L, 1);
			if (params.containsKey("deviceName")){
				mNeuraApiClient.addDevice(params.get("deviceName").toString(), callback);
			}else if(params.containsKey("deviceCapabilityNames")){
				Hashtable<Object, Object> deviceCapabilityNames = (Hashtable<Object, Object>)params.get("deviceCapabilityNames");
				Collection<Object> names = deviceCapabilityNames.values();
				ArrayList<String> namesList = new ArrayList<>();
				for (Object name : names){
					namesList.add(name.toString());
				}
				result = mNeuraApiClient.addDevice(namesList, callback);
			}else{
				result = mNeuraApiClient.addDevice(callback);
			}
		}else{
			result = mNeuraApiClient.addDevice(callback);
		}

		L.pushBoolean(result);
		return 1;
	}

	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int addPlace(LuaState L) {
		// TODO
		return 0;
	}

	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int enableAutomaticallySyncLogs(LuaState L) {
		boolean enabled = true;
		if (L.isBoolean(1)){
			enabled = L.toBoolean(1);
		}
		mNeuraApiClient.enableAutomaticallySyncLogs(enabled);
		return 0;
	}

	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int enableNeuraHandingStateAlertMessages(LuaState L) {
		boolean enabled = true;
		if (L.isBoolean(1)){
			enabled = L.toBoolean(1);
		}
		mNeuraApiClient.enableAutomaticallySyncLogs(enabled);
		return 0;
	}

	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int forgetMe(LuaState L) {
		boolean showAreYouSureDialog = false;
		if (L.isBoolean(1)){
			showAreYouSureDialog = L.toBoolean(1);
		}

		int listener = fListener;
		if ( CoronaLua.isListener( L, -1, "" ) ) {
			listener = CoronaLua.newRef(L, -1);
		}
		final int finalListener = listener;
		final CoronaActivity activity = CoronaEnvironment.getCoronaActivity();

		final boolean finalShowAreYouSureDialog = showAreYouSureDialog;
		activity.runOnUiThread(new Runnable() {
			@Override

			public void run() {
				mNeuraApiClient.forgetMe(activity, finalShowAreYouSureDialog, new Handler.Callback() {
					@Override
					public boolean handleMessage(Message msg) {
						HashMap<String, Object> params = new HashMap<>();
						params.put("response", msg.toString());

						dispatch(params, "forgetMe", finalListener);
						return false;
					}
				});
			}
		});

		return 0;
	}

	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int getAppPermissions(final LuaState L) {
		int listener = fListener;
		if ( CoronaLua.isListener( L, -1, "" ) ) {
			listener = CoronaLua.newRef(L, -1);
		}
		final int finalListener = listener;

		mNeuraApiClient.getAppPermissions(new GetPermissionsRequestCallbacks() {
			@Override
			public void onSuccess(List<Permission> list) throws RemoteException {
				HashMap<String, Object> params = new HashMap<>();
				params.put("type", "Success");
				Hashtable<Integer, Object> data = new Hashtable<>();
				for (Permission p : list){
					data.put(data.size()+1, jsonToHashTable(L, p.toJson().toString()));
				}
				params.put("data", data);

				dispatch(params, "getAppPermissions", finalListener);
			}

			@Override
			public void onFailure(Bundle bundle, int i) throws RemoteException {
				dispatchOnFailure(bundle, i, "getAppPermissions", finalListener);
			}

			@Override
			public IBinder asBinder() {
				return null;
			}
		});

		return 0;
	}

	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int getDailySummary(final LuaState L) {
		if (!L.isNumber(1)){
			Log.e("Corona", "neura.getDailySummary() takes number as the first argument.");
			return 0;
		}

		long timestamp = (long)L.toNumber(1);
		int listener = fListener;
		if ( CoronaLua.isListener( L, -1, "" ) ) {
			listener = CoronaLua.newRef(L, -1);
		}

		final int finalListener = listener;

		mNeuraApiClient.getDailySummary(timestamp, new DailySummaryCallbacks() {
			@Override
			public void onSuccess(DailySummaryData situationData) {
				HashMap<String, Object> params = new HashMap<>();
				Log.d("Corona", "Daily Summary : " + situationData.toString());
				params.put("type", "Success");
				Log.d("Corona", situationData.toJson().toString());
				params.put("data", jsonToHashTable(L, situationData.toJson().toString()));

				dispatch(params, "getDailySummary", finalListener);
			}

			@Override
			public void onFailure(Bundle resultData, int errorCode) {
				HashMap<String, Object> params = new HashMap<>();
				params.put("type", "Failure");
				params.put("isError", true);
				params.put("response", ""+errorCode);
				params.put("data", SDKUtils.errorCodeToString(errorCode));

				dispatch(params, "getDailySummary", finalListener);
			}
		});
		return 0;
	}

	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int getKnownCapabilities(LuaState L) {
		ArrayList<Capability> list = mNeuraApiClient.getKnownCapabilities();
		Hashtable<Object, Object> capabilities = new Hashtable<>();
		for (Capability c : list){
			capabilities.put(capabilities.size()+1, jsonToHashTable(L, c.toJson().toString()));
		}
		CoronaLua.pushHashtable(L, capabilities);
		return 1;
	}

	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int getKnownDevices(final LuaState L) {

		int listener = fListener;
		if ( CoronaLua.isListener( L, -1, "" ) ) {
			listener = CoronaLua.newRef(L, -1);
		}

		final int finalListener = listener;

		boolean result = mNeuraApiClient.getKnownDevices(new DevicesRequestCallback() {
			@Override
			public void onSuccess(DevicesResponseData data) {
				HashMap<String, Object> params = new HashMap<>();
				params.put("type", "Success");
				params.put("data", jsonToHashTable(L, data.toJson().toString()));

				dispatch(params, "getKnownDevices", finalListener);
			}

			@Override
			public void onFailure(int errorCode) {
				dispatchOnFailure(null, errorCode, "getKnownDevices", finalListener);
			}
		});

		L.pushBoolean(result);

		return 1;
	}

	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int getLocationBasedEvents(LuaState L) {
		ArrayList<String> list = mNeuraApiClient.getLocationBasedEvents();
		Hashtable<Object, Object> events = new Hashtable<>();
		for (String s : list){
			events.put(events.size()+1, s);
		}
		CoronaLua.pushHashtable(L, events);
		return 1;
	}

	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int getMissingDataForEvent(LuaState L) {
		if (!L.isString(1)){
			Log.e("Corona", "neura.getMissingDataForEvent() takes string as the first argument.");
			return 0;
		}

		String eventName = L.toString(1);

		int listener = fListener;
		if ( CoronaLua.isListener( L, -1, "" ) ) {
			listener = CoronaLua.newRef(L, -1);
		}

		final int finalListener = listener;

		boolean result = mNeuraApiClient.getMissingDataForEvent(eventName, new PickerCallback() {
			@Override
			public void onResult(boolean success) {
				HashMap<String, Object> params = new HashMap<>();
				params.put("response", success);

				dispatch(params, "getMissingDataForEvent", finalListener);
			}
		});

		L.pushBoolean(result);
		return 1;
	}

	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int getPermissionStatus(LuaState L) {

		return 0;
	}

	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int getSdkVersion(LuaState L) {
		L.pushString(mNeuraApiClient.getSdkVersion());
		return 1;
	}

	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int getPluginVersion(LuaState L) {
		L.pushString(PLUGIN_VERSION);
		return 1;
	}

	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int getSleepProfile(final LuaState L) {
		if (!L.isNumber(1) || !L.isNumber(2)){
			Log.e("Corona", "neura.getSleepProfile() takes numbers as the first two arguments.");
			return 0;
		}

		long startTimestamp = (long)L.toNumber(1);
		long endTimestamp = (long)L.toNumber(2);
		int listener = fListener;
		if ( CoronaLua.isListener( L, -1, "" ) ) {
			listener = CoronaLua.newRef(L, -1);
		}

		final int finalListener = listener;

		mNeuraApiClient.getSleepProfile(startTimestamp, endTimestamp, new SleepProfileCallbacks() {
			@Override
			public void onSuccess(SleepProfileData situationData) {
				Log.d("Corona", "Sleep profile : " + situationData.toString());
				HashMap<String, Object> params = new HashMap<>();
				params.put("type", "Success");
				params.put("data", jsonToHashTable(L, situationData.toJson().toString()));

				dispatch(params, "getSleepProfile", finalListener);
			}

			@Override
			public void onFailure(Bundle resultData, int errorCode) {
				dispatchOnFailure(resultData, errorCode, "getSleepProfile", finalListener);
			}
		});
		return 0;
	}

	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int getSubscriptions(final LuaState L) {
		int listener = fListener;
		if ( CoronaLua.isListener( L, -1, "" ) ) {
			listener = CoronaLua.newRef(L, -1);
		}

		final int finalListener = listener;

		mNeuraApiClient.getSubscriptions(new GetSubscriptionsCallbacks() {
			@Override
			public void onSuccess(List<AppSubscription> list) {
				HashMap<String, Object> params = new HashMap<>();
				params.put("type", "Success");
				Hashtable<Integer, Object> data = new Hashtable<>();
				for (AppSubscription s : list){
					data.put(data.size()+1, jsonToHashTable(L, s.toJson().toString()));
				}
				params.put("data", data);

				dispatch(params, "getSubscriptions", finalListener);
			}

			@Override
			public void onFailure(Bundle bundle, int i) {
				dispatchOnFailure(bundle, i, "getSubscriptions", finalListener);
			}
		});
		return 0;
	}

	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int getUserDetails(final LuaState L) {
		int listener = fListener;
		if ( CoronaLua.isListener( L, -1, "" ) ) {
			listener = CoronaLua.newRef(L, -1);
		}

		final int finalListener = listener;

		mNeuraApiClient.getUserDetails(new UserDetailsCallbacks() {
			@Override
			public void onSuccess(UserDetails userDetails) {
				HashMap<String, Object> params = new HashMap<>();
				params.put("type", "Success");
				params.put("data", jsonToHashTable(L, userDetails.toJson().toString()));

				dispatch(params, "getUserDetails", finalListener);
			}

			@Override
			public void onFailure(Bundle resultData, int errorCode) {
				dispatchOnFailure(resultData, errorCode, "getUserDetails", finalListener);
			}
		});
		return 0;
	}

	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int getUserPhone(LuaState L) {
		int listener = fListener;
		if ( CoronaLua.isListener( L, -1, "" ) ) {
			listener = CoronaLua.newRef(L, -1);
		}

		final int finalListener = listener;

		mNeuraApiClient.getUserPhone(new UserPhoneCallbacks() {
			@Override
			public void onSuccess(UserPhone userPhone) {
				HashMap<String, Object> params = new HashMap<>();
				params.put("type", "Success");
				params.put("data", userPhone.getPhone());

				dispatch(params, "getUserPhone", finalListener);
			}

			@Override
			public void onFailure(Bundle resultData, int errorCode) {
				dispatchOnFailure(resultData, errorCode, "getUserPhone", finalListener);
			}
		});
		return 0;
	}

	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int getUserPlaceByLabelType(LuaState L) {
		if (!L.isString(1)){
			Log.e("Corona", "neura.getUserPlaceByLabelType() takes string as the first argument.");
			return 0;
		}

		String placeLabelType = L.toString(1);

		ArrayList<PlaceNode> list = mNeuraApiClient.getUserPlaceByLabelType(placeLabelType);
		Hashtable<Object, Object> placeNodes = new Hashtable<>();
		for (PlaceNode p : list){
			placeNodes.put(placeNodes.size()+1, jsonToHashTable(L, p.toJson().toString()));
		}
		CoronaLua.pushHashtable(L, placeNodes);

		return 1;
	}

	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int getUserSituation(final LuaState L) {
		if (!L.isNumber(1)){
			Log.e("Corona", "neura.getUserSituation() takes number as the first argument.");
			return 0;
		}

		long timestamp = (long)L.toNumber(1);

		int listener = fListener;
		if ( CoronaLua.isListener( L, -1, "" ) ) {
			listener = CoronaLua.newRef(L, -1);
		}

		final int finalListener = listener;

		mNeuraApiClient.getUserSituation(new SituationCallbacks() {
			@Override
			public void onSuccess(SituationData situationData) {
				Log.d("Corona", "User Situation : " + situationData.toString());
				HashMap<String, Object> params = new HashMap<>();
				params.put("type", "Success");
				params.put("data", jsonToHashTable(L, situationData.toJson().toString()));

				dispatch(params, "getUserSituation", finalListener);
			}

			@Override
			public void onFailure(Bundle resultData, int errorCode) {
				dispatchOnFailure(resultData, errorCode, "getUserSituation", finalListener);
			}
		}, timestamp);

		return 0;
	}

	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int hasDeviceWithCapability(LuaState L) {
		if (!L.isString(1)){
			Log.e("Corona", "neura.hasDeviceWithCapability() takes string as the first argument.");
			return 0;
		}

		String capabilityName = L.toString(1);

		boolean hasCapability = mNeuraApiClient.hasDeviceWithCapability(capabilityName);
		L.pushBoolean(hasCapability);

		return 1;
	}

	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int isLoggedIn(LuaState L) {
		L.pushBoolean(mNeuraApiClient.isLoggedIn());

		return 1;
	}

	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int isMissingDataForEvent(LuaState L) {
		if (!L.isString(1)){
			Log.e("Corona", "neura.isMissingDataForEvent() takes string as the first argument.");
			return 0;
		}

		String eventName = L.toString(1);

		L.pushBoolean(mNeuraApiClient.isMissingDataForEvent(eventName));

		return 1;
	}

	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int registerFirebaseToken(LuaState L) {
		CoronaActivity activity = CoronaEnvironment.getCoronaActivity();
		String firebaseToken = FirebaseInstanceId.getInstance(FirebaseApp.getInstance()).getToken();
		Log.d("Corona", "Firebase token: " + firebaseToken);
		mNeuraApiClient.registerFirebaseToken(activity, firebaseToken);
		return 0;
	}

	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int removeSubscription(LuaState L) {
		if (!L.isString(1) || !L.isString(2)){
			Log.e("Corona", "neura.removeSubscription(eventName, eventIdentifier) takes strings as the first two arguments.");
			return 0;
		}

		String eventName = L.toString(1);
		String eventIdentifier = L.toString(2);
		int listener = fListener;
		if ( CoronaLua.isListener( L, -1, "" ) ) {
			listener = CoronaLua.newRef(L, -1);
		}


		final int finalListener = listener;

		mNeuraApiClient.removeSubscription(eventName, eventIdentifier,
				new SubscriptionRequestCallbacks() {
					@Override
					public void onSuccess(String eventName, Bundle bundle, String eventIdentifier) {
						HashMap<String, Object> params = new HashMap<>();
						params.put("type", "Success");
						Hashtable<String, String> eventData = new Hashtable<String, String>();
						eventData.put("eventName", eventName);
						eventData.put("eventIdentifier", eventIdentifier);
						params.put("event", eventData);

//						dispatch(params, "removeSubscription", finalListener);
					}

					@Override
					public void onFailure(String eventName, Bundle bundle, int i) {
						HashMap<String, Object> params = new HashMap<>();
						params.put("type", "Failure");
						Hashtable<String, String> eventData = new Hashtable<String, String>();
						eventData.put("eventName", eventName);
						params.put("event", eventData);

						params.put("isError", true);
						params.put("response", ""+i);
						params.put("data", SDKUtils.errorCodeToString(i));

//						dispatch(params, "removeSubscription", finalListener);
					}

				});

		return 0;
	}

	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int sendFeedbackOnEvent(LuaState L) {
		if (!L.isString(1) || !L.isBoolean(2)){
			Log.e("Corona", "neura.sendFeedbackOnEvent(neuraId, approved) takes string as the first arguments and boolean as the second argument.");
			return 0;
		}
		String neuraId = L.toString(1);
		boolean approved = L.toBoolean(2);
		mNeuraApiClient.sendFeedbackOnEvent(neuraId, approved);
		return 0;
	}

	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int shouldSubscribeToEvent(LuaState L) {
		if (!L.isString(1)){
			Log.e("Corona", "neura.shouldSubscribeToEvent(eventName) takes string as the first argument.");
			return 0;
		}
		String eventName = L.toString(1);
		boolean result = mNeuraApiClient.shouldSubscribeToEvent(eventName);
		L.pushBoolean(result);
		return 1;
	}

	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int simulateAnEvent(LuaState L) {
		mNeuraApiClient.simulateAnEvent();
		return 0;
	}

	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int registerNotificationForEvent(final LuaState L) {
		if ( !L.isString(1) || !L.isTable(2) ) {
			Log.e("Corona", "neura.registerNotificationForEvent(eventName, options) takes string as the first arguments and table as the second argument.");
			return 0;
		}
		String eventName = L.toString(1);
		Hashtable<Object, Object> options = CoronaLua.toHashtable(L, 2);

		String filename = CoronaEnvironment.getCoronaActivity().getFilesDir().getPath() + "/notifications.neura";

		Map<String, Hashtable<Object, Object>> notificationsMap = null;
		try {

			FileInputStream fileInputStream  = new FileInputStream(filename);
			ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);

			notificationsMap = (HashMap<String, Hashtable<Object, Object>>) objectInputStream.readObject();
			objectInputStream.close();

		} catch (IOException | ClassNotFoundException e) {
			Log.d("Corona", "Input Stream : " + e.getMessage());
			e.printStackTrace();
		}

		if (notificationsMap == null){
			notificationsMap = new HashMap<String, Hashtable<Object, Object>>();
		}

		notificationsMap.put(eventName, options);

		try {
			FileOutputStream fileOutputStream = new FileOutputStream(filename);
			ObjectOutputStream objectOutputStream= new ObjectOutputStream(fileOutputStream);

			objectOutputStream.writeObject(notificationsMap);
			objectOutputStream.close();
		} catch (IOException e) {
			Log.d("Corona", "Output Stream : " + e.getMessage());
			e.printStackTrace();
		}

		return 0;
	}

	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int unregisterNotificationForEvent(final LuaState L) {
		if ( !L.isString(1) ) {
			Log.e("Corona", "neura.unregisterNotificationForEvent(eventName) takes string as the first arguments and table as the second argument.");
			return 0;
		}
		String eventName = L.toString(1);

		String filename = CoronaEnvironment.getCoronaActivity().getFilesDir().getPath() + "/notifications.neura";

		Map<String, Hashtable<Object, Object>> notificationsMap = null;
		try {

			FileInputStream fileInputStream  = new FileInputStream(filename);
			ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);

			notificationsMap = (HashMap<String, Hashtable<Object, Object>>) objectInputStream.readObject();
			objectInputStream.close();

		} catch (IOException | ClassNotFoundException e) {
			Log.d("Corona", "Input Stream : " + e.getMessage());
			e.printStackTrace();
			return 0;
		}

		notificationsMap.remove(eventName);
		try {
			FileOutputStream fileOutputStream = new FileOutputStream(filename);
			ObjectOutputStream objectOutputStream= new ObjectOutputStream(fileOutputStream);

			objectOutputStream.writeObject(notificationsMap);
			objectOutputStream.close();
		} catch (IOException e) {
			Log.d("Corona", "Output Stream : " + e.getMessage());
			e.printStackTrace();
		}

		return 0;
	}

	@SuppressWarnings("unused")
	private class ConnectWrapper implements NamedJavaFunction {

		@Override
		public String getName() {
			return "connect";
		}

		@Override
		public int invoke(LuaState L) {
			return connect(L);
		}
	}

	@SuppressWarnings("unused")
	private class DisconnectWrapper implements NamedJavaFunction {

		@Override
		public String getName() {
			return "disconnect";
		}

		@Override
		public int invoke(LuaState L) {
			return disconnect(L);
		}
	}

	@SuppressWarnings("unused")
	private class AuthenticateWrapper implements NamedJavaFunction {

		@Override
		public String getName() {
			return "authenticate";
		}

		@Override
		public int invoke(LuaState L) {
			return authenticate(L);
		}
	}

	@SuppressWarnings("unused")
	private class SubscribeToEventWrapper implements NamedJavaFunction {

		@Override
		public String getName() {
			return "subscribeToEvent";
		}

		@Override
		public int invoke(LuaState L) {
			return subscribeToEvent(L);
		}
	}

	@SuppressWarnings("unused")
	private class AddDeviceWrapper implements NamedJavaFunction {

		@Override
		public String getName() {
			return "addDevice";
		}

		@Override
		public int invoke(LuaState L) {
			return addDevice(L);
		}
	}

	@SuppressWarnings("unused")
	private class AddPlaceWrapper implements NamedJavaFunction {

		@Override
		public String getName() {
			return "addPlace";
		}

		@Override
		public int invoke(LuaState L) {
			return addPlace(L);
		}
	}

	@SuppressWarnings("unused")
	private class EnableAutomaticallySyncLogsWrapper implements NamedJavaFunction {

		@Override
		public String getName() {
			return "enableAutomaticallySyncLogs";
		}

		@Override
		public int invoke(LuaState L) {
			return enableAutomaticallySyncLogs(L);
		}
	}

	@SuppressWarnings("unused")
	private class EnableNeuraHandingStateAlertMessagesWrapper implements NamedJavaFunction {

		@Override
		public String getName() {
			return "enableNeuraHandingStateAlertMessages";
		}

		@Override
		public int invoke(LuaState L) {
			return enableNeuraHandingStateAlertMessages(L);
		}
	}

	@SuppressWarnings("unused")
	private class ForgetMeWrapper implements NamedJavaFunction {

		@Override
		public String getName() {
			return "forgetMe";
		}

		@Override
		public int invoke(LuaState L) {
			return forgetMe(L);
		}
	}

	@SuppressWarnings("unused")
	private class GetAppPermissionsWrapper implements NamedJavaFunction {

		@Override
		public String getName() {
			return "getAppPermissions";
		}

		@Override
		public int invoke(LuaState L) {
			return getAppPermissions(L);
		}
	}

	@SuppressWarnings("unused")
	private class GetDailySummaryWrapper implements NamedJavaFunction {

		@Override
		public String getName() {
			return "getDailySummary";
		}

		@Override
		public int invoke(LuaState L) {
			return getDailySummary(L);
		}
	}

	@SuppressWarnings("unused")
	private class GetKnownCapabilitiesWrapper implements NamedJavaFunction {

		@Override
		public String getName() {
			return "getKnownCapabilities";
		}

		@Override
		public int invoke(LuaState L) {
			return getKnownCapabilities(L);
		}
	}

	@SuppressWarnings("unused")
	private class GetKnownDevicesWrapper implements NamedJavaFunction {

		@Override
		public String getName() {
			return "getKnownDevices";
		}

		@Override
		public int invoke(LuaState L) {
			return getKnownDevices(L);
		}
	}

	@SuppressWarnings("unused")
	private class GetLocationBasedEventsWrapper implements NamedJavaFunction {

		@Override
		public String getName() {
			return "getLocationBasedEvents";
		}

		@Override
		public int invoke(LuaState L) {
			return getLocationBasedEvents(L);
		}
	}

	@SuppressWarnings("unused")
	private class GetMissingDataForEventWrapper implements NamedJavaFunction {

		@Override
		public String getName() {
			return "getMissingDataForEvent";
		}

		@Override
		public int invoke(LuaState L) {
			return getMissingDataForEvent(L);
		}
	}

	@SuppressWarnings("unused")
	private class GetPermissionStatusWrapper implements NamedJavaFunction {

		@Override
		public String getName() {
			return "getPermissionStatus";
		}

		@Override
		public int invoke(LuaState L) {
			return getPermissionStatus(L);
		}
	}

	@SuppressWarnings("unused")
	private class GetSdkVersionWrapper implements NamedJavaFunction {

		@Override
		public String getName() {
			return "getSdkVersion";
		}

		@Override
		public int invoke(LuaState L) {
			return getSdkVersion(L);
		}
	}

	@SuppressWarnings("unused")
	private class GetPluginVersionWrapper implements NamedJavaFunction {

		@Override
		public String getName() {
			return "getPluginVersion";
		}

		@Override
		public int invoke(LuaState L) {
			return getPluginVersion(L);
		}
	}

	@SuppressWarnings("unused")
	private class GetSleepProfileWrapper implements NamedJavaFunction {

		@Override
		public String getName() {
			return "getSleepProfile";
		}

		@Override
		public int invoke(LuaState L) {
			return getSleepProfile(L);
		}
	}

	@SuppressWarnings("unused")
	private class GetSubscriptionsWrapper implements NamedJavaFunction {

		@Override
		public String getName() {
			return "getSubscriptions";
		}

		@Override
		public int invoke(LuaState L) {
			return getSubscriptions(L);
		}
	}

	@SuppressWarnings("unused")
	private class GetUserDetailsWrapper implements NamedJavaFunction {

		@Override
		public String getName() {
			return "getUserDetails";
		}

		@Override
		public int invoke(LuaState L) {
			return getUserDetails(L);
		}
	}

	@SuppressWarnings("unused")
	private class GetUserPhoneWrapper implements NamedJavaFunction {

		@Override
		public String getName() {
			return "getUserPhone";
		}

		@Override
		public int invoke(LuaState L) {
			return getUserPhone(L);
		}
	}

	@SuppressWarnings("unused")
	private class GetUserPlaceByLabelTypeWrapper implements NamedJavaFunction {

		@Override
		public String getName() {
			return "getUserPlaceByLabelType";
		}

		@Override
		public int invoke(LuaState L) {
			return getUserPlaceByLabelType(L);
		}
	}

	@SuppressWarnings("unused")
	private class GetUserSituationWrapper implements NamedJavaFunction {

		@Override
		public String getName() {
			return "getUserSituation";
		}

		@Override
		public int invoke(LuaState L) {
			return getUserSituation(L);
		}
	}

	@SuppressWarnings("unused")
	private class HasDeviceWithCapabilityWrapper implements NamedJavaFunction {

		@Override
		public String getName() {
			return "hasDeviceWithCapability";
		}

		@Override
		public int invoke(LuaState L) {
			return hasDeviceWithCapability(L);
		}
	}

	@SuppressWarnings("unused")
	private class IsLoggedInWrapper implements NamedJavaFunction {

		@Override
		public String getName() {
			return "isLoggedIn";
		}

		@Override
		public int invoke(LuaState L) {
			return isLoggedIn(L);
		}
	}

	@SuppressWarnings("unused")
	private class IsMissingDataForEventWrapper implements NamedJavaFunction {

		@Override
		public String getName() {
			return "isMissingDataForEvent";
		}

		@Override
		public int invoke(LuaState L) {
			return isMissingDataForEvent(L);
		}
	}

	@SuppressWarnings("unused")
	private class RegisterFirebaseTokenWrapper implements NamedJavaFunction {

		@Override
		public String getName() {
			return "registerFirebaseToken";
		}

		@Override
		public int invoke(LuaState L) {
			return registerFirebaseToken(L);
		}
	}

	@SuppressWarnings("unused")
	private class RemoveSubscriptionWrapper implements NamedJavaFunction {

		@Override
		public String getName() {
			return "removeSubscription";
		}

		@Override
		public int invoke(LuaState L) {
			return removeSubscription(L);
		}
	}

	@SuppressWarnings("unused")
	private class SendFeedbackOnEventWrapper implements NamedJavaFunction {

		@Override
		public String getName() {
			return "sendFeedbackOnEvent";
		}

		@Override
		public int invoke(LuaState L) {
			return sendFeedbackOnEvent(L);
		}
	}

	@SuppressWarnings("unused")
	private class ShouldSubscribeToEventWrapper implements NamedJavaFunction {

		@Override
		public String getName() {
			return "shouldSubscribeToEvent";
		}

		@Override
		public int invoke(LuaState L) {
			return shouldSubscribeToEvent(L);
		}
	}

	@SuppressWarnings("unused")
	private class SimulateAnEventWrapper implements NamedJavaFunction {

		@Override
		public String getName() {
			return "simulateAnEvent";
		}

		@Override
		public int invoke(LuaState L) {
			return simulateAnEvent(L);
		}
	}

	@SuppressWarnings("unused")
	private class RegisterNotificationForEventWrapper implements NamedJavaFunction {

		@Override
		public String getName() {
			return "registerNotificationForEvent";
		}

		@Override
		public int invoke(LuaState L) {
			return registerNotificationForEvent(L);
		}
	}

	@SuppressWarnings("unused")
	private class UnregisterNotificationForEventWrapper implements NamedJavaFunction {

		@Override
		public String getName() {
			return "unregisterNotificationForEvent";
		}

		@Override
		public int invoke(LuaState L) {
			return unregisterNotificationForEvent(L);
		}
	}

}
