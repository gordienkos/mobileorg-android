package com.matburt.mobileorg.Gui.Wizard.Wizards;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.DropboxAPI.Account;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.session.AppKeyPair;
import com.matburt.mobileorg.Gui.Wizard.FolderAdapter;
import com.matburt.mobileorg.Gui.Wizard.WizardView;
import com.matburt.mobileorg.R;

public class DropboxWizard extends Wizard {
	
	private TextView dropboxAccountInfo;
	private DropboxAPI<AndroidAuthSession> dropboxApi;
	private FolderAdapter directoryAdapter;
	
	private boolean isLoggedIn = false;
	private boolean dropboxLoginAttempted = false;
	private Button loginButton;

	public DropboxWizard(WizardView wizardView, Context context) {
		super(wizardView, context);

        /* TODO This is a hack to prevent NetworkOnMainThreadException to happen. This could be
           fixed properly by moving all communication with the dropbox API to a thread. */
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
	}
	

	@Override
	public void setupFirstPage() {
		createDropboxLogin();
	}
	
	public View createDropboxLogin() {		
		View view = LayoutInflater.from(context).inflate(R.layout.wizard_dropbox, null);
		
		dropboxAccountInfo = (TextView) view.findViewById(R.id.wizard_dropbox_accountinfo);

		AppKeyPair appKeys = new AppKeyPair(context.getString(R.string.dropbox_consumer_key),	context.getString(R.string.dropbox_consumer_secret));

		AndroidAuthSession session = new AndroidAuthSession(appKeys);

		dropboxApi = new DropboxAPI<AndroidAuthSession>(session);

		loginButton = (Button) view
				.findViewById(R.id.wizard_dropbox_login_button);
		loginButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (isLoggedIn) {
					dropboxApi.getSession().unlink();
					// need to clear the keys
				} else {
					dropboxLoginAttempted = true;
					dropboxApi.getSession().startOAuth2Authentication(context);
				}
			}
		});
		
		wizardView.addPage(view);
		wizardView.setNavButtonStateOnPage(1, true, WizardView.MIDDLE_PAGE);
		wizardView.disableAllNextActions(1);
		return view;
	}
	
	public View createDropboxList() {
		View view = LayoutInflater.from(context).inflate(R.layout.wizard_folder_pick_list, null);
		
		// setup directory browser
		DropboxDirectoryBrowser directory = new DropboxDirectoryBrowser(context, dropboxApi);
		// setup directory browser adapter
        // in View.getView
		directoryAdapter = new FolderAdapter(
                context
                , R.layout.folder_adapter_row
                , directory.listFiles()
        );
		directoryAdapter.setDoneButton((Button) view.findViewById(R.id.wizard_done_button));
		// bind adapter to browser
		directoryAdapter.setDirectoryBrowser(directory);
		// bind adapter to listview
		ListView folderList = (ListView) view.findViewById(R.id.wizard_folder_list);
		folderList.setAdapter(directoryAdapter);
		directoryAdapter.notifyDataSetChanged();
		
		setupDoneButton(view); // onClick - saves settings, finishes current activity
		wizardView.addPage(view);
		wizardView.enablePage(1); // setNavButtonStateOnPage is called inside for page-1
		// !!! enables nav buttons on that page (page-2):
		wizardView.setNavButtonStateOnPage(2, true, WizardView.LAST_PAGE);
		
		return view;
	}
	
	@Override
	public void refresh() {
		handleDropboxResume();
	}
	
	public void handleDropboxResume() {
		if (dropboxLoginAttempted && dropboxApi.getSession().authenticationSuccessful()) {
			dropboxLoginAttempted = false;
			try {
				// MANDATORY call to complete auth.
				// Sets the access token on the session
				dropboxApi.getSession().finishAuthentication();
                String accessToken = dropboxApi.getSession().getOAuth2AccessToken();
				storeAccessToken(accessToken);
				Toast.makeText(context, "Logged into Dropbox successfully!", Toast.LENGTH_SHORT).show();
				try {
					Account accountInfo = dropboxApi.accountInfo();
					dropboxAccountInfo.setText("User: "
							+ accountInfo.displayName + "; Id: "
							+ String.valueOf(accountInfo.uid));
				} catch (DropboxException e) {}
				loginButton.setEnabled(false);
				createDropboxList();
				wizardView.enablePage(1);
			} catch (IllegalStateException e) {
				Toast.makeText(context,
						String.format("Login failed: %s", e.toString()),
						Toast.LENGTH_SHORT).show();
			}
		}
	}

	private void storeAccessToken(String strAccessToken) {
		// Save the access token for later use
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		prefs.edit().putString(context.getString(R.string.db_key_dropbox_access_token), strAccessToken).commit();
	}

	@Override
	public void saveSettings() {
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(context);
		Editor editor = prefs.edit();
		
		editor.putString("syncSource", "dropbox");
		editor.putString(context.getString(R.string.db_key_dropbox_path), directoryAdapter.getCheckedDirectory() + "/");
		editor.commit();
	}
}
