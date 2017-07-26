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

package com.archos.mediacenter.video.widget;

import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViewsService;

public class VideoWidgetServices {

	private final static String TAG = "VideoWidgetServices";
	private final static boolean DBG = false;
	
	public static class Movies extends RemoteViewsService {
		@Override
		public RemoteViewsFactory onGetViewFactory(Intent intent) {
			if (DBG) Log.d(TAG, "Movies onGetViewFactory");
			return new RemoteViewsFactoryMovies(this.getApplicationContext(), intent);
		}
	}

	public static class TVShows extends RemoteViewsService {
		@Override
		public RemoteViewsFactory onGetViewFactory(Intent intent) {
			if (DBG) Log.d(TAG, " TVShowsonGetViewFactory");
			return new RemoteViewsFactoryTVShows(this.getApplicationContext(), intent);
		}
	}

	public static class AllVideos extends RemoteViewsService {
	    @Override
	    public RemoteViewsFactory onGetViewFactory(Intent intent) {
	        if (DBG) Log.d(TAG, "AllVideos onGetViewFactory");
	        return new RemoteViewsFactoryAllVideos(this.getApplicationContext(), intent);
	    }
	}

	public static class RecentlyAdded extends RemoteViewsService {
	    @Override
	    public RemoteViewsFactory onGetViewFactory(Intent intent) {
	        if (DBG) Log.d(TAG, "RecentlyAdded onGetViewFactory");
	        return new RemoteViewsFactoryRecentlyAdded(this.getApplicationContext(), intent);
	    }
	}

	public static class RecentlyPlayed extends RemoteViewsService {
	    @Override
	    public RemoteViewsFactory onGetViewFactory(Intent intent) {
	        if (DBG) Log.d(TAG, "RecentlyPlayed onGetViewFactory");
	        return new RemoteViewsFactoryRecentlyPlayed(this.getApplicationContext(), intent);
	    }
	}
}