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


import android.net.Uri;

import com.archos.filecorelibrary.FileEditor;
import com.archos.filecorelibrary.FileEditorFactory;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;

/**
 * Hash code is based on Media Player Classic. In natural language it calculates: size + 64bit
 * checksum of the first and last 64k (even if they overlap because the file is smaller than
 * 128k).
 */
public class OpenSubtitlesHasher {
        
        /**
         * Size of the chunks that will be hashed in bytes (64 KB)
         */
        private static final int HASH_CHUNK_SIZE = 64 * 1024;
        
        
        public static String computeHash(File file) {
                long size = file.length();
                long chunkSizeForFile = Math.min(HASH_CHUNK_SIZE, size);
                long head = 0;
                long tail = 0;
                FileInputStream fis = null;
                FileChannel fileChannel = null;
                try {
                    fis = new FileInputStream(file);
                    fileChannel = fis.getChannel();
                    head = computeHashForChunk(fileChannel.map(MapMode.READ_ONLY, 0, chunkSizeForFile));
//                    tail = computeHashForChunk(fileChannel.map(MapMode.READ_ONLY, Math.max(size - HASH_CHUNK_SIZE, 0), chunkSizeForFile));

                    //Alternate way to calculate tail hash for files over 4GB.
                    ByteBuffer bb = ByteBuffer.allocateDirect((int)chunkSizeForFile);
                    int read = 0;
                    long position = Math.max(size - HASH_CHUNK_SIZE, 0);
                    while ((read = fileChannel.read(bb, position)) > 0) {
                        position += read;
                    }
                    bb.flip();
                    tail = computeHashForChunk(bb);

                    return String.format("%016x", size + head + tail);
                } catch (FileNotFoundException e1) {
                    e1.printStackTrace();
                    return null;
                }
                catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }finally {
                    closeSilently(fileChannel);
                    closeSilently(fis);
                }
        }
        

    public static String computeHash(Uri uri, long length) throws Exception {
        FileEditor editor = FileEditorFactory.getFileEditorForUrl(uri,null);
        InputStream stream;
        int chunkSizeForFile = (int) Math.min(HASH_CHUNK_SIZE, length);
        // buffer that will contain the head and the tail chunk, chunks will overlap if length is smaller than two chunks
        byte[] chunkBytes = new byte[(int) Math.min(2 * HASH_CHUNK_SIZE, length)];
        // first chunk
        stream = editor.getInputStream();
        DataInputStream in = new DataInputStream(stream);
        in.readFully(chunkBytes, 0, chunkSizeForFile);
        long tailChunkPosition = length - chunkSizeForFile;
        // seek to position of the tail chunk, or not at all if length is smaller than two chunks
//                while (position < tailChunkPosition && (position += in.skip(tailChunkPosition - position)) >= 0);
        if (length >= 2*chunkSizeForFile) {
            in.close();
            stream.close();
            stream = editor.getInputStream(tailChunkPosition);
            in = new DataInputStream(stream);   
        }
        // second chunk, or the rest of the data if length is smaller than two chunks
        in.readFully(chunkBytes, chunkSizeForFile, chunkBytes.length - chunkSizeForFile);
        long head = computeHashForChunk(ByteBuffer.wrap(chunkBytes, 0, chunkSizeForFile));
        long tail = computeHashForChunk(ByteBuffer.wrap(chunkBytes, chunkBytes.length - chunkSizeForFile, chunkSizeForFile));
        stream.close();
        return String.format("%016x", length + head + tail);
    }
        public static String computeHash(HttpURLConnection urlConnection, long length) throws IOException {

            InputStream stream = urlConnection.getInputStream();
                int chunkSizeForFile = (int) Math.min(HASH_CHUNK_SIZE, length);

                // buffer that will contain the head and the tail chunk, chunks will overlap if length is smaller than two chunks
                byte[] chunkBytes = new byte[(int) Math.min(2 * HASH_CHUNK_SIZE, length)];

                DataInputStream in = new DataInputStream(stream);

                // first chunk
                in.readFully(chunkBytes, 0, chunkSizeForFile);

                long tailChunkPosition = length - chunkSizeForFile;

                // seek to position of the tail chunk, or not at all if length is smaller than two chunks
                if (length >= 2*chunkSizeForFile){
                    in.close();
                    URL url = urlConnection.getURL();
                    urlConnection.disconnect();
                    urlConnection = (HttpURLConnection) url.openConnection();
                    urlConnection.setRequestProperty("Range", "bytes="+tailChunkPosition+"-");
                    urlConnection.connect();
                    stream = urlConnection.getInputStream();
                    in = new DataInputStream(stream);
                }
                // second chunk, or the rest of the data if length is smaller than two chunks
                in.readFully(chunkBytes, chunkSizeForFile, chunkBytes.length - chunkSizeForFile);
                long head = computeHashForChunk(ByteBuffer.wrap(chunkBytes, 0, chunkSizeForFile));
                long tail = computeHashForChunk(ByteBuffer.wrap(chunkBytes, chunkBytes.length - chunkSizeForFile, chunkSizeForFile));
                return String.format("%016x", length + head + tail);
        }

        private static long computeHashForChunk(ByteBuffer buffer) {
                
                LongBuffer longBuffer = buffer.order(ByteOrder.LITTLE_ENDIAN).asLongBuffer();
                long hash = 0;
                while (longBuffer.hasRemaining()) {
                        hash += longBuffer.get();
                }
                
                return hash;
        }
        
        static void closeSilently(Closeable closeme) {
           if (closeme == null) return;
           try {
              closeme.close();
           } catch (IOException e) {
              // silence
           }
        }
}