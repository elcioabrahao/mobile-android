/*
 * Copyright (C) 2003-2015 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.exoplatform.shareextension;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.http.HttpResponse;

import org.exoplatform.R;
import org.exoplatform.model.ExoAccount;
import org.exoplatform.model.SocialPostInfo;
import org.exoplatform.model.SocialSpaceInfo;
import org.exoplatform.shareextension.service.ShareService;
import org.exoplatform.singleton.AccountSetting;
import org.exoplatform.singleton.ServerSettingHelper;
import org.exoplatform.singleton.SocialServiceHelper;
import org.exoplatform.social.client.api.ClientServiceFactory;
import org.exoplatform.social.client.api.SocialClientContext;
import org.exoplatform.social.client.api.SocialClientLibException;
import org.exoplatform.social.client.api.service.VersionService;
import org.exoplatform.social.client.core.ClientServiceFactoryHelper;
import org.exoplatform.ui.social.SpaceSelectorActivity;
import org.exoplatform.utils.ExoConnectionUtils;
import org.exoplatform.utils.ExoConstants;
import org.exoplatform.utils.ExoDocumentUtils;
import org.exoplatform.utils.ExoDocumentUtils.DocumentInfo;
import org.exoplatform.utils.Log;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.text.format.DateFormat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.Toast;
import android.widget.ViewFlipper;

/**
 * Created by The eXo Platform SAS
 * 
 * @author Philippe Aristote paristote@exoplatform.com
 * @since 3 Jun 2015
 */
public class ShareActivity extends FragmentActivity {

  /**
   * Direction of the animation to switch from one fragment to another
   */
  public enum Anim {
    NO_ANIM, FROM_LEFT, FROM_RIGHT
  }

  public static final String       LOG_TAG                  = ShareActivity.class.getName();

  public static final String       DEFAULT_CONTENT_NAME     = "TEMP_FILE_TO_SHARE";

  // type of button in actionBar, for invisible button
  public static final int          BUTTON_TYPE_INVISIBLE    = 0;

  // use id of share button in share_button_wrapper
  public static final int          BUTTON_TYPE_SHARE        = R.id.share_button;

  // use id of signin button in share_button_wrapper
  public static final int          BUTTON_TYPE_SIGNIN       = R.id.signin_login_btn;

  private static final int         SELECT_SHARE_DESTINATION = 11;

  // position index of view in share_button_wrapper file
  private static final int         SHARE_BUTTON_INDEX       = 0;

  private static final int         PROGRESS_INDEX           = 1;

  private static final int         SIGNIN_BUTTON_INDEX      = 2;

  private boolean                  online;

  // post button
  private Button                   mainButton;

  // signin button
  private Button                   mSignInButton;

  private ViewFlipper              mButtonWrapper;

  private WeakReference<LoginTask> mLoginRef;

  private MenuItem                 mMenuItem;

  private boolean                  mBtnEnable               = false;

  private SocialPostInfo           postInfo;

  private boolean                  mMultiFlag               = false;

  private List<Uri>                mAttachmentUris;

  @Override
  protected void onCreate(Bundle bundle) {
    super.onCreate(bundle);

    setContentView(R.layout.share_extension_activity);
    setTitle(R.string.ShareTitle);
    online = false;
    postInfo = new SocialPostInfo();

    if (isIntentCorrect()) {
      Intent intent = getIntent();
      String type = intent.getType();
      if ("text/plain".equals(type)) {
        // The share does not contain an attachment
        // TODO extract the link info - MOB-1866
        postInfo.postMessage = intent.getStringExtra(Intent.EXTRA_TEXT);
      } else {
        // The share contains an attachment
        if (mMultiFlag) {
          mAttachmentUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        } else {
          Uri contentUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
          if (contentUri != null) {
              mAttachmentUris = new ArrayList<Uri>();
              mAttachmentUris.add(contentUri);
          }
          Log.d(LOG_TAG, "Number of files to share: ", mAttachmentUris.size());
        }
        prepareAttachmentsAsync();
      }

      init();

      // Create and display the composer, aka ComposeFragment
      ComposeFragment composer = ComposeFragment.getFragment();
      openFragment(composer, ComposeFragment.COMPOSE_FRAGMENT, Anim.NO_ANIM);
    } else {
      // We're not supposed to reach this activity by anything else than an
      // ACTION_SEND intent
      finish();
    }

  }

