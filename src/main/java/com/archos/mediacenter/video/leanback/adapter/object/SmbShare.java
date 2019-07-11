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

package com.archos.mediacenter.video.leanback.adapter.object;

import com.archos.filecorelibrary.samba.Share;

/**
 * Created by vapillon on 10/04/15.
 */
public class SmbShare extends NetworkSource {

    final Share mShare;

    public SmbShare(Share share) {
        super(share.getName());
        mShare = share;
    }

    public String getDisplayName() {
        return mShare.getDisplayName();
    }

    public String getWorkgroup() {
        return mShare.getWorkgroup();
    }

    public String getAddress() {
        return mShare.getAddress();
    }

    public Share getFileCoreShare() {
        return mShare;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof SmbShare) {
            SmbShare other = (SmbShare)o;

            // Here we used to use the equals() method of Share.
            // But in fact we must only compare the IP, not the Name or the Workgroup, because Name and Workgroup may change
            // (TCP discovery returns IP only, need to update it with name once we get result from UDP discovery)
            if (this.mShare.getAddress()!=null) {
                return this.mShare.getAddress().equals(other.mShare.getAddress());
            }
        }
        return false;
    }
}
