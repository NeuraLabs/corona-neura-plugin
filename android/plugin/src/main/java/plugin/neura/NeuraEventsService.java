/**
 * Created by stivendeleur on 2/21/17.
 */

package plugin.neura;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.content.Intent;
import android.app.PendingIntent;
import android.app.IntentService;
import android.os.Bundle;
import android.support.v4.app.NotificationManagerCompat;

import com.ansca.corona.CoronaEnvironment;
import com.ansca.corona.storage.ResourceServices;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.neura.standalonesdk.events.NeuraEvent;
import com.neura.standalonesdk.events.NeuraPushCommandFactory;
import com.neura.standalonesdk.events.NeuraEventCallBack;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import com.ansca.corona.CoronaActivity;
import com.ansca.corona.CoronaEnvironment;
import com.neura.standalonesdk.service.NeuraApiClient;

import android.app.AlarmManager;
import android.content.SharedPreferences;

public class NeuraEventsService extends FirebaseMessagingService {

	/*public static boolean alarm1Snooze = false;
	public static boolean alarm2Snooze = false;
	public static boolean alarm3Snooze = false;*/
	//public static boolean[] snoozeArray = new boolean[3];
	//public static long[] snoozeStartTime = new long[3];
	//public static boolean[] canCheckAlarm = new boolean[3];

	@Override
	public void onMessageReceived(RemoteMessage message) {
		//Log.i("Corona", "onMessageReceived");
		final Map data = message.getData();
		NeuraPushCommandFactory pushCommand = NeuraPushCommandFactory.getInstance();
		Context appContext = getApplicationContext();


		boolean isNeuraPush = pushCommand.isNeuraPush(appContext, data, new NeuraEventCallBack() {

			@Override
			public void neuraEventDetected(NeuraEvent event) {
				String eventText;
				if (event != null && !event.toString().isEmpty()) {
					eventText = event.toString();

					HashMap<String, Object> params = new HashMap<>();
					params.put("type", "Success");
					params.put("data", data.get("pushData"));
					LuaLoader.dispatch(params, "onNeuraMessageReceived", -1);

					Log.i(getClass().getSimpleName(), "received Neura event - " + eventText);

					SharedPreferences mPrefs = getApplicationContext().getSharedPreferences("neuraplugin", 0);
					boolean usingCustomReminders = mPrefs.getBoolean("usingCustomReminders", false);

					//Log.d("Corona", "usingCustomReminders retrieved as " + usingCustomReminders);
					if (usingCustomReminders)
					{
						checkForNeuraAlarm(getApplicationContext(), event);
					}
					else
					{
						generateNotification(getApplicationContext(), event);
					}

					//Not mandatory, just gives Neura sdk feedback on the event
					NeuraApiClient.sendFeedbackOnEvent(getApplicationContext(), event.getNeuraId());
				}
				else {
					eventText = "couldn't parse data";
				}

			}
		});

		if(!isNeuraPush) {
			//Handle non neura push here
			Log.i(getClass().getSimpleName(), "FCM RemoteMessage not sent by Neura server");
		}
	}

    /*public boolean doesNeuraAlarmExist(int id){
		boolean doesExist = false;

		CoronaActivity activity = CoronaEnvironment.getCoronaActivity();
		if (activity != null){
			Context context = activity.getApplicationContext();
			Intent intent = new Intent(context, LuaLoader.NeuraAlarm.class);

			//intent.setAction(Intent.ACTION_VIEW);
			PendingIntent test = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_NO_CREATE);
			if (test != null){
				doesExist = true;
			}
		}
		return doesExist;
	}*/

