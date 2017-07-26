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

package com.archos.mediacenter.video.picasso;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.archos.filecorelibrary.jcifs.JcifsFileEditor;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Request;
import com.squareup.picasso.RequestHandler;

import java.io.IOException;
import java.io.InputStream;

/**
 * Integration of smb:// images download into com.squareup.picasso
 * Created by vapillon on 23/04/15.
 */
public class SmbRequestHandler extends RequestHandler {

    private static final String TAG = "SmbRequestHandler";

    public final static String SAMBA_SCHEME = "smb";

    private final Context mContext;

    public SmbRequestHandler(Context context) {
        mContext = context;
    }

    @Override
    public boolean canHandleRequest(Request request) {
        return SAMBA_SCHEME.equals(request.uri.getScheme());
    }

    @Override
    public Result load(Request request, int networkPolicy) throws IOException {
        JcifsFileEditor editor = new JcifsFileEditor(request.uri);

        InputStream inputStream = null;
        try {
            inputStream = editor.getInputStream();
        } catch (IOException e) {
            Log.e(TAG, "Failed to get the input stream for "+request.uri, e);
            return null;
        }

        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
        inputStream.close();
        if (bitmap==null) {
            return null;
        }
        else {
            return new Result(bitmap, Picasso.LoadedFrom.NETWORK);
        }
    }
}
