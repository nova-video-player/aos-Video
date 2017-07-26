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

package com.archos.mediacenter.video.utils.oauth;


import java.net.URLEncoder;
import java.util.Iterator;

import org.json.JSONObject;

import android.util.Log;

/**
 *	Class with all the information of the authentication
 */
public class OAuthData {
	


	public String provider;		/** name of the provider */
	public String state;		/** state send */
	public String token;
	public String code;/** token received */
	public String secret;		/** secret received (only in oauth1) */
	public String status;		/** status of the request (succes, error, ....) */
	public String expires_in;	/** if the token expires */
	public String error;		/** error encountered */
	public JSONObject request;	/** API request description */
	
	
}