  private boolean isIntentCorrect() {
    Intent intent = getIntent();
    String action = intent.getAction();
    String type = intent.getType();
    mMultiFlag = Intent.ACTION_SEND_MULTIPLE.equals(action);
    return ((Intent.ACTION_SEND.equals(action) || mMultiFlag) && type != null);
  }

  private void init() {
    // Load the list of accounts
    ServerSettingHelper.getInstance().getServerInfoList(this);
    // Init the activity with the selected account
    postInfo.ownerAccount = AccountSetting.getInstance().getCurrentAccount();
    if (postInfo.ownerAccount == null) {
      List<ExoAccount> serverList = ServerSettingHelper.getInstance().getServerInfoList(this);
      if (serverList == null || serverList.size() == 0) {
        // TODO open the configuration assistant to create an account
        // Then come back here after the result
        Toast.makeText(this, R.string.ShareErrorNoAccountConfigured, Toast.LENGTH_LONG).show();
        finish();
      }
      int selectedServerIdx = Integer.parseInt(getSharedPreferences(ExoConstants.EXO_PREFERENCE, 0).getString(ExoConstants.EXO_PRF_DOMAIN_INDEX,
                                                                                                              "-1"));
      AccountSetting.getInstance().setDomainIndex(String.valueOf(selectedServerIdx));
      AccountSetting.getInstance()
                    .setCurrentAccount((selectedServerIdx == -1 || selectedServerIdx >= serverList.size()) ? null
                                                                                                          : serverList.get(selectedServerIdx));
      postInfo.ownerAccount = AccountSetting.getInstance().getCurrentAccount();
    }
    ExoAccount acc = postInfo.ownerAccount;
    if (acc != null && acc.password != null && !"".equals(acc.password)) {
      // Try to login to setup the social services
      loginWithSelectedAccount();
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    // When we come back from the space selector activity
    // TODO make it another fragment
    if (requestCode == SELECT_SHARE_DESTINATION) {
      if (resultCode == RESULT_OK) {
        SocialSpaceInfo space = (SocialSpaceInfo) data.getParcelableExtra(SpaceSelectorActivity.SELECTED_SPACE);
        ComposeFragment composer = ComposeFragment.getFragment();
        if (space != null) {
          composer.setSpaceSelectorLabel(space.displayName);
        } else {
          composer.setSpaceSelectorLabel(getResources().getString(R.string.Public));
        }
        postInfo.destinationSpace = space;
      }
    }
  }

  /**
   * Util method to switch from one fragment to another, with an animation
   * 
   * @param toOpen The Fragment to open in this transaction
   * @param key the string key of the fragment
   * @param anim the type of animation
   */
  public void openFragment(Fragment toOpen, String key, Anim anim) {
    FragmentTransaction tr = getSupportFragmentManager().beginTransaction();
    switch (anim) {
    case FROM_LEFT:
      tr.setCustomAnimations(R.anim.fragment_enter_ltr, R.anim.fragment_exit_ltr);
      break;
    case FROM_RIGHT:
      tr.setCustomAnimations(R.anim.fragment_enter_rtl, R.anim.fragment_exit_rtl);
      break;
    default:
    case NO_ANIM:
      break;
    }
    tr.replace(R.id.share_extension_fragment, toOpen, key);
    tr.commit();
  }

  @Override
  public void onBackPressed() {
    // Intercept the back button taps to display previous state with animation
    // If we're on the composer, call super to finish the activity
    ComposeFragment composer = ComposeFragment.getFragment();
    if (composer.isAdded()) {
      if (postInfo != null)
        ExoDocumentUtils.deleteLocalFiles(postInfo.postAttachedFiles);
      super.onBackPressed();
    } else if (AccountsFragment.getFragment().isAdded()) {
      // close the accounts fragment and reopen the composer fragment
      openFragment(composer, ComposeFragment.COMPOSE_FRAGMENT, Anim.FROM_LEFT);
    } else if (SignInFragment.getFragment().isAdded()) {
      // close the sign in fragment and reopen the accounts fragment
      openFragment(AccountsFragment.getFragment(), AccountsFragment.ACCOUNTS_FRAGMENT, Anim.FROM_LEFT);
    }
  }

  @Override
  protected void onDestroy() {
    if (ComposeFragment.getFragment() != null)
      ComposeFragment.getFragment().setThumbnailImage(null);
    super.onDestroy();
  }

  /*
   * GETTERS & SETTERS
   */

  public void setPostMessage(String message) {
    if (message != null) {
      postInfo.postMessage = message;
    }
  }

  public SocialPostInfo getPostInfo() {
    return postInfo;
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.share, menu);
    mMenuItem = menu.findItem(R.id.menu_shareextension_post);
    if (mMenuItem != null) {
      View v = mMenuItem.getActionView();
      if (v != null) {
        mButtonWrapper = (ViewFlipper) v.findViewById(R.id.share_button_wrapper);
        mainButton = (Button) v.findViewById(R.id.share_button);
        mSignInButton = (Button) v.findViewById(R.id.signin_login_btn);
        toggleMainButtonType(BUTTON_TYPE_SHARE);
        enableDisableMainButton(mBtnEnable);
        LoginTask task = mLoginRef == null ? null : mLoginRef.get();
        if (task != null && task.getStatus() == Status.RUNNING) {
          toggleProgressVisible(true);
        }
      }
    }
    return true;
  }

