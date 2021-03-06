/*
 * Copyright 2018 The Android Open Source Project
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

package androidx.media.test.service;

import static android.support.mediacompat.testlib.util.IntentUtil.CLIENT_PACKAGE_NAME;

import android.text.TextUtils;

import androidx.media2.MediaSession2;
import androidx.media2.MediaSessionService2;
import androidx.media2.SessionCommandGroup2;

import java.util.concurrent.Executors;

public class MockMediaSessionService2 extends MediaSessionService2 {
    /**
     * ID of the session that this service will create.
     */
    public static final String ID = "TestSession";
    public MediaSession2 mSession2;

    @Override
    public void onCreate() {
        TestServiceRegistry.getInstance().setServiceInstance(this);
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        TestServiceRegistry.getInstance().cleanUp();
    }

    @Override
    public MediaSession2 onGetSession() {
        TestServiceRegistry registry = TestServiceRegistry.getInstance();
        TestServiceRegistry.OnGetSessionHandler onGetSessionHandler =
                registry.getOnGetSessionHandler();
        if (onGetSessionHandler != null) {
            return onGetSessionHandler.onGetSession();
        }

        if (mSession2 == null) {
            MediaSession2.SessionCallback callback = registry.getSessionCallback();
            mSession2 = new MediaSession2.Builder(MockMediaSessionService2.this, new MockPlayer(0))
                    .setId(ID)
                    .setSessionCallback(Executors.newSingleThreadExecutor(),
                            callback != null ? callback : new TestSessionCallback())
                    .build();
        }
        return mSession2;
    }

    private class TestSessionCallback extends MediaSession2.SessionCallback {
        @Override
        public SessionCommandGroup2 onConnect(MediaSession2 session,
                MediaSession2.ControllerInfo controller) {
            if (TextUtils.equals(CLIENT_PACKAGE_NAME, controller.getPackageName())) {
                return super.onConnect(session, controller);
            }
            return null;
        }
    }
}
