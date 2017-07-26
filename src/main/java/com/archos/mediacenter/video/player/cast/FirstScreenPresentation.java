// Copyright 2017 Archos SA
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

package com.archos.mediacenter.video.player.cast;

import android.content.Context;
import android.os.Bundle;
import android.view.Display;
import android.view.TextureView;
import android.view.View;

import com.archos.mediacenter.video.R;
import com.google.android.gms.cast.CastPresentation;

/**
 * Created by alexandre on 08/06/16.
 */
public class FirstScreenPresentation extends CastPresentation {

    private final OnCreateCallback mCallback;
    private TextureView mFirstScreenSurfaceView;
    public interface OnCreateCallback{

        void onFirstScreenPresentationCreated(View rootView);
    }
    public FirstScreenPresentation(Context context,
                                   Display display, OnCreateCallback callback) {
        super(context, display);
        mCallback  =callback;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.floating_player);
        mFirstScreenSurfaceView = (TextureView)
                findViewById(R.id.gl_surface_view);
        mCallback.onFirstScreenPresentationCreated(findViewById(R.id.root));
    }
}