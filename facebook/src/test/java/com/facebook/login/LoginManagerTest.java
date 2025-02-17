/*
 * Copyright (c) 2014-present, Facebook, Inc. All rights reserved.
 *
 * You are hereby granted a non-exclusive, worldwide, royalty-free license to use,
 * copy, modify, and distribute this software in source code or binary form for use
 * in connection with the web services and APIs provided by Facebook.
 *
 * As with any software that integrates with the Facebook platform, your use of
 * this software is subject to the Facebook Developer Principles and Policies
 * [http://developers.facebook.com/policy/]. This copyright notice shall be
 * included in all copies or substantial portions of the software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.facebook.login;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.doThrow;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Looper;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.ActivityResultRegistry;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityOptionsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import com.facebook.AccessToken;
import com.facebook.AccessTokenSource;
import com.facebook.FacebookActivity;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookPowerMockTestCase;
import com.facebook.FacebookSdk;
import com.facebook.FacebookSdkNotInitializedException;
import com.facebook.Profile;
import com.facebook.internal.CallbackManagerImpl;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.internal.util.collections.Sets;
import org.powermock.core.classloader.annotations.PrepareForTest;

@PrepareForTest({FacebookSdk.class, AccessToken.class, Profile.class})
public class LoginManagerTest extends FacebookPowerMockTestCase {

  private static final String MOCK_APP_ID = "1234";
  private static final String USER_ID = "1000";
  private final String TOKEN_STRING = "A token of my esteem";
  private final List<String> PERMISSIONS = Arrays.asList("walk", "chew gum");
  private final Date EXPIRES = new Date(2025, 5, 3);
  private final Date LAST_REFRESH = new Date(2023, 8, 15);
  private final Date DATA_ACCESS_EXPIRATION_TIME = new Date(2025, 5, 3);

  @Mock public Activity mockActivity;
  @Mock public Fragment mockFragment;
  @Mock public Context mockApplicationContext;
  @Mock public PackageManager mockPackageManager;
  @Mock public FacebookCallback<LoginResult> mockCallback;
  @Mock public ThreadPoolExecutor threadExecutor;
  @Mock public FragmentActivity mockFragmentActivity;
  @Mock public SharedPreferences mockSharedPreferences;
  @Mock public SharedPreferences.Editor mockEditor;
  @Mock public Looper mockLooper;

  @Before
  public void before() throws Exception {
    mockStatic(FacebookSdk.class);

    mockStatic(AccessToken.class);
    when(AccessToken.getCurrentAccessToken()).thenReturn(null);

    mockStatic(Profile.class);

    when(FacebookSdk.isInitialized()).thenReturn(true);
    when(FacebookSdk.getApplicationId()).thenReturn(MOCK_APP_ID);
    when(FacebookSdk.getApplicationContext()).thenReturn(mockApplicationContext);
    when(FacebookSdk.getExecutor()).thenReturn(threadExecutor);
    when(mockFragment.getActivity()).thenReturn(mockFragmentActivity);
    when(mockActivity.getApplicationContext()).thenReturn(mockApplicationContext);
    when(FacebookSdk.getApplicationContext().getSharedPreferences(anyString(), anyInt()))
        .thenReturn(mockSharedPreferences);
    when(mockSharedPreferences.edit()).thenReturn(mockEditor);
    when(mockEditor.putBoolean(anyString(), anyBoolean())).thenReturn(mockEditor);

    // We use mocks rather than RobolectricPackageManager because it's simpler to not
    // have to specify Intents. Default to resolving all intents to something.
    ResolveInfo resolveInfo = new ResolveInfo();
    when(mockApplicationContext.getPackageManager()).thenReturn(mockPackageManager);
    when(mockApplicationContext.getMainLooper()).thenReturn(mockLooper);
    when(mockApplicationContext.getApplicationContext()).thenReturn(mockApplicationContext);
    when(mockPackageManager.resolveActivity(any(Intent.class), anyInt())).thenReturn(resolveInfo);
  }

  @Test
  public void testRequiresSdkToBeInitialized() {
    try {
      when(FacebookSdk.isInitialized()).thenReturn(false);

      LoginManager loginManager = new LoginManager();

      fail();
    } catch (FacebookSdkNotInitializedException exception) {
    }
  }

  @Test
  public void testGetInstance() {
    LoginManager loginManager = LoginManager.getInstance();
    assertNotNull(loginManager);
  }

  @Test
  public void testLoginBehaviorDefaultsToSsoWithFallback() {
    LoginManager loginManager = new LoginManager();
    assertEquals(LoginBehavior.NATIVE_WITH_FALLBACK, loginManager.getLoginBehavior());
  }

  @Test
  public void testCanChangeLoginBehavior() {
    LoginManager loginManager = new LoginManager();
    loginManager.setLoginBehavior(LoginBehavior.NATIVE_ONLY);
    assertEquals(LoginBehavior.NATIVE_ONLY, loginManager.getLoginBehavior());
  }

  @Test
  public void testDefaultAudienceDefaultsToFriends() {
    LoginManager loginManager = new LoginManager();
    assertEquals(DefaultAudience.FRIENDS, loginManager.getDefaultAudience());
  }

  @Test
  public void testCanChangeDefaultAudience() {
    LoginManager loginManager = new LoginManager();
    loginManager.setDefaultAudience(DefaultAudience.EVERYONE);
    assertEquals(DefaultAudience.EVERYONE, loginManager.getDefaultAudience());
  }

  @Test
  public void testLogInWithReadAndActivityThrowsIfPublishPermissionGiven() {
    LoginManager loginManager = new LoginManager();
    try {
      loginManager.logInWithReadPermissions(
          mockActivity, Arrays.asList("public_profile", "publish_actions"));
      fail();
    } catch (FacebookException exception) {
    }
  }

  @Test
  public void testLogInWithPublishAndActivityThrowsIfPublishPermissionGiven() {
    LoginManager loginManager = new LoginManager();
    try {
      loginManager.logInWithPublishPermissions(
          mockActivity, Arrays.asList("public_profile", "publish_actions"));
      fail();
    } catch (FacebookException exception) {
    }
  }

  @Test
  public void testLogInThrowsIfCannotResolveFacebookActivity() {
    when(mockPackageManager.resolveActivity(any(Intent.class), anyInt())).thenReturn(null);

    LoginManager loginManager = new LoginManager();

    try {
      loginManager.logInWithReadPermissions(
          mockActivity, Arrays.asList("public_profile", "user_friends"));
      fail();
    } catch (FacebookException exception) {
    }
  }

  @Test
  public void testLogInThrowsIfCannotStartFacebookActivity() {
    doThrow(new ActivityNotFoundException())
        .when(mockActivity)
        .startActivityForResult(any(Intent.class), anyInt());

    LoginManager loginManager = new LoginManager();

    try {
      loginManager.logInWithReadPermissions(
          mockActivity, Arrays.asList("public_profile", "user_friends"));
      fail();
    } catch (FacebookException exception) {
    }
  }

  @Test
  public void testRequiresNonNullActivity() {
    try {
      LoginManager loginManager = new LoginManager();
      loginManager.logInWithReadPermissions(
          (Activity) null, Arrays.asList("public_profile", "user_friends"));
      fail();
    } catch (NullPointerException exception) {
    }
  }

  @Test
  public void testRequiresNonNullFragment() {
    try {
      LoginManager loginManager = new LoginManager();
      loginManager.logInWithReadPermissions(
          (Fragment) null, Arrays.asList("public_profile", "user_friends"));
      fail();
    } catch (NullPointerException exception) {
    }
  }

  @Test
  public void testLogInWithReadDoesNotThrowWithReadPermissions() {
    LoginManager loginManager = new LoginManager();
    loginManager.logInWithReadPermissions(
        mockActivity, Arrays.asList("public_profile", "user_friends"));
  }

  @Test
  public void testLogInWithReadListCreatesPendingRequestWithCorrectValues() {
    LoginManager loginManager = new LoginManager();
    // Change some defaults so we can verify the pending request picks them up.
    loginManager.setLoginBehavior(LoginBehavior.NATIVE_ONLY);
    loginManager.setDefaultAudience(DefaultAudience.EVERYONE);
    loginManager.logInWithReadPermissions(
        mockActivity, Arrays.asList("public_profile", "user_friends"));
    implTestLogInCreatesPendingRequestWithCorrectValues(
        loginManager, Arrays.asList("public_profile", "user_friends"));
  }

  @Test
  public void testLogInWithReadAndAccessTokenCreatesReauthRequest() {
    AccessToken accessToken = createAccessToken();
    when(AccessToken.getCurrentAccessToken()).thenReturn(accessToken);

    LoginManager loginManager = new LoginManager();
    loginManager.logInWithReadPermissions(
        mockActivity, Arrays.asList("public_profile", "user_friends"));
    int loginRequestCode = CallbackManagerImpl.RequestCodeOffset.Login.toRequestCode();
    verify(mockActivity, times(1)).startActivityForResult(any(Intent.class), eq(loginRequestCode));
  }

  public void implTestLogInCreatesPendingRequestWithCorrectValues(
      LoginManager loginManager, final Collection<String> expectedPermissions) {

    ArgumentMatcher<Intent> m =
        new ArgumentMatcher<Intent>() {
          @Override
          public boolean matches(Intent argument) {
            Intent orig = argument;
            Bundle bundle = orig.getBundleExtra(LoginFragment.REQUEST_KEY);
            LoginClient.Request request =
                (LoginClient.Request) bundle.getParcelable(LoginFragment.EXTRA_REQUEST);
            assertEquals(MOCK_APP_ID, request.getApplicationId());
            assertEquals(LoginBehavior.NATIVE_ONLY, request.getLoginBehavior());
            assertEquals(DefaultAudience.EVERYONE, request.getDefaultAudience());

            Set<String> permissions = request.getPermissions();
            for (String permission : expectedPermissions) {
              assertTrue(permissions.contains(permission));
            }

            return true;
          }
        };
    int loginRequestCode = CallbackManagerImpl.RequestCodeOffset.Login.toRequestCode();
    verify(mockActivity, times(1)).startActivityForResult(argThat(m), eq(loginRequestCode));
  }

  @Test
  public void testLogInWithReadAndActivityStartsFacebookActivityWithCorrectRequest() {

    LoginManager loginManager = new LoginManager();
    loginManager.logInWithReadPermissions(
        mockActivity, Arrays.asList("public_profile", "user_friends"));

    ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
    verify(mockActivity).startActivityForResult(intentArgumentCaptor.capture(), anyInt());
    Intent intent = intentArgumentCaptor.getValue();

    ComponentName componentName = intent.getComponent();
    assertEquals(FacebookActivity.class.getName(), componentName.getClassName());
    assertEquals(LoginBehavior.NATIVE_WITH_FALLBACK.name(), intent.getAction());
  }

  @Test
  public void testLogInWithReadAndFragmentStartsFacebookActivityWithCorrectRequest() {

    LoginManager loginManager = new LoginManager();
    loginManager.logInWithReadPermissions(
        mockFragment, Arrays.asList("public_profile", "user_friends"));

    ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
    verify(mockFragment).startActivityForResult(intentArgumentCaptor.capture(), anyInt());
    Intent intent = intentArgumentCaptor.getValue();

    ComponentName componentName = intent.getComponent();
    assertEquals(FacebookActivity.class.getName(), componentName.getClassName());
    assertEquals(LoginBehavior.NATIVE_WITH_FALLBACK.name(), intent.getAction());
  }

  @Test
  public void testLogInWitPublishDoesNotThrowWithPublishPermissions() {
    LoginManager loginManager = new LoginManager();
    loginManager.logInWithPublishPermissions(
        mockActivity, Arrays.asList("publish_actions", "publish_stream"));
  }

  @Test
  public void testLogInWithPublishListCreatesPendingRequestWithCorrectValues() {
    LoginManager loginManager = new LoginManager();
    // Change some defaults so we can verify the pending request picks them up.
    loginManager.setLoginBehavior(LoginBehavior.NATIVE_ONLY);
    loginManager.setDefaultAudience(DefaultAudience.EVERYONE);
    loginManager.logInWithPublishPermissions(
        mockActivity, Arrays.asList("publish_actions", "publish_stream"));

    implTestLogInCreatesPendingRequestWithCorrectValues(
        loginManager, Arrays.asList("publish_actions", "publish_stream"));
  }

  @Test
  public void testLogInWithPublishAndAccessTokenCreatesReauthRequest() {
    AccessToken accessToken = createAccessToken();
    when(AccessToken.getCurrentAccessToken()).thenReturn(accessToken);

    LoginManager loginManager = new LoginManager();
    loginManager.logInWithPublishPermissions(
        mockActivity, Arrays.asList("publish_actions", "publish_stream"));

    int loginRequestCode = CallbackManagerImpl.RequestCodeOffset.Login.toRequestCode();
    verify(mockActivity, times(1)).startActivityForResult(any(Intent.class), eq(loginRequestCode));
  }

  @Test
  public void testOnActivityResultReturnsTrueAndCallsCallbackOnCancelResultCode() {
    LoginManager loginManager = new LoginManager();
    loginManager.logInWithReadPermissions(
        mockActivity, Arrays.asList("public_profile", "user_friends"));

    boolean result = loginManager.onActivityResult(Activity.RESULT_CANCELED, null, mockCallback);

    assertTrue(result);
    verify(mockCallback, times(1)).onCancel();
    verify(mockCallback, never()).onSuccess(isA(LoginResult.class));
  }

  @Test
  public void testOnActivityResultReturnsTrueAndCallsCallbackOnCancelResultCodeEvenWithData() {
    LoginManager loginManager = new LoginManager();
    loginManager.logInWithReadPermissions(
        mockActivity, Arrays.asList("public_profile", "user_friends"));

    Intent intent = createSuccessResultIntent();
    boolean result = loginManager.onActivityResult(Activity.RESULT_CANCELED, intent, mockCallback);

    assertTrue(result);
    verify(mockCallback, times(1)).onCancel();
    verify(mockCallback, never()).onSuccess(isA(LoginResult.class));
  }

  @Test
  public void testOnActivityResultDoesNotModifyCurrentAccessTokenOnCancelResultCode() {
    LoginManager loginManager = new LoginManager();
    loginManager.logInWithReadPermissions(
        mockActivity, Arrays.asList("public_profile", "user_friends"));

    loginManager.onActivityResult(Activity.RESULT_CANCELED, null, mockCallback);

    verifyStatic(AccessToken.class, never());
    AccessToken.setCurrentAccessToken(any(AccessToken.class));
  }

  @Test
  public void testOnActivityResultHandlesMissingCallbackOnCancelResultCode() {
    LoginManager loginManager = new LoginManager();
    loginManager.logInWithReadPermissions(
        mockActivity, Arrays.asList("public_profile", "user_friends"));

    boolean result = loginManager.onActivityResult(Activity.RESULT_CANCELED, null);

    assertTrue(result);
  }

  @Test
  public void testOnActivityResultReturnsTrueAndCallsCallbackOnNullData() {
    LoginManager loginManager = new LoginManager();
    loginManager.logInWithReadPermissions(
        mockActivity, Arrays.asList("public_profile", "user_friends"));

    boolean result = loginManager.onActivityResult(Activity.RESULT_OK, null, mockCallback);

    assertTrue(result);
    verify(mockCallback, times(1)).onError(isA(FacebookException.class));
    verify(mockCallback, never()).onSuccess(isA(LoginResult.class));
  }

  @Test
  public void testOnActivityResultReturnsTrueAndCallsCallbackOnMissingResult() {
    LoginManager loginManager = new LoginManager();
    loginManager.logInWithReadPermissions(
        mockActivity, Arrays.asList("public_profile", "user_friends"));

    Intent intent = createSuccessResultIntent();
    intent.removeExtra(LoginFragment.RESULT_KEY);
    boolean result = loginManager.onActivityResult(Activity.RESULT_OK, intent, mockCallback);

    assertTrue(result);
    verify(mockCallback, times(1)).onError(isA(FacebookException.class));
    verify(mockCallback, never()).onSuccess(isA(LoginResult.class));
  }

  @Test
  public void testOnActivityResultReturnsTrueAndCallsCallbackOnErrorResult() {
    LoginManager loginManager = new LoginManager();
    loginManager.logInWithReadPermissions(
        mockActivity, Arrays.asList("public_profile", "user_friends"));

    boolean result =
        loginManager.onActivityResult(Activity.RESULT_OK, createErrorResultIntent(), mockCallback);

    ArgumentCaptor<FacebookException> exceptionArgumentCaptor =
        ArgumentCaptor.forClass(FacebookException.class);

    assertTrue(result);
    verify(mockCallback, times(1)).onError(exceptionArgumentCaptor.capture());
    verify(mockCallback, never()).onSuccess(isA(LoginResult.class));
    assertEquals("foo: bar", exceptionArgumentCaptor.getValue().getMessage());
  }

  @Test
  public void testOnActivityResultReturnsTrueAndCallsCallbackOnCancelResult() {
    LoginManager loginManager = new LoginManager();
    loginManager.logInWithReadPermissions(
        mockActivity, Arrays.asList("public_profile", "user_friends"));

    boolean result =
        loginManager.onActivityResult(
            Activity.RESULT_CANCELED, createCancelResultIntent(), mockCallback);

    assertTrue(result);
    verify(mockCallback, times(1)).onCancel();
    verify(mockCallback, never()).onSuccess(isA(LoginResult.class));
  }

  @Test
  public void testOnActivityResultDoesNotModifyCurrentAccessTokenOnErrorResultCode() {
    LoginManager loginManager = new LoginManager();
    loginManager.logInWithReadPermissions(
        mockActivity, Arrays.asList("public_profile", "user_friends"));

    loginManager.onActivityResult(
        Activity.RESULT_CANCELED, createErrorResultIntent(), mockCallback);

    verifyStatic(AccessToken.class, never());
    AccessToken.setCurrentAccessToken(any(AccessToken.class));
  }

  @Test
  public void testOnActivityResultReturnsTrueAndCallsCallbackOnSuccessResult() {
    LoginManager loginManager = new LoginManager();
    loginManager.logInWithReadPermissions(
        mockActivity, Arrays.asList("public_profile", "user_friends"));

    boolean result =
        loginManager.onActivityResult(
            Activity.RESULT_OK, createSuccessResultIntent(), mockCallback);

    assertTrue(result);
    verify(mockCallback, never()).onError(any(FacebookException.class));
    verify(mockCallback, times(1)).onSuccess(isA(LoginResult.class));
  }

  @Test
  public void testOnHandlesMissingCallbackkOnSuccessResult() {
    LoginManager loginManager = new LoginManager();
    loginManager.logInWithReadPermissions(
        mockActivity, Arrays.asList("public_profile", "user_friends"));

    boolean result =
        loginManager.onActivityResult(Activity.RESULT_OK, createSuccessResultIntent(), null);

    assertTrue(result);
  }

  @Test
  public void testOnActivityResultSetsCurrentAccessTokenOnSuccessResult() {
    LoginManager loginManager = new LoginManager();
    loginManager.logInWithReadPermissions(
        mockActivity, Arrays.asList("public_profile", "user_friends"));

    boolean result =
        loginManager.onActivityResult(
            Activity.RESULT_OK, createSuccessResultIntent(), mockCallback);

    verifyStatic(AccessToken.class, times(1));
    AccessToken.setCurrentAccessToken(eq(createAccessToken()));
  }

  @Test
  public void testLoginWithLauncher() {
    final LoginManager loginManager = new LoginManager();
    ActivityResultRegistry registry = new ActivityResultRegistry() {
      @Override
      public <I, O> void onLaunch(int requestCode, @NonNull ActivityResultContract<I, O> contract,
                                  I input, @Nullable ActivityOptionsCompat options) {
        dispatchResult(requestCode, Activity.RESULT_OK, createSuccessResultIntent());
      }
    };
    ActivityResultLauncher<FacebookLoginContract.Request> launcher =
            registry.register("test", new FacebookLoginContract(), new ActivityResultCallback<FacebookLoginContract.Result>() {
              @Override
              public void onActivityResult(FacebookLoginContract.Result result) {
                assertTrue(result instanceof FacebookLoginContract.Result.Success);
                FacebookLoginContract.Result.Success success = (FacebookLoginContract.Result.Success) result;
                loginManager.onActivityResult(success.getResultCode(), success.getIntent(), mockCallback);
              }
            });
    loginManager.logIn(launcher, Arrays.asList("public_profile", "user_friends"));
    verifyStatic(AccessToken.class, times(1));
    verify(mockCallback, never()).onError(any(FacebookException.class));
    verify(mockCallback, times(1)).onSuccess(isA(LoginResult.class));
  }

  @Test
  public void testLoginCancelWithLauncher() {
    final LoginManager loginManager = new LoginManager();
    ActivityResultRegistry registry = new ActivityResultRegistry() {
      @Override
      public <I, O> void onLaunch(int requestCode, @NonNull ActivityResultContract<I, O> contract,
                                  I input, @Nullable ActivityOptionsCompat options) {
        dispatchResult(requestCode, Activity.RESULT_CANCELED, createCancelResultIntent());
      }
    };
    ActivityResultLauncher<FacebookLoginContract.Request> launcher =
            registry.register("test", new FacebookLoginContract(), new ActivityResultCallback<FacebookLoginContract.Result>() {
              @Override
              public void onActivityResult(FacebookLoginContract.Result result) {
                assertTrue(result instanceof FacebookLoginContract.Result.Success);
                FacebookLoginContract.Result.Success success = (FacebookLoginContract.Result.Success) result;
                loginManager.onActivityResult(success.getResultCode(), success.getIntent(), mockCallback);
              }
            });
    loginManager.logIn(launcher, Arrays.asList("public_profile", "user_friends"));
    verifyStatic(AccessToken.class, never());
    verify(mockCallback, never()).onError(any(FacebookException.class));
    verify(mockCallback, times(1)).onCancel();
  }

  @Test
  public void testLoginErrorWithLauncher() {
    final LoginManager loginManager = new LoginManager();
    ActivityResultRegistry registry = new ActivityResultRegistry() {
      @Override
      public <I, O> void onLaunch(int requestCode, @NonNull ActivityResultContract<I, O> contract,
                                  I input, @Nullable ActivityOptionsCompat options) {
        dispatchResult(requestCode, Activity.RESULT_CANCELED, createErrorResultIntent());
      }
    };
    ActivityResultLauncher<FacebookLoginContract.Request> launcher =
            registry.register("test", new FacebookLoginContract(), new ActivityResultCallback<FacebookLoginContract.Result>() {
              @Override
              public void onActivityResult(FacebookLoginContract.Result result) {
                assertTrue(result instanceof FacebookLoginContract.Result.Success);
                FacebookLoginContract.Result.Success success = (FacebookLoginContract.Result.Success) result;
                loginManager.onActivityResult(success.getResultCode(), success.getIntent(), mockCallback);
              }
            });
    loginManager.logIn(launcher, Arrays.asList("public_profile", "user_friends"));
    verifyStatic(AccessToken.class, never());
    verify(mockCallback, never()).onSuccess(any(LoginResult.class));
    verify(mockCallback, times(1)).onError(isA(FacebookException.class));
  }

  @Test
  public void testNoActivityWithLauncher() {
    final LoginManager loginManager = new LoginManager();
    ActivityResultRegistry registry = new ActivityResultRegistry() {
      @Override
      public <I, O> void onLaunch(int requestCode, @NonNull ActivityResultContract<I, O> contract,
                                  I input, @Nullable ActivityOptionsCompat options) {
        ActivityResultContract.SynchronousResult<?> syncResult = contract.getSynchronousResult(mockApplicationContext, input);
        if (syncResult == null) {
          throw new ActivityNotFoundException();
        } else {
          dispatchResult(requestCode, syncResult.getValue());
        }
      }
    };
    ActivityResultLauncher<FacebookLoginContract.Request> launcher =
            registry.register("test", new FacebookLoginContract(), new ActivityResultCallback<FacebookLoginContract.Result>() {
              @Override
              public void onActivityResult(FacebookLoginContract.Result result) {
                assertTrue(result instanceof FacebookLoginContract.Result.Error);
              }
            });
    loginManager.logIn(launcher, Arrays.asList("public_profile", "user_friends"));
  }

  private Intent createSuccessResultIntent() {
    Intent intent = new Intent();

    Set<String> permissions = Sets.newSet("public_profile", "user_friends");
    LoginClient.Request request =
        new LoginClient.Request(null, permissions, null, null, null, null);

    AccessToken accessToken = createAccessToken();
    LoginClient.Result result = LoginClient.Result.createTokenResult(request, accessToken);
    intent.putExtra(LoginFragment.RESULT_KEY, result);

    return intent;
  }

  private FacebookLoginContract.Result createSuccessResult() {
    return new FacebookLoginContract.Result.Success(Activity.RESULT_OK, createSuccessResultIntent());
  }

  private Intent createErrorResultIntent() {
    Intent intent = new Intent();

    LoginClient.Request request = mock(LoginClient.Request.class);

    LoginClient.Result result = LoginClient.Result.createErrorResult(request, "foo", "bar");
    intent.putExtra(LoginFragment.RESULT_KEY, result);

    return intent;
  }

  private Intent createCancelResultIntent() {
    Intent intent = new Intent();

    LoginClient.Request request = mock(LoginClient.Request.class);

    LoginClient.Result result = LoginClient.Result.createCancelResult(request, null);
    intent.putExtra(LoginFragment.RESULT_KEY, result);

    return intent;
  }

  private AccessToken createAccessToken() {
    return new AccessToken(
        TOKEN_STRING,
        MOCK_APP_ID,
        USER_ID,
        PERMISSIONS,
        null,
        null,
        AccessTokenSource.WEB_VIEW,
        EXPIRES,
        LAST_REFRESH,
        DATA_ACCESS_EXPIRATION_TIME);
  }
}