    private void checkForNeuraAlarm(Context context, NeuraEvent event) {
    	/*for(int i=0; i<=9; i++){
		    boolean alarmExists = doesNeuraAlarmExist(i);
		    Log.d("Corona", "Alarm " + i + " Exists = " + alarmExists);
		}*/
		/*for(int i=0; i<snoozeArray.length; i++){
		    Log.d("Corona", "Alarm " + i + " Exists = " + snoozeArray[i]);
		}*/

		String eventName = event.getEventName();
		long now = System.currentTimeMillis();
		long[] alarmWaitTimes = {300000, 2400000, 2400000};//300k = 5 mins, 2.4m = 40 mins

		SharedPreferences mPrefs = getApplicationContext().getSharedPreferences("neuraplugin", 0);
		boolean isSnooze1 = mPrefs.getBoolean("isSnooze1", false);	
		boolean isSnooze2 = mPrefs.getBoolean("isSnooze2", false);	
		boolean isSnooze3 = mPrefs.getBoolean("isSnooze3", false);	

		boolean canCheckAlarm1 = mPrefs.getBoolean("canCheckAlarm1", false);	
		boolean canCheckAlarm2 = mPrefs.getBoolean("canCheckAlarm2", false);	
		boolean canCheckAlarm3 = mPrefs.getBoolean("canCheckAlarm3", false);	

		long snoozeStartTime1 = mPrefs.getLong("snoozeStartTime1", System.currentTimeMillis());
		long snoozeStartTime2 = mPrefs.getLong("snoozeStartTime2", System.currentTimeMillis());
		long snoozeStartTime3 = mPrefs.getLong("snoozeStartTime3", System.currentTimeMillis());

		if (isSnooze1 == true){
			String[] alarm1Triggers = { "userArrivedHome", "userArrivedToWork", "userFinishedWalking", "userFinishedRunning", "userFinishedDriving" };   
			for(int i=0; i<alarm1Triggers.length; i++){   
			    if (alarm1Triggers[i].equals(eventName)){
			    	long diff = now - snoozeStartTime1;
			    	if (diff >= alarmWaitTimes[0])
			    	{
			    		

						Intent alarmIntent = new Intent(context, LuaLoader.NeuraAlarm.class);
					    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 4, alarmIntent, PendingIntent.FLAG_ONE_SHOT);
					    AlarmManager alarmManager = (AlarmManager) context.getSystemService(context.ALARM_SERVICE);
				    	alarmManager.cancel(pendingIntent);

			    	
				    	Intent alarmIntNew = new Intent(context, LuaLoader.NeuraAlarmService.class);
			        	alarmIntNew.putExtra("notificationCode",4);
						alarmIntNew.putExtra("notificationType","pill");
						alarmIntNew.putExtra("isSnooze",1);
			        	context.startService(alarmIntNew);

			        	isSnooze1 = false;
			    		snoozeStartTime1 = 0;
			        }
			        else
			        {
			        	long remainingTime = alarmWaitTimes[0] - diff;
			        	int seconds = (int) (remainingTime / 1000) % 60 ;
						int minutes = (int) ((remainingTime / (1000*60)) % 60);
						int hours   = (int) ((remainingTime / (1000*60*60)) % 24);

						//Log.d("Corona", "Too early to trigger alarm 1. Wait another "+minutes+":"+seconds);

			        }
			    }
			}
		}

		if (isSnooze2 == true || canCheckAlarm2 == true){
			Log.d("Corona", "Can check alarm 2");
			String[] alarm2Triggers = { "userWokeUp","userGotUp","userArrivedHome","userArrivedToWork", "userFinishedWalking", "userFinishedRunning", "userFinishedDriving", "userArrivedAtPharmacy", "userArrivedAtGroceryStore" };   
			for(int i=0; i<alarm2Triggers.length; i++){         
			    if (alarm2Triggers[i].equals(eventName)){
			    	Log.d("Corona", "Valid trigger");
			    	long diff = now - snoozeStartTime2;
			    	if (diff >= alarmWaitTimes[1] || canCheckAlarm2 == true)
			    	{
			    		Log.d("Corona", "Can show notification");

				    	Intent alarmIntNew = new Intent(context, LuaLoader.NeuraAlarmService.class);
			        	alarmIntNew.putExtra("notificationCode",5);
						alarmIntNew.putExtra("notificationType","period");
						if (isSnooze2 == true){
							alarmIntNew.putExtra("isSnooze",1);
						}
						else
						{
							alarmIntNew.putExtra("isSnooze",0);
						}
			        	context.startService(alarmIntNew);

			        	canCheckAlarm2 = false;
				    	isSnooze2 = false;
				    	snoozeStartTime2 = 0;
			        }
			        else
			        {
			        	long remainingTime = alarmWaitTimes[1] - diff;
			        	int seconds = (int) (remainingTime / 1000) % 60 ;
						int minutes = (int) ((remainingTime / (1000*60)) % 60);
						int hours   = (int) ((remainingTime / (1000*60*60)) % 24);
						Log.d("Corona", "Cannot show notification yet");
						//Log.d("Corona", "Too early to trigger alarm 2. Wait another "+minutes+":"+seconds);

			        
			        }
			    }
			}
		}
		if (isSnooze3 == true || canCheckAlarm3 == true){
			Log.d("Corona", "Can check alarm 3");
			String[] alarm3Triggers = { "userWokeUp","userGotUp","userArrivedHome","userArrivedToWork", "userFinishedWalking", "userFinishedRunning", "userFinishedDriving", "userArrivedAtPharmacy", "userArrivedAtGroceryStore" };   
			for(int i=0; i<alarm3Triggers.length; i++){         
			    if (alarm3Triggers[i].equals(eventName)){
			    	Log.d("Corona", "Valid trigger");
			    	long diff = now - snoozeStartTime3;
			    	if (diff >= alarmWaitTimes[2] || canCheckAlarm3 == true)
			    	{
			    		Log.d("Corona", "Can show notification");

				    	Intent alarmIntNew = new Intent(context, LuaLoader.NeuraAlarmService.class);
			        	alarmIntNew.putExtra("notificationCode",6);
						alarmIntNew.putExtra("notificationType","ovulation");
						if (isSnooze3 == true){
							alarmIntNew.putExtra("isSnooze",1);
						}
						else
						{
							alarmIntNew.putExtra("isSnooze",0);
						}
			        	context.startService(alarmIntNew);

			        	canCheckAlarm3 = false;
				    	isSnooze3 = false;
				    	snoozeStartTime3 = 0;
			        }
			        else
			        {
			        	long remainingTime = alarmWaitTimes[2] - diff;
			        	int seconds = (int) (remainingTime / 1000) % 60 ;
						int minutes = (int) ((remainingTime / (1000*60)) % 60);
						int hours   = (int) ((remainingTime / (1000*60*60)) % 24);
						Log.d("Corona", "Cannot show notification yet");
						//Log.d("Corona", "Too early to trigger alarm 3. Wait another "+minutes+":"+seconds);

			        
			        }
			    }	
			}
		}