  /**
   * @param type pass 0 for invisible the button
   */
  public void toggleMainButtonType(int type) {
    if (mMenuItem == null)
      return;
    if (type == BUTTON_TYPE_SIGNIN) {
      // switch from post => signin
      mMenuItem.setVisible(true);
      mMenuItem.setTitle(R.string.SignInInformation);
    } else if (type == BUTTON_TYPE_SHARE) {
      // switch from signin => post
      mMenuItem.setVisible(true);
      mMenuItem.setTitle(R.string.StatusUpdate);
    } else { // all other case treat as invisible case
      type = BUTTON_TYPE_INVISIBLE;
      mMenuItem.setVisible(false);
    }
    mButtonWrapper.setTag(type);
  }

  private int getButtonIndexFromTag() {
    // default show share button
    int ret = SHARE_BUTTON_INDEX;
    if (mButtonWrapper != null && mButtonWrapper.getTag() instanceof Integer) {
      final int type = (Integer) mButtonWrapper.getTag();
      if (type == BUTTON_TYPE_SIGNIN) {
        // case signin button
        ret = SIGNIN_BUTTON_INDEX;
      } else if (type == BUTTON_TYPE_SHARE) {
        // case share button
        ret = SHARE_BUTTON_INDEX;
      }
    }
    return ret;
  }

  public boolean isProgressVisible() {
    return (mButtonWrapper != null && mButtonWrapper.getDisplayedChild() == PROGRESS_INDEX);
  }

  public void toggleProgressVisible(boolean visible) {
    if (mButtonWrapper != null) {
      mButtonWrapper.setDisplayedChild(visible ? PROGRESS_INDEX : getButtonIndexFromTag());
    }
  }

  /**
   * Switch the main button to the given enabled state. If the main button is
   * the post button, we also check that the account is online.
   * 
   * @param enabled
   */
  public void enableDisableMainButton(boolean enabled) {
    mBtnEnable = enabled;
    if (mMenuItem == null) {
      return;
    }
    if (mainButton != null) {
      boolean currentState = mainButton.isEnabled();
      if (currentState != enabled) {
        mainButton.setEnabled(enabled && online);
      }
    }
    if (mSignInButton != null) {
      boolean currentState = mSignInButton.isEnabled();
      if (currentState != enabled) {
        mSignInButton.setEnabled(enabled);
      }
    }
  }

  /*
   * CLICK LISTENERS
   */

