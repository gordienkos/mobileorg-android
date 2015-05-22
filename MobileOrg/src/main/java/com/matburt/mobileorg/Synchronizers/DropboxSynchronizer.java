package com.matburt.mobileorg.Synchronizers;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.DropboxAPI.DropboxInputStream;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.exception.DropboxUnlinkedException;
import com.dropbox.client2.session.AppKeyPair;
import com.matburt.mobileorg.R;
import com.matburt.mobileorg.util.FileUtils;
import com.matburt.mobileorg.util.OrgUtils;

public class DropboxSynchronizer implements SynchronizerInterface {

	private String m_strRemoteIndexPath;
	private String m_strRemotePath;

    private boolean m_fIsLoggedIn = false;
	 
	private DropboxAPI<AndroidAuthSession> m_dropboxApi;
	private Context m_context;
    
    public DropboxSynchronizer(Context context) {
    	m_context = context;

		SharedPreferences sharedPreferences = PreferenceManager
				.getDefaultSharedPreferences(context);
    	
		m_strRemoteIndexPath = sharedPreferences.getString("dropboxPath", "");
		if (!m_strRemoteIndexPath.startsWith("/")) {
			m_strRemoteIndexPath = "/" + m_strRemoteIndexPath;
		}

		String dbPath = sharedPreferences.getString("dropboxPath", "");
		m_strRemotePath = dbPath.substring(0, dbPath.lastIndexOf("/") + 1);
        connect();
    }

    private AndroidAuthSession buildSession() {
        AppKeyPair appKeyPair = new AppKeyPair(m_context.getString(R.string.dropbox_consumer_key), m_context.getString(R.string.dropbox_consumer_secret));
        String strAccessToken = getAccessToken();
        return strAccessToken != null ? new AndroidAuthSession(appKeyPair, strAccessToken) : new AndroidAuthSession(appKeyPair);
    }

    public boolean isConfigured() {
        return m_fIsLoggedIn && !m_strRemoteIndexPath.equals("");
    }

    public void putRemoteFile(String filename, String contents) throws IOException {
		FileUtils orgFile = new FileUtils(filename, m_context);
        BufferedWriter writer =  orgFile.getWriter();
        writer.write(contents);
        writer.close();
    
        File uploadFile = orgFile.getFile();
        FileInputStream fis = new FileInputStream(uploadFile);
        try {
            m_dropboxApi.putFileOverwrite(m_strRemotePath + filename, fis, uploadFile.length(), null);
        } catch (DropboxUnlinkedException e) {
            throw new IOException("Dropbox Authentication Failed, re-run setup wizard please");
        } catch (DropboxException e) {
            throw new IOException("Uploading " + filename + " failed because: " + e.toString());
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {}
            }
        }
    }

	public BufferedReader getRemoteFile(String filename) throws IOException {
		String filePath = m_strRemotePath + filename;
        try {
            DropboxInputStream is = m_dropboxApi.getFileStream(filePath, null);
            BufferedReader fileReader = new BufferedReader(new InputStreamReader(is));
            return fileReader;
        } catch (DropboxUnlinkedException e) {
            throw new IOException("Dropbox Authentication Failed, re-run setup wizard please");
        } catch (DropboxException e) {
            throw new IOException("Fetching " + filename + " failed: " + e.toString());
        }
	}

    
    /**
     * This handles authentication if the user's token & secret
     * are stored locally, so we don't have to store user-name & password
     * and re-send every time.
     */
    private void connect() {
        AndroidAuthSession session = buildSession();
        m_dropboxApi = new DropboxAPI<AndroidAuthSession>(session);
        if (!m_dropboxApi.getSession().isLinked()) {
            m_fIsLoggedIn = false;
            Log.d("MobileOrg", "Dropbox account was unlinked...");
            //TODO: throw new IOException("Dropbox Authentication Failed, re-run setup wizard");
        }
        else {
            m_fIsLoggedIn = true;
        }
    }
    
    /**
     * Shows keeping the access token returned from Trusted Authenticator in a local
     * store, rather than storing user name & password, and re-authenticating each
     * time (which is not to be done, ever).
     *
     * @return String [access_token], or null if none stored
     */
    private String getAccessToken() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
                m_context.getApplicationContext());
        return prefs.getString(m_context.getString(R.string.db_key_dropbox_access_token), null);
    }


	private void showToast(String msg) {
		final String u_msg = msg;
		final Handler mHandler = new Handler();
		final Runnable mRunPost = new Runnable() {
			public void run() {
				Toast.makeText(m_context, u_msg, Toast.LENGTH_LONG).show();
			}
		};

		new Thread() {
			public void run() {
				mHandler.post(mRunPost);
			}
		}.start();
	}


	@Override
	public void postSynchronize() {
	}

	@Override
	public boolean isConnectable() {
		return OrgUtils.isNetworkOnline(m_context);
	}
}