		SharedPreferences.Editor mEditor = mPrefs.edit();
		mEditor.putBoolean("isSnooze1", isSnooze1).commit();
		mEditor.putBoolean("isSnooze2", isSnooze2).commit();
		mEditor.putBoolean("isSnooze3", isSnooze3).commit();

		mEditor.putBoolean("canCheckAlarm1", canCheckAlarm1).commit();
		mEditor.putBoolean("canCheckAlarm2", canCheckAlarm2).commit();
		mEditor.putBoolean("canCheckAlarm3", canCheckAlarm3).commit();

		mEditor.putLong("snoozeStartTime1", snoozeStartTime1).commit();
		mEditor.putLong("snoozeStartTime2", snoozeStartTime2).commit();
		mEditor.putLong("snoozeStartTime3", snoozeStartTime3).commit();

    }


	private void generateNotification(Context context, NeuraEvent event) {
		NotificationCompat.Builder builder = new NotificationCompat.Builder(context);

		String eventName = event.getEventName();

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
			return;
		}
		Hashtable<Object, Object> params = notificationsMap.get(eventName);

		if (params != null) {
			if (params.containsKey("contentTitle")) {
				builder.setContentTitle(params.get("contentTitle").toString());
				builder.setStyle(new NotificationCompat.BigTextStyle().bigText(params.get("contentTitle").toString()));
				;
			}
			if (params.containsKey("contentText")) {
				builder.setContentText(params.get("contentText").toString());
			}
			if (params.containsKey("number")) {
				builder.setNumber((int) params.get("number"));
			}
			if (params.containsKey("icon")) {
				AssetManager assetManager = context.getAssets();

				InputStream istr;
				Bitmap bitmap = null;
				try {
					istr = assetManager.open(params.get("icon").toString());
					bitmap = BitmapFactory.decodeStream(istr);
				} catch (IOException e) {
					bitmap = BitmapFactory.decodeResource(context.getResources(), context.getApplicationInfo().icon);
				}
				builder.setLargeIcon(bitmap);
			}else{
				builder.setLargeIcon(BitmapFactory.decodeResource(context.getResources(), context.getApplicationInfo().icon));
			}

			ResourceServices resourceServices = new ResourceServices(context);
			builder.setSmallIcon(resourceServices.getDrawableResourceId("corona_statusbar_icon_default"));


			builder.setAutoCancel(true)
					.setWhen(System.currentTimeMillis());
			Notification notification = builder.build();

			NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
			notificationManager.notify((int) System.currentTimeMillis(), notification);
		}
	}

	@Override
	protected Intent zzF(Intent intent) {
		return null;
	}
}