  public void onMainButtonClicked(View view) {
    // Tap on the Post button
    final int id = view.getId();
    if (id == BUTTON_TYPE_SHARE) {
      if (postInfo.ownerAccount == null || !online) {
        Toast.makeText(this, R.string.ShareCannotPostBecauseOffline, Toast.LENGTH_LONG).show();
        return;
      }

      String postMessage = postInfo.postMessage;
      if (postMessage == null || "".equals(postMessage))
        return;

      Log.d(LOG_TAG, "Start share service...");
      Intent share = new Intent(getBaseContext(), ShareService.class);
      share.putExtra(ShareService.POST_INFO, postInfo);
      startService(share);
      Toast.makeText(getBaseContext(), R.string.ShareOperationStarted, Toast.LENGTH_LONG).show();

      // Post is in progress, our work is done here
      finish();
    } else if (id == BUTTON_TYPE_SIGNIN) {
      // Tap on the Sign In button
      postInfo.ownerAccount.password = SignInFragment.getFragment().getPassword();
      openFragment(ComposeFragment.getFragment(), ComposeFragment.COMPOSE_FRAGMENT, Anim.FROM_LEFT);
      loginWithSelectedAccount();
    }
  }

  private void hideSoftKeyboard() {
    InputMethodManager mgr = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    mgr.hideSoftInputFromWindow(ComposeFragment.getFragment().getEditText().getWindowToken(), 0);
  }

  public void onSelectAccount() {
    // Called when the select account field is tapped
    hideSoftKeyboard();
    openFragment(AccountsFragment.getFragment(), AccountsFragment.ACCOUNTS_FRAGMENT, Anim.FROM_RIGHT);
  }

  public void onAccountSelected(ExoAccount account) {
    // Called when an account with password was selected.
    // If the selected account has no password, we open the SignInFragment first
    if (!account.equals(postInfo.ownerAccount)) {
      online = false;
      postInfo.ownerAccount = account;
      loginWithSelectedAccount();
    }
  }

  public void onSelectSpace() {
    // Called when the select space field is tapped
    if (online) {
      Intent spaceSelector = new Intent(this, SpaceSelectorActivity.class);
      startActivityForResult(spaceSelector, SELECT_SHARE_DESTINATION);
    } else {
      Toast.makeText(this, R.string.ShareCannotSelectSpaceBecauseOffline, Toast.LENGTH_LONG).show();
    }
  }

  /*
   * TASKS
   */

  public void loginWithSelectedAccount() {
    LoginTask task = new LoginTask();
    mLoginRef = new WeakReference<ShareActivity.LoginTask>(task);
    task.execute(postInfo.ownerAccount);
  }

  private void prepareAttachmentsAsync() {
    if (!ExoDocumentUtils.didRequestPermission(this, ExoConstants.REQUEST_PICK_IMAGE_FROM_GALLERY)) {
      new PrepareAttachmentsTask().execute();
    }
  }

