package plugin.neura;

//import com.ansca.corona.CoronaActivity;
import android.util.Log;

import com.ansca.corona.CoronaActivity;
import com.ansca.corona.CoronaEnvironment;
import com.ansca.corona.CoronaLua;
import com.ansca.corona.CoronaRuntime;
import com.ansca.corona.CoronaRuntimeListener;
import com.naef.jnlua.JavaFunction;
import com.naef.jnlua.LuaState;
import com.naef.jnlua.NamedJavaFunction;

import com.neura.standalonesdk.service.NeuraApiClient;
import com.neura.standalonesdk.util.Builder;
import com.neura.resources.authentication.AuthenticateCallback;
import com.neura.resources.authentication.AuthenticateData;
import com.neura.sdk.service.SubscriptionRequestCallbacks;
import com.neura.sdk.object.EventDefinition;
import com.neura.standalonesdk.util.SDKUtils;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;


@SuppressWarnings("WeakerAccess")
public class LuaLoader implements JavaFunction, CoronaRuntimeListener {
	// Neura
	private NeuraApiClient mNeuraApiClient;

	static private int fListener;
	static CoronaRuntime fRuntime;

	/** This corresponds to the event name, e.g. [Lua] event.name */
	private static final String PLUGIN_NAME = "neura";

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
		// Register this plugin into Lua with the following functions.
		NamedJavaFunction[] luaFunctions = new NamedJavaFunction[] {
				new ConnectWrapper(),
				new DisconnectWrapper(),
				new AuthenticateWrapper(),
		};
		String libName = L.toString( 1 );
		L.register(libName, luaFunctions);

		// Returning 1 indicates that the Lua require() function will return the above Lua library.
		return 1;
	}

	@Override
	public void onLoaded(CoronaRuntime runtime) {
		fRuntime = runtime;
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

	public static void dispatch(Map<String, Object> params, String name, int fListener)
	{
		try {
			LuaState luaState = fRuntime.getLuaState();
			CoronaLua.newEvent(luaState, name);

			CoronaLua.pushValue(luaState, PLUGIN_NAME);
			luaState.setField(-2, "provider");

			for (String key : params.keySet()){
				CoronaLua.pushValue(luaState, params.get(key));
				luaState.setField(-2, key);
			}

			CoronaLua.dispatchEvent(luaState, fListener, 0);
		}catch(Exception exception){
			Log.e("Corona", "Unable to dispatch event. " + exception.toString());
		}
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

		if (appSecret.equals("") || appUid.equals("")){
			Log.e("Corona", "neura.connect() takes table as first argument with appUid and appSecret as required keys.");
			return 0;
		}

		CoronaActivity activity = CoronaEnvironment.getCoronaActivity();
		Builder builder = new Builder(activity);
		mNeuraApiClient = builder.build();
		mNeuraApiClient.setAppUid(appUid);
		mNeuraApiClient.setAppSecret(appSecret);
		activity.runOnUiThread(new Runnable() {
			@Override

			public void run() {
				mNeuraApiClient.connect();
			}
		});

		return 0;
	}

	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int disconnect(LuaState L) {
		mNeuraApiClient.disconnect();

		return 0;
	}

	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int authenticate(LuaState L) {
		int listener = fListener;
		if ( CoronaLua.isListener( L, -1, "" ) ) {
			listener = CoronaLua.newRef(L, -1);
		}
		final int finalListener = listener;
		mNeuraApiClient.authenticate(new AuthenticateCallback() {
			@Override
			public void onSuccess(AuthenticateData authenticateData) {
				HashMap<String, Object> params = new HashMap<>();
				params.put("type", "Success");
				Hashtable<String, Object> data = new Hashtable<>();
				data.put("neuraUserId", authenticateData.getNeuraUserId());
				data.put("accessToken", authenticateData.getAccessToken());
				ArrayList<EventDefinition> events = authenticateData.getEvents();
				Hashtable<Integer, String> eventsJson = new Hashtable<Integer, String>();
				for (EventDefinition event : events){
					eventsJson.put(eventsJson.size(), event.toJson().toString());
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

}
