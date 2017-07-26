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

package com.archos.mediacenter.video.browser;

import com.archos.mediacenter.utils.ThumbnailEngine;
import com.archos.mediacenter.utils.ThumbnailRequest;
import com.archos.mediacenter.utils.ThumbnailRequester;

/**
 * As it is now it should be called ThumbnailRequesterVideo...
 * May evolve later to be Video/Music/Anything agnostic
 */
public class ThumbnailRequesterVideo extends ThumbnailRequester {
    private final static String TAG = "ThumbnailRequesterVideo";
	private final static boolean DBG = false;

	public ThumbnailRequesterVideo(ThumbnailEngine engine, ThumbnailAdapterVideo adapterVideo) {
	    super(engine, adapterVideo);
	}

	public ThumbnailRequest getThumbnailRequest(int position, String debugLog) {
	    final ThumbnailAdapterVideo adapterVideo = (ThumbnailAdapterVideo)mAdapter;
	    return adapterVideo.getThumbnailRequest(position);
	}

	/**
     * Returns true if the request is still matching the content of the list (it may have been changed since the request was sent)
     */
	@Override
    public boolean isRequestStillValid(ThumbnailRequest request) {
	    final ThumbnailAdapterVideo adapterVideo = (ThumbnailAdapterVideo)mAdapter;
        // verify that the list position and the library ID match
	    ThumbnailRequest tr = adapterVideo.getThumbnailRequest(request.getListPosition());
        return ((tr!=null) && (tr.getMediaDbId() == request.getMediaDbId()));
    }
}
