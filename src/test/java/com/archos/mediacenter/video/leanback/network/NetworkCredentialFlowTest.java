// Copyright 2026 Courville Software
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.archos.mediacenter.video.leanback.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.test.core.app.ApplicationProvider;

import com.archos.filecorelibrary.ListingEngine;
import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;
import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase.Credential;
import com.archos.mediacenter.filecoreextension.UriUtils;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.leanback.filebrowsing.ListingActivity;
import com.archos.mediacenter.video.leanback.filebrowsing.ListingFragment;
import com.archos.mediacenter.video.leanback.network.ftp.FtpServerCredentialsDialog;
import com.archos.mediacenter.video.leanback.network.smb.SmbServerCredentialsDialog;
import com.archos.mediacenter.video.leanback.network.webdav.WebdavListingActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class NetworkCredentialFlowTest {

    private Context mContext;
    private final String TEST_WEBDAV_URI = "webdavs://home.courville.org:5006/download/";

    public static class TestNetworkListingFragment extends NetworkListingFragment {
        public Uri lastStartedUri = null;
        public int startListingCount = 0;
        public ListingEngine.ErrorEnum lastFatalErrorCode = null;
        public Exception lastFatalErrorException = null;

        @Override
        protected ListingFragment instantiateNewFragment() {
            return new TestNetworkListingFragment();
        }

        @Override
        protected void startListing(Uri uri) {
            lastStartedUri = uri;
            startListingCount++;
            // Do not call super.startListing() to avoid launching real network listing engines
        }

        @Override
        public void onListingFatalError(Exception e, ListingEngine.ErrorEnum errorCode) {
            lastFatalErrorCode = errorCode;
            lastFatalErrorException = e;
            super.onListingFatalError(e, errorCode);
        }
    }

    public static class TestWebdavListingActivity extends WebdavListingActivity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            setTheme(R.style.Theme_AppCompat_NoActionBar);
            super.onCreate(savedInstanceState);
        }

        @Override
        protected ListingFragment getStartingFragment() {
            return new TestNetworkListingFragment();
        }
    }

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        NetworkCredentialsDatabase.getInstance().loadCredentials(mContext);
    }

    private ActivityController<TestWebdavListingActivity> createActivityController(Uri uri) {
        Intent intent = new Intent(mContext, TestWebdavListingActivity.class);
        intent.putExtra(ListingActivity.EXTRA_ROOT_URI, uri);
        intent.putExtra(ListingActivity.EXTRA_ROOT_NAME, "download");
        intent.putExtra(ListingActivity.EXTRA_STARTING_URI, uri);
        return Robolectric.buildActivity(TestWebdavListingActivity.class, intent).setup();
    }

    private TestNetworkListingFragment getFragment(ActivityController<TestWebdavListingActivity> controller) {
        Fragment frag = controller.get().getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        assertNotNull(frag);
        assertTrue(frag instanceof TestNetworkListingFragment);
        return (TestNetworkListingFragment) frag;
    }

    @Test
    public void testWebdav401PrefillsStoredCredentialsInDialog() {
        String testUser = "media";
        String testPass = "secret123";
        NetworkCredentialsDatabase.getInstance().saveCredential(
                new Credential(testUser, testPass, TEST_WEBDAV_URI, "", false));

        ActivityController<TestWebdavListingActivity> controller = createActivityController(Uri.parse(TEST_WEBDAV_URI));
        TestNetworkListingFragment fragment = getFragment(controller);

        // Simulate 401 Unauthorized callback
        fragment.onCredentialRequired(new Exception("401 Unauthorized"));
        ShadowLooper.idleMainLooper();

        NetworkServerCredentialsDialog dialog = (NetworkServerCredentialsDialog)
                fragment.getParentFragmentManager().findFragmentByTag(NetworkServerCredentialsDialog.class.getCanonicalName());
        assertNotNull("Dialog should be shown after 401", dialog);

        AlertDialog alertDialog = (AlertDialog) dialog.getDialog();
        assertNotNull(alertDialog);

        EditText userEt = alertDialog.findViewById(R.id.username);
        EditText passEt = alertDialog.findViewById(R.id.password);
        EditText remoteEt = alertDialog.findViewById(R.id.remote);
        EditText portEt = alertDialog.findViewById(R.id.port);
        Spinner typeSp = alertDialog.findViewById(R.id.ssh_spinner);

        assertNotNull(userEt);
        assertNotNull(passEt);
        assertEquals(testUser, userEt.getText().toString());
        assertEquals(testPass, passEt.getText().toString());
        assertEquals("home.courville.org", remoteEt.getText().toString());
        assertEquals("5006", portEt.getText().toString());
        assertEquals(UriUtils.getUriType(Uri.parse(TEST_WEBDAV_URI)).intValue(), typeSp.getSelectedItemPosition());

        dialog.dismiss();
        controller.destroy();
    }

    @Test
    public void testRetryBreakerStopsLoopOnSecond401WithoutRepeatingDialog() {
        ActivityController<TestWebdavListingActivity> controller = createActivityController(Uri.parse(TEST_WEBDAV_URI));
        TestNetworkListingFragment fragment = getFragment(controller);

        int initialStartCount = fragment.startListingCount;

        // First 401 -> presents credentials dialog
        fragment.onCredentialRequired(new Exception("401 Unauthorized"));
        ShadowLooper.idleMainLooper();

        NetworkServerCredentialsDialog dialog = (NetworkServerCredentialsDialog)
                fragment.getParentFragmentManager().findFragmentByTag(NetworkServerCredentialsDialog.class.getCanonicalName());
        assertNotNull(dialog);

        AlertDialog alertDialog = (AlertDialog) dialog.getDialog();
        assertNotNull(alertDialog);

        // User updates password and clicks Connect (Positive Button)
        EditText passEt = alertDialog.findViewById(R.id.password);
        passEt.setText("new_password");
        Button positiveButton = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        positiveButton.performClick();
        ShadowLooper.idleMainLooper();

        // startListing should have been triggered with new credentials
        assertEquals(initialStartCount + 1, fragment.startListingCount);
        assertTrue(fragment.mCredentialsJustProvided);

        // Second 401 occurs after credentials just entered -> must break loop
        fragment.onCredentialRequired(new Exception("401 Unauthorized (retry failed)"));
        ShadowLooper.idleMainLooper();

        // Must report ERROR_AUTHENTICATION and NOT reopen credentials dialog
        assertEquals(ListingEngine.ErrorEnum.ERROR_AUTHENTICATION, fragment.lastFatalErrorCode);
        NetworkServerCredentialsDialog secondDialog = (NetworkServerCredentialsDialog)
                fragment.getParentFragmentManager().findFragmentByTag(NetworkServerCredentialsDialog.class.getCanonicalName());
        assertNull("Dialog should not be opened again on retry failure", secondDialog);

        controller.destroy();
    }

    @Test
    public void testCancelViaButtonReportsAuthenticationErrorOnlyOnce() {
        ActivityController<TestWebdavListingActivity> controller = createActivityController(Uri.parse(TEST_WEBDAV_URI));
        TestNetworkListingFragment fragment = getFragment(controller);

        fragment.onCredentialRequired(new Exception("401 Unauthorized"));
        ShadowLooper.idleMainLooper();

        NetworkServerCredentialsDialog dialog = (NetworkServerCredentialsDialog)
                fragment.getParentFragmentManager().findFragmentByTag(NetworkServerCredentialsDialog.class.getCanonicalName());
        assertNotNull(dialog);

        AlertDialog alertDialog = (AlertDialog) dialog.getDialog();
        assertNotNull(alertDialog);

        Button negativeButton = alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE);
        negativeButton.performClick();
        ShadowLooper.idleMainLooper();

        assertEquals(ListingEngine.ErrorEnum.ERROR_AUTHENTICATION, fragment.lastFatalErrorCode);

        // Check error message TextView is visible
        TextView errorMessage = fragment.getView().findViewById(R.id.error);
        assertEquals(View.VISIBLE, errorMessage.getVisibility());
        assertEquals(mContext.getString(R.string.error_credentials), errorMessage.getText().toString());

        controller.destroy();
    }

    @Test
    public void testCancelViaBackDismissalInvokesCallbackExactlyOnce() {
        NetworkServerCredentialsDialog dialog = new NetworkServerCredentialsDialog();
        Bundle args = new Bundle();
        args.putString(NetworkServerCredentialsDialog.REMOTE, "home.courville.org");
        args.putInt(NetworkServerCredentialsDialog.PORT, 5006);
        args.putInt(NetworkServerCredentialsDialog.TYPE, UriUtils.getUriType(Uri.parse(TEST_WEBDAV_URI)));
        args.putString(NetworkServerCredentialsDialog.PATH, "/download/");
        dialog.setArguments(args);

        AtomicInteger cancelCallbackCount = new AtomicInteger(0);
        dialog.setOnCancelClickListener(view -> cancelCallbackCount.incrementAndGet());

        ActivityController<TestWebdavListingActivity> controller = createActivityController(Uri.parse(TEST_WEBDAV_URI));
        dialog.show(controller.get().getSupportFragmentManager(), "test_dialog");
        ShadowLooper.idleMainLooper();

        // Simulate dismissing dialog via onCancel (e.g. back press / outside touch)
        dialog.onCancel(dialog.getDialog());
        assertEquals(1, cancelCallbackCount.get());

        // Repeated onCancel or negative click should not fire again
        dialog.onCancel(dialog.getDialog());
        assertEquals(1, cancelCallbackCount.get());

        controller.destroy();
    }

    @Test
    public void testSmbCancelViaBackDismissalInvokesCallbackExactlyOnce() {
        SmbServerCredentialsDialog dialog = new SmbServerCredentialsDialog();
        Bundle args = new Bundle();
        args.putParcelable(SmbServerCredentialsDialog.URI, Uri.parse("smb://server/share/"));
        dialog.setArguments(args);

        AtomicInteger cancelCallbackCount = new AtomicInteger(0);
        dialog.setOnCancelClickListener(view -> cancelCallbackCount.incrementAndGet());

        ActivityController<TestWebdavListingActivity> controller = createActivityController(Uri.parse(TEST_WEBDAV_URI));
        dialog.show(controller.get().getSupportFragmentManager(), "test_smb_dialog");
        ShadowLooper.idleMainLooper();

        dialog.onCancel(dialog.getDialog());
        assertEquals(1, cancelCallbackCount.get());

        dialog.onCancel(dialog.getDialog());
        assertEquals(1, cancelCallbackCount.get());

        controller.destroy();
    }

    @Test
    public void testFtpCancelViaBackDismissalInvokesCallbackExactlyOnce() {
        FtpServerCredentialsDialog dialog = new FtpServerCredentialsDialog();
        Bundle args = new Bundle();
        args.putString(FtpServerCredentialsDialog.REMOTE, "ftp.server.com");
        args.putInt(FtpServerCredentialsDialog.PORT, 21);
        args.putInt(FtpServerCredentialsDialog.TYPE, 0);
        args.putString(FtpServerCredentialsDialog.PATH, "/files/");
        dialog.setArguments(args);

        AtomicInteger cancelCallbackCount = new AtomicInteger(0);
        dialog.setOnCancelClickListener(view -> cancelCallbackCount.incrementAndGet());

        ActivityController<TestWebdavListingActivity> controller = createActivityController(Uri.parse(TEST_WEBDAV_URI));
        dialog.show(controller.get().getSupportFragmentManager(), "test_ftp_dialog");
        ShadowLooper.idleMainLooper();

        dialog.onCancel(dialog.getDialog());
        assertEquals(1, cancelCallbackCount.get());

        dialog.onCancel(dialog.getDialog());
        assertEquals(1, cancelCallbackCount.get());

        controller.destroy();
    }
}
