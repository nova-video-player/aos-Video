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

package com.archos.mediacenter.video.utils;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.TorrentObserverService;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipException;


public class TorrentBlocklistDialogPreference extends Preference{
	AlertDialog od=null;

	protected File mCurrentDirectory;
	ProgressDialog mProgress; 
	private View mView;
	private String defaultBlocklist = "https://list.iblocklist.com/?list=bt_level1&fileformat=p2p&archiveformat=gz";

	private String mCurrentBlockList;

	public TorrentBlocklistDialogPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		
	}
	@Override
    public View onCreateView(ViewGroup parent) {
         mView = super.onCreateView(parent);
         setup();
         return mView;
    }
	public TorrentBlocklistDialogPreference(Context context, AttributeSet attrs,
			int defStyle) {
		super(context, attrs, defStyle); 
	}
	private void setup() {
		
		mProgress = new ProgressDialog(getContext());
		mProgress.setMessage(getContext().getString(R.string.blocklist_downloading));
		mProgress.setCancelable(false);
		
		if((mCurrentBlockList=getSharedPreferences().getString(getKey(), defaultBlocklist))!=null){
            File file = getContext().getFileStreamPath(TorrentObserverService.BLOCKLIST);
			if(file.exists()) {
                setSummary(mCurrentBlockList);
                mView.findViewById(R.id.button).setVisibility(View.VISIBLE);
            }
			else {
                setSummary(R.string.blocklist_not_loaded);
                mView.findViewById(R.id.button).setVisibility(View.GONE);
            }
		}
		else
			mView.findViewById(R.id.button).setVisibility(View.GONE);
		
		mView.findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                startDownload(getSharedPreferences().getString(getKey(), defaultBlocklist));
            }
        });
	}
	@Override
	protected void onBindView(View view)
	{       
		super.onBindView(view);

		TextView summary= (TextView)view.findViewById(android.R.id.summary);
		summary.setMaxLines(1); //only one line for pref
	}    
	public boolean isDialogShowing(){
		return od!=null&&od.isShowing();
	}
	@Override
	public void onClick() {
		if(getOnPreferenceClickListener()==null){

			mCurrentDirectory =new File("/");
			final EditText ed = new EditText(getContext());
			ed.setText(getSharedPreferences().getString(getKey(), defaultBlocklist));
			Builder b = new Builder(getContext());
			b.setTitle(R.string.torrent_blocklist_url);

			b.setView(ed);
			b.setNegativeButton(android.R.string.cancel, null);
			b.setPositiveButton(android.R.string.ok, new OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					if(ed.getText().toString().isEmpty()){
						showErrorDialog(getErrorString(-3));
						return;
					}
					//trying to download
					Uri uri = Uri.parse(ed.getText().toString());
					String path = "";
					String query = "";
					try {
						if(uri.getPath()!=null)
							path= URLEncoder.encode(uri.getPath(), "UTF-8").replace("+", "%20").replace("%2F", "/");
						if(uri.getQuery()!=null){
							
							for(String param : uri.getQueryParameterNames()){
								if(!query.isEmpty())
									query+="&";
								query+=URLEncoder.encode(param, "UTF-8")+"=";
								query+=uri.getQueryParameter(param);
									
							}
						}
					} catch (UnsupportedEncodingException e) {
						return;
					}

					startDownload(uri.getScheme()+"://"+uri.getHost()+path+(!query.isEmpty()?"?"+query:""));

				}


			});
			od =b.create();

			od.show();
		}
	}

	private void startDownload(final String url){
		if(url == null)
			return;

		new AsyncTask<Void, Void, Integer>() {
			@Override
			protected void onPreExecute() {    
				mProgress.show();
			}
			@Override
			protected void onProgressUpdate(Void... rien){
			}
			@Override
			protected Integer doInBackground(Void... rien) {







				try {
					URL urlCo = new URL(url);
					URLConnection mUrlConnection =  urlCo.openConnection();
					mUrlConnection.setConnectTimeout(20000);
					mUrlConnection.setReadTimeout(40000);
					int error = ((HttpURLConnection)mUrlConnection).getResponseCode();
					((HttpURLConnection)mUrlConnection).setInstanceFollowRedirects(true);

					if(!(200<=error&&300>error))
						//error
						return error;

					InputStream inputStream = mUrlConnection.getInputStream();
					FileOutputStream fileOutput = getContext().openFileOutput(TorrentObserverService.BLOCKLIST, Context.MODE_PRIVATE);
					byte[] buffer = new byte[1024];
					int bufferLength = 0;
					while ( (bufferLength = inputStream.read(buffer)) > 0 ) {
						fileOutput.write(buffer, 0, bufferLength);
					}
					fileOutput.close();
					//now we check is gzip
					try  {
						getContext().openFileInput(
								TorrentObserverService.BLOCKLIST);
						InputStream is;
						try{
							is = new GZIPInputStream(
								getContext().openFileInput(
										TorrentObserverService.BLOCKLIST));
						}
						catch (IOException e) {
							return 200;
						} 

						FileOutputStream output = getContext().openFileOutput(
								TorrentObserverService.BLOCKLIST+"_tmp", Context.MODE_PRIVATE); 
						int bufferSize = 1024;
						buffer = new byte[bufferSize];
						int len = 0;
						while ((len = is.read(buffer)) != -1) {
							output.write(buffer, 0, len);
						}
						try{
							output.close();
							is.close();
							is = getContext().openFileInput(TorrentObserverService.BLOCKLIST+"_tmp"); 
							output = getContext().openFileOutput(
									TorrentObserverService.BLOCKLIST, Context.MODE_PRIVATE);
							buffer = new byte[bufferSize];
							len = 0;
							while ((len = is.read(buffer)) != -1) {
								output.write(buffer, 0, len);
							}
							output.close();
							is.close();
							getContext().getFileStreamPath(TorrentObserverService.BLOCKLIST+"_tmp").delete();
							return 200;
						}
						catch(IOException i){                   
						}
					} 
					catch (ZipException z) {                    
						return 200;} //not a gzip
					catch (FileNotFoundException e) {

					} 
					catch (IOException e) {
					} 
				}
				catch (IOException e) {
				} 
				catch (IllegalStateException e) {
				}
				return -1;

			}

			@Override
			protected void onPostExecute(Integer success) {
				mProgress.dismiss();
				if(200<=success&&300>success){
					//saving url
					setSummary(url);
					mCurrentBlockList = url;
					getSharedPreferences().edit().putString(getKey(), url).commit();
					Toast.makeText(getContext(), getErrorString(200), Toast.LENGTH_SHORT).show();
					mView.findViewById(R.id.button).setVisibility(View.VISIBLE);
				}	
				else{
					showErrorDialog(getErrorString(success));
				}

			}
		}.execute();

	}
	private String getErrorString(int success){
		switch(success){
			case 404:
				return getContext().getString(R.string.blocklist_file_not_found);
			case 200:
				return getContext().getString(R.string.blocklist_success);
			case -2 :
				return getContext().getString(R.string.blocklist_invalid);
			case -3 :
				return getContext().getString(R.string.blocklist_empty);
				
			
			default:
				return getContext().getString(R.string.blocklist_unknown_error);
		}
	}
	private void showErrorDialog(String message){
		new AlertDialog.Builder(getContext())
		.setTitle(R.string.error_listing)
		.setMessage(message)

		.create().show();
	}


}
