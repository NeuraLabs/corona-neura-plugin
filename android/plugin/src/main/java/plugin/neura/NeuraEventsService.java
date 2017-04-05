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

import com.ansca.corona.CoronaEnvironment;
import com.ansca.corona.storage.ResourceServices;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.neura.standalonesdk.events.NeuraEvent;
import com.neura.standalonesdk.events.NeuraPushCommandFactory;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

public class NeuraEventsService extends FirebaseMessagingService {
	@Override
	public void onMessageReceived(RemoteMessage message) {
		Map data = message.getData();
		if (NeuraPushCommandFactory.getInstance().isNeuraEvent(data)) {
			NeuraEvent event = NeuraPushCommandFactory.getInstance().getEvent(data);
			String eventText = event != null ? event.toString() : "couldn't parse data";

			Log.i("Corona", "received Neura event - " + eventText);
			HashMap<String, Object> params = new HashMap<>();
			params.put("type", "Success");
			params.put("data", data.get("pushData"));
			LuaLoader.dispatch(params, "onNeuraMessageReceived", -1);

			generateNotification(getApplicationContext(), event);
		}
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
}