  @SuppressLint("Override")
  @Override
  public void onRequestPermissionsResult(int reqCode, String[] permissions, int[] results) {
    if (reqCode == ExoConstants.REQUEST_PICK_IMAGE_FROM_GALLERY) {
      if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
        // permission granted
        prepareAttachmentsAsync();
      } else {
        // permission denied
        AlertDialog.Builder db = new AlertDialog.Builder(this);
        DialogInterface.OnClickListener dialogInterface = new OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_NEUTRAL) {
              Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package",
                                                                                                     getPackageName(),
                                                                                                     null));
              intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
              startActivity(intent);
            }
            finish();
          }
        };
        db.setMessage(R.string.ShareErrorStoragePermissionDenied)
          .setNegativeButton(R.string.ShareErrorDialogLeave, dialogInterface)
          .setNeutralButton(R.string.ShareErrorDialogAppSettings, dialogInterface);
        AlertDialog dialog = db.create();
        dialog.show();
      }
    }
  }

  /**
   * Performs these operations asynchronously:
   * <ol>
   * <li>Check each file URI in mAttachmentsUris</li>
   * <li>If the file is less than 10MB, add it to postInfo</li>
   * <li>Stop after 10 files</li>
   * <li>Generate a bitmap for the thumbnail</li>
   * <li>Wait until the Compose fragment is ready and display the thumbnail</li>
   * </ol>
   * 
   * @author paristote
   */
  private class PrepareAttachmentsTask extends AsyncTask<Void, Void, Void> {

    private Bitmap thumbnail = null;

    private String errorMessage;

    private Bitmap getThumbnail(File origin) {
      BitmapFactory.Options opts = new BitmapFactory.Options();
      opts.inSampleSize = 4;
      opts.inPreferredConfig = Bitmap.Config.RGB_565;
      return BitmapFactory.decodeFile(origin.getAbsolutePath(), opts);
    }

    private File getFileWithRotatedBitmap(DocumentInfo info, String filename) throws IOException {
      FileOutputStream fos = null;
      try {
        // Decode bitmap from input stream
        Bitmap bm = BitmapFactory.decodeStream(info.documentData);
        // Turn the image in the correct orientation
        bm = ExoDocumentUtils.rotateBitmapByAngle(bm, info.orientationAngle);
        File file = new File(getFilesDir(), filename);
        fos = new FileOutputStream(file);
        bm.compress(CompressFormat.JPEG, 100, fos);
        fos.flush();
        return file;
      } catch (OutOfMemoryError e) {
        throw new RuntimeException("Exception while decoding/rotating the bitmap", e);
      } finally {
        try {
          // try..catch here to not break the process if close() fails
          if (fos != null)
            fos.close();
        } catch (IOException ignored) {
        }
      }
    }

    private File getFileWithData(DocumentInfo info, String filename) throws IOException {
      FileOutputStream fileOutput = null;
      BufferedInputStream buffInput = null;
      try {
        // create temp file
        fileOutput = openFileOutput(filename, MODE_PRIVATE);
        buffInput = new BufferedInputStream(info.documentData);
        byte[] buf = new byte[1024];
        int len;
        while ((len = buffInput.read(buf)) != -1) {
          fileOutput.write(buf, 0, len);
        }
        return new File(getFilesDir(), filename);
      } finally {
        try {
          // try..catch here to not break the process if close() fails
          if (buffInput != null)
            buffInput.close();
          if (fileOutput != null)
            fileOutput.close();
        } catch (IOException ignored) {
        }
      }
    }

    @Override
    protected Void doInBackground(Void... params) {
      Set<Integer> errors = new HashSet<Integer>();
      if (mAttachmentUris != null && !mAttachmentUris.isEmpty()) {
        postInfo.postAttachedFiles = new ArrayList<String>(ExoConstants.SHARE_EXTENSION_MAX_ITEMS);
        for (Uri att : mAttachmentUris) {
          // Stop when we reach the maximum number of files
          if (postInfo.postAttachedFiles.size() == ExoConstants.SHARE_EXTENSION_MAX_ITEMS) {
            errors.add(R.string.ShareErrorTooManyFiles);
            break;
          }
          DocumentInfo info = ExoDocumentUtils.documentInfoFromUri(att, getApplicationContext());
          // Skip if the file cannot be read
          if (info == null) {
            errors.add(R.string.ShareErrorCannotReadDoc);
            continue;
          }
          // Skip if the file is more than 10MB
          if (info.documentSizeKb > (ExoConstants.SHARE_EXTENSION_MAX_SIZE_MB * 1024)) {
            errors.add(R.string.ShareErrorFileTooBig);
            continue;
          }
          // All good, let's copy this file in our app's storage
          // We must do this because some sharing apps (e.g. Google Photos)
          // will revoke the permission on the files when the activity stops,
          // therefore the service won't be able to access them
          String cleanName = ExoDocumentUtils.cleanupFilename(info.documentName);
          String tempFileName = DateFormat.format("yyyy-MM-dd-HH:mm:ss", System.currentTimeMillis()) + "-" + cleanName;

          try {
            // Create temp file
            File tempFile = null;
            if ("image/jpeg".equals(info.documentMimeType) && info.orientationAngle != ExoDocumentUtils.ROTATION_0) {
              // For an image with an EXIF rotation information, we get the file
              // from the bitmap rotated back to its correct orientation
              tempFile = getFileWithRotatedBitmap(info, tempFileName);
            } else {
              // Otherwise we just write the data to a file
              tempFile = getFileWithData(info, tempFileName);
            }
            // add file to list
            postInfo.postAttachedFiles.add(tempFile.getAbsolutePath());
            if (thumbnail == null) {
              thumbnail = getThumbnail(tempFile);
            }
          } catch (Exception e) {
            errors.add(R.string.ShareErrorCannotReadDoc);
          }
        }
        // Done creating the files
        // Create an error message (if any) to display in onPostExecute
        if (!errors.isEmpty()) {
          StringBuilder message;
          if (postInfo.postAttachedFiles.size() == 0)
            message = new StringBuilder(getString(R.string.ShareErrorAllFilesCannotShare)).append(":");
          else
            message = new StringBuilder(getString(R.string.ShareErrorSomeFilesCannotShare)).append(":");
          for (Integer errCode : errors) {
            switch (errCode) {
            case R.string.ShareErrorCannotReadDoc:
            case R.string.ShareErrorFileTooBig:
            case R.string.ShareErrorTooManyFiles:
              message.append("\n").append(getString(errCode));
              break;
            }
          }
          errorMessage = message.toString();
        }
        while (ComposeFragment.getFragment() == null) {
          // Wait until the compose fragment is ready
        }
      }
      return null;
    }

    @Override
    protected void onPostExecute(Void result) {
      ComposeFragment.getFragment().setThumbnailImage(thumbnail);
      if (postInfo.postAttachedFiles != null)
        ComposeFragment.getFragment().setNumberOfAttachments(postInfo.postAttachedFiles.size());
      if (errorMessage != null)
        Toast.makeText(ShareActivity.this, errorMessage, Toast.LENGTH_LONG).show();
    }
  }

  /**
   * Perform these operations in background:
   * <ol>
   * <li>Logout any currently logged in account</li>
   * <li>Login with the given account</li>
   * <li>If login is successful, setup the social client and services</li>
   * </ol>
   * 
   * @author paristote
   */
  private class LoginTask extends AsyncTask<ExoAccount, Void, Integer> {

    @Override
    protected void onPreExecute() {
      toggleProgressVisible(true);
      super.onPreExecute();
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Integer doInBackground(ExoAccount... accounts) {
      ExoConnectionUtils.loggingOut();
      String username = accounts[0].username;
      String password = accounts[0].password;
      String url = accounts[0].serverUrl + "/rest/private/platform/info";
      try {
        Log.d(LOG_TAG, String.format("Started login request to %s ...", url));
        HttpResponse resp = ExoConnectionUtils.getPlatformResponse(username, password, url);
        ExoConnectionUtils.checkPLFVersion(resp, accounts[0].serverUrl, username);
        int result = ExoConnectionUtils.checkPlatformRespose(resp);
        if (ExoConnectionUtils.LOGIN_SUCCESS == result) {
          URL u = new URL(accounts[0].serverUrl);
          SocialClientContext.setProtocol(u.getProtocol());
          SocialClientContext.setHost(u.getHost());
          SocialClientContext.setPort(u.getPort());
          SocialClientContext.setPortalContainerName(ExoConstants.ACTIVITY_PORTAL_CONTAINER);
          SocialClientContext.setRestContextName(ExoConstants.ACTIVITY_REST_CONTEXT);
          SocialClientContext.setUsername(username);
          SocialClientContext.setPassword(password);
          ClientServiceFactory clientServiceFactory = ClientServiceFactoryHelper.getClientServiceFactory();
          VersionService versionService = clientServiceFactory.createVersionService();
          SocialClientContext.setRestVersion(versionService.getLatest());
          SocialServiceHelper.getInstance().activityService = clientServiceFactory.createActivityService();
          SocialServiceHelper.getInstance().spaceService = clientServiceFactory.createSpaceService();
          SocialServiceHelper.getInstance().identityService = clientServiceFactory.createIdentityService();
        }
        return result;
      } catch (MalformedURLException e) {
        Log.e(LOG_TAG, "Login task failed", e);
      } catch (IOException e) {
        Log.e(LOG_TAG, "Login task failed", e);
      } catch (SocialClientLibException e) {
        Log.e(LOG_TAG, "Login task failed", e);
      } catch (Exception e) {
        // XXX cannot replace because SocialClientLib can throw exceptions like
        // ServerException, UnsupportMethod ,..
        Log.e(LOG_TAG, "Login task failed", e);
      }
      return ExoConnectionUtils.LOGIN_FAILED;
    }

    @Override
    protected void onCancelled() {
      super.onCancelled();
      toggleProgressVisible(false);
    }

    @Override
    protected void onPostExecute(Integer result) {
      Log.d(LOG_TAG, String.format("Received login response %s", result));
      if (ExoConnectionUtils.LOGIN_SUCCESS == result) {
        online = true;
      } else {
        Toast.makeText(getApplicationContext(), R.string.ShareErrorSignInFailed, Toast.LENGTH_LONG).show();
        postInfo.ownerAccount.password = "";
        online = false;
      }
      toggleProgressVisible(false);
      enableDisableMainButton(online && !"".equals(ComposeFragment.getFragment().getPostMessage()));
    }
  }

  // TODO support for text content that contains an URL to download a file
  // e.g. share from dropbox
  @SuppressWarnings("unused")
  private class DownloadTask extends AsyncTask<String, Void, String> {

    private String extractUriFromText(String text) {
      int posHttp = text.indexOf("http://");
      int posHttps = text.indexOf("https://");
      int startOfLink = -1;
      if (posHttps > -1)
        startOfLink = posHttps;
      else if (posHttp > -1)
        startOfLink = posHttp;
      if (startOfLink > -1) {
        int endOfLink = text.indexOf(' ', startOfLink);
        if (endOfLink == -1)
          endOfLink = text.length() - startOfLink;
        return text.substring(startOfLink, endOfLink);
      } else {
        return null;
      }
    }

    private String getUriWithoutQueryString(URI uri) {
      StringBuffer buf = new StringBuffer(uri.getScheme()).append("://")
                                                          .append(uri.getHost())
                                                          .append(uri.getPort() != -1 ? ":" + uri.getPort() : "")
                                                          .append(uri.getRawPath() != null ? uri.getRawPath() : "");
      return buf.toString();
    }

    private String getFileName(String decodedPath) {
      if (decodedPath == null)
        return DEFAULT_CONTENT_NAME;
      if (decodedPath.endsWith("/"))
        decodedPath = decodedPath.substring(0, decodedPath.length() - 1);
      int beginNamePos = decodedPath.lastIndexOf('/');
      if (beginNamePos < 0)
        return DEFAULT_CONTENT_NAME;
      String name = decodedPath.substring(beginNamePos + 1);
      if (name == null || "".equals(name))
        return DEFAULT_CONTENT_NAME;
      else
        return name;
    }

    @Override
    protected String doInBackground(String... params) {
      String str = params[0];
      if (str != null) {
        try {
          URI uri = URI.create(extractUriFromText(str));
          String strUri = getUriWithoutQueryString(uri);
          Log.d(LOG_TAG, "Started download of " + strUri);
          BufferedInputStream in = new BufferedInputStream(new URL(strUri).openStream());
          String fileName = getFileName(uri.getPath());
          FileOutputStream out = openFileOutput(fileName, 0);
          byte[] buf = new byte[1024];
          int len;
          while ((len = in.read(buf)) != -1) {
            out.write(buf, 0, len);
          }
          out.close();
          in.close();
          // comment because DownloadTask is unused now, no need to update
          // code to match multi share case
          // postInfo.postAttachmentUri = new URI("file://" +
          // getFileStreamPath(fileName).getAbsolutePath()).toString();
          // isLocalFile = true;
          // Log.d(LOG_TAG, "Download successful: ");
        } catch (Exception e) {
          Log.e(LOG_TAG, "Error ", e);
        }
      }
      return null;
    }
  }
}
