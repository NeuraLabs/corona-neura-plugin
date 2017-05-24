package plugin.neura;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.neura.android.statealert.SensorsManager;
import com.neura.resources.sensors.SensorType;
import com.neura.standalonesdk.util.NeuraStateAlertReceiver;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Objects;

/**
 * Created by stivendeleur on 2/26/17.
 */

public class HandleNeuraStateAlertReceiver extends NeuraStateAlertReceiver {
	@Override
	public void onDetectedMissingPermission(Context context, String permission) {
		HashMap<String, Object> params = new HashMap<>();
		params.put("type", "Success");
		params.put("data", permission);
		Log.d("Corona", "Neura detected missing permission : " + permission);

		LuaLoader.dispatch(params, "onDetectedMissingPermission", -1);
		Toast.makeText(context, "Neura detected missing permission : " + permission, Toast.LENGTH_SHORT).show();
	}

	@Override
	public void onDetectedMissingPermissionAfterUserPressedNeverAskAgain(Context context, String permission) {
		HashMap<String, Object> params = new HashMap<>();
		params.put("type", "Success");
		params.put("data", permission);
		Log.d("Corona", "Neura detected missing permission BUT user already pressed 'Never ask again': "
				+ permission);
		LuaLoader.dispatch(params, "onDetectedMissingPermissionAfterUserPressedNeverAskAgain", -1);
		Toast.makeText(context, "Neura detected missing permission BUT user already pressed 'Never ask again': "
				+ permission, Toast.LENGTH_SHORT).show();
	}

	@Override
	public void onSensorStateChanged(Context context, SensorType sensorType, boolean isEnabled) {
		HashMap<String, Object> params = new HashMap<>();
		params.put("type", "Success");
		Hashtable<Object, Object> data = new Hashtable<>();
		String sensorTypeString = "";
		switch (sensorType){
			case wifi:
				sensorTypeString = "wifi";
				break;
			case network:
				sensorTypeString = "network";
				break;
			case location:
				sensorTypeString = "locations";
				break;
			case bluetooth:
				sensorTypeString = "bluetooth";
				break;
		}
		data.put("sensorType", sensorTypeString);
		data.put("isEnabled", isEnabled);
		params.put("data", data);

		Log.d("Corona", "Neura detected that " + SensorsManager.getInstance().getSensorName(sensorType) +
				" sensor is " + (isEnabled ? "enabled" : "disabled"));
		LuaLoader.dispatch(params, "onDetectedMissingPermissionAfterUserPressedNeverAskAgain", -1);

		Toast.makeText(context, "Neura detected that " + SensorsManager.getInstance().getSensorName(sensorType) +
				" sensor is " + (isEnabled ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
		/**
		 * If the sensor is disabled, you may open the settings with an intent, in an activity's context :
		 */
		//if (!isEnabled)
		//startActivityForResult(new Intent(SensorsManager.getInstance().getSensorAction(sensorType), REQUEST_CODE));
	}
}
