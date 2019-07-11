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


import org.fourthline.cling.model.meta.Device;

/**
 * Created by vapillon on 10/04/15.
 */
public class UpnpServer extends NetworkSource {

    final Device mClingDevice;

    public UpnpServer(Device device, String name) {
        super(name);
        mClingDevice = device;
    }

    public String getModelName() {
        if ((mClingDevice.getDetails()!=null) && (mClingDevice.getDetails().getModelDetails()!=null)) {
            return mClingDevice.getDetails().getModelDetails().getModelName();
        } else {
            return null;
        }
    }

    /**
     * @return the object needed to start the browsing with UpnpListingEngine
     */
    public Device getClingDevice() {
        return mClingDevice;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof UpnpServer) {
            UpnpServer other = (UpnpServer)o;
            return (this.mClingDevice.hashCode() == other.mClingDevice.hashCode() &&
                    this.mName.equals(other.mName));
        }
        return false;
    }
}
