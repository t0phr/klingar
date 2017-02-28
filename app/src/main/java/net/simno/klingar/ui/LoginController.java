/*
 * Copyright (C) 2016 Simon Norberg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.simno.klingar.ui;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.widget.ContentLoadingProgressBar;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bluelinelabs.conductor.RouterTransaction;
import com.squareup.okhttp.Credentials;

import net.simno.klingar.BuildConfig;
import net.simno.klingar.KlingarApp;
import net.simno.klingar.R;
import net.simno.klingar.data.LoginManager;
import net.simno.klingar.data.api.PlexService;
import net.simno.klingar.data.api.model.User;
import net.simno.klingar.util.RxHelper;
import net.simno.klingar.util.SimpleSingleObserver;
import net.simno.klingar.util.Strings;

import javax.inject.Inject;

import butterknife.BindString;
import butterknife.BindView;
import butterknife.OnEditorAction;

import static com.bluelinelabs.conductor.rxlifecycle2.ControllerEvent.DETACH;
import static net.simno.klingar.ui.ToolbarOwner.TITLE_GONE;
import static net.simno.klingar.util.Views.invisible;
import static net.simno.klingar.util.Views.visible;

public class LoginController extends BaseController {

  @BindView(R.id.login_form) LinearLayout loginForm;
  @BindView(R.id.username_edit) EditText usernameEdit;
  @BindView(R.id.password_edit) EditText passwordEdit;
  @BindView(R.id.content_loading) ContentLoadingProgressBar contentLoading;
  @BindString(R.string.app_name) String appName;
  @BindString(R.string.invalid_username) String invalidUsername;
  @BindString(R.string.invalid_password) String invalidPassword;

  @Inject ToolbarOwner toolbarOwner;
  @Inject PlexService plex;
  @Inject LoginManager loginManager;
  @Inject InputMethodManager imm;

  public LoginController(Bundle args) {
    super(args);
  }

  @Override protected int getLayoutResource() {
    return R.layout.controller_login;
  }

  @Override protected void injectDependencies() {
    if (getActivity() != null) {
      KlingarApp.get(getActivity()).component().inject(this);
    }
  }

  @NonNull @Override
  protected View onCreateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
    View view = super.onCreateView(inflater, container);
    toolbarOwner.setConfig(ToolbarOwner.Config.builder()
        .background(false)
        .backNavigation(false)
        .titleAlpha(TITLE_GONE)
        .build());
    usernameEdit.requestFocus();
    contentLoading.hide();
    return view;
  }

  @Override protected void onAttach(@NonNull View view) {
    super.onAttach(view);
    debugLogin();
  }

  private void debugLogin() {
    if (BuildConfig.DEBUG) {
      if (!TextUtils.equals(BuildConfig.DEBUG_USER, "null")
          && !TextUtils.equals(BuildConfig.DEBUG_PWD, "null")) {
        usernameEdit.setText(BuildConfig.DEBUG_USER);
        passwordEdit.setText(BuildConfig.DEBUG_PWD);
        login();
      }
    }
  }

  @OnEditorAction(R.id.password_edit)
  boolean onPasswordEditorAction(TextView view, int actionId, KeyEvent event) {
    if ((event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)
        || (actionId == EditorInfo.IME_ACTION_DONE)) {
      login();
      return true;
    }
    return false;
  }

  private void login() {
    disableInput();

    String username = usernameEdit.getText().toString();
    String password = passwordEdit.getText().toString();

    if (Strings.isBlank(username)) {
      usernameEdit.setError(invalidUsername);
      enableInput();
      return;
    }
    if (Strings.isBlank(password) || password.length() < 8) {
      passwordEdit.setError(invalidPassword);
      enableInput();
      return;
    }

    hideInputMethod();
    invisible(loginForm);
    contentLoading.show();
    disposables.add(plex.signIn(Credentials.basic(username, password))
        .compose(bindUntilEvent(DETACH))
        .compose(RxHelper.singleSchedulers())
        .subscribeWith(new SimpleSingleObserver<User>() {
          @Override public void onSuccess(User user) {
            loginManager.login(user.authenticationToken);
            if (getActivity() != null) {
              getActivity().invalidateOptionsMenu();
            }
            getRouter().setRoot(RouterTransaction.with(new BrowserController(null)));
          }

          @Override public void onError(Throwable e) {
            super.onError(e);
            loginManager.logout();
            contentLoading.hide();
            visible(loginForm);
            showToast(R.string.sign_in_failed);
            enableInput();
          }
        }));
  }

  private void enableInput() {
    usernameEdit.setEnabled(true);
    passwordEdit.setEnabled(true);
  }

  private void disableInput() {
    usernameEdit.setEnabled(false);
    passwordEdit.setEnabled(false);
  }

  private void hideInputMethod() {
    imm.hideSoftInputFromWindow(usernameEdit.getWindowToken(), 0);
    imm.hideSoftInputFromWindow(passwordEdit.getWindowToken(), 0);
  }
}
