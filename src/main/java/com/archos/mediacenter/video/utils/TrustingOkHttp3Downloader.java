// Copyright 2024 Courville Software
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

import com.archos.mediacenter.video.R;

import android.content.Context;

import com.squareup.picasso.Downloader;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.Request;

// okhttp3 downloader that trusts letsencrypt certificates added manually
// because on android 7.1 and below platform certificates are not up to date
public class TrustingOkHttp3Downloader implements Downloader {

    private final Context context;

    public TrustingOkHttp3Downloader(Context context) {
        this.context = context;
    }

    @Override
    public Response load(Request request) throws IOException {
        OkHttpClient client = getOkHttpClient();
        return client.newCall(request).execute();
    }

    @Override
    public void shutdown() {
        // Empty implementation, no resources to clean up
    }

    private OkHttpClient getOkHttpClient() {
        try {
            // Load LetsEncrypt certificates from raw resources
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            InputStream certInputStream1 = context.getResources().openRawResource(R.raw.isrg_root_x1);
            Certificate certificate1 = certificateFactory.generateCertificate(certInputStream1);
            certInputStream1.close();

            InputStream certInputStream2 = context.getResources().openRawResource(R.raw.isrg_root_x2);
            Certificate certificate2 = certificateFactory.generateCertificate(certInputStream2);
            certInputStream2.close();

            // Create a TrustManager that trusts the letsencrypt loaded certificates
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null);
            keyStore.setCertificateEntry("cert1", certificate1);
            keyStore.setCertificateEntry("cert2", certificate2);

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);

            // Build the OkHttpClient with the custom TrustManager
            OkHttpClient.Builder builder = new OkHttpClient.Builder();

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

            // Check if the first TrustManager is an X509TrustManager
            if (trustManagerFactory.getTrustManagers()[0] instanceof X509TrustManager x509TrustManager) {
                builder.sslSocketFactory(sslContext.getSocketFactory(), x509TrustManager);
            } else {
                // Handle the case where the TrustManager is not X509TrustManager (unlikely)
                throw new RuntimeException("Unexpected TrustManager type");
            }

            return builder.build();
        } catch (CertificateException | IOException | KeyStoreException | NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException(e);
        }
    }
}
