package org.sorz.lab.tinykeepass;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.security.keystore.UserNotAuthenticatedException;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;

import java.io.File;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;

import de.slackspace.openkeepass.KeePassDatabase;
import de.slackspace.openkeepass.domain.Entry;
import de.slackspace.openkeepass.domain.KeePassFile;
import de.slackspace.openkeepass.exception.KeePassDatabaseUnreadableException;

public class MainActivity extends AppCompatActivity
        implements FingerprintDialogFragment.OnFragmentInteractionListener {
    private final static String TAG = MainActivity.class.getName();
    private final static int REQUEST_CONFIRM_DEVICE_CREDENTIAL = 0;
    private final static int NOTIFICATION_ID_COPY_PASSWORD = 1;

    private SharedPreferences preferences;
    private KeyguardManager keyguardManager;
    private ClipboardManager clipboardManager;
    private NotificationManager notificationManager;
    private SecureStringStorage secureStringStorage;

    private File databaseFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        databaseFile = new File(getNoBackupFilesDir(), FetchDatabaseTask.DB_FILENAME);
        try {
            secureStringStorage = new SecureStringStorage(this);
        } catch (SecureStringStorage.SystemException e) {
            throw new RuntimeException(e);
        }

        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, new DatabaseLockedFragment())
                    .commit();

            if (KeePassStorage.getKeePassFile() == null && !databaseFile.canRead()) {
                doConfigureDatabase();
                finish();
            } else {
                doUnlockDatabase();
            }
        }
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(view ->
                Snackbar
                    .make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show());
    }

    public void doUnlockDatabase() {
        if (KeePassStorage.getKeePassFile() != null)
            showEntryList();
        else
            openDatabase();
    }

    public void doConfigureDatabase() {
        KeePassStorage.setKeePassFile(null);
        startActivity(new Intent(this, DatabaseSetupActivity.class));
    }

    public void copyEntry(Entry entry) {
        if (entry.getUsername() != null) {
            clipboardManager.setPrimaryClip(
                    ClipData.newPlainText("Username", entry.getUsername()));
            showSnackbar(String.format("Username \"%s\" copied", entry.getUsername()),
                    Snackbar.LENGTH_SHORT);
        }
        if (entry.getPassword() != null) {
            Notification.Builder builder = new Notification.Builder(this)
                    .setSmallIcon(R.drawable.ic_vpn_key_white_24dp)
                    .setContentTitle("Click to copy password");
            if (entry.getUsername() != null)
                builder.setContentText(
                        String.format("Copy %s's password to clipboard.", entry.getUsername()));
            else if (entry.getTitle() != null)
                builder.setContentText("Copy password for " + entry.getTitle());
            else
                builder.setContentText("Copy password to clipboard.");
            notificationManager.notify(NOTIFICATION_ID_COPY_PASSWORD, builder.build());

        }
    }
    private void openDatabase() {
        int authMethod = preferences.getInt("key-auth-method", 0);
        switch (authMethod) {
            case 0: // no auth
            case 1: // screen lock
                try {
                    Cipher cipher = secureStringStorage.getDecryptCipher();
                    openDatabase(cipher);
                } catch (UserNotAuthenticatedException e) {
                    // should do authentication
                    Intent intent = keyguardManager.createConfirmDeviceCredentialIntent(
                            getString(R.string.auth_key_title),
                            getString(R.string.auth_key_decription));
                    startActivityForResult(intent, REQUEST_CONFIRM_DEVICE_CREDENTIAL);
                } catch (SecureStringStorage.SystemException e) {
                    throw new RuntimeException(e);
                }
                break;
            case 2: // fingerprint
                getFragmentManager().beginTransaction()
                        .add(FingerprintDialogFragment.newInstance(), "fingerprint")
                        .commit();
                break;
        }
    }

    private void openDatabase(Cipher cipher) {
        try {
            List<String> strings = secureStringStorage.get(cipher);
            KeePassFile db = KeePassDatabase.getInstance(databaseFile).openDatabase(strings.get(0));
            KeePassStorage.setKeePassFile(db);
            showEntryList();
        } catch (SecureStringStorage.SystemException e) {
            throw new RuntimeException(e);
        } catch (BadPaddingException | IllegalBlockSizeException | UserNotAuthenticatedException e) {
            Log.w(TAG, "fail to decrypt keys", e);
            showSnackbar("Failed to decrypt keys", Snackbar.LENGTH_LONG);
        } catch (KeePassDatabaseUnreadableException | UnsupportedOperationException e) {
            Log.w(TAG, "cannot open database.", e);
            showSnackbar(e.getLocalizedMessage(), Snackbar.LENGTH_LONG);
        }
    }

    private void showEntryList() {
        getFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, EntryFragment.newInstance())
                .commit();
    }

    private void showSnackbar(CharSequence text, int duration) {
        Snackbar.make(findViewById(R.id.toolbar), text, duration).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONFIRM_DEVICE_CREDENTIAL:
                if (resultCode == RESULT_OK)
                    openDatabase();
                else
                    showSnackbar("Failed to authenticate user", Snackbar.LENGTH_LONG);
                break;
            default:
                break;
        }
    }

    @Override
    public void onFingerprintCancel() {
        showSnackbar("Failed to authenticate user", Snackbar.LENGTH_LONG);
    }

    @Override
    public void onFingerprintSuccess(Cipher cipher) {
        openDatabase(cipher);
    }
}
