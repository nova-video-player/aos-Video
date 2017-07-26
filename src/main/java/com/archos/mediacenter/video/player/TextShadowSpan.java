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

package com.archos.mediacenter.video.player;

import android.graphics.Color;
import android.text.TextPaint;
import android.text.style.CharacterStyle;

public class TextShadowSpan extends CharacterStyle {

    private final float radius;
    private final float dx;
    private final float dy;
    private final int color;
    
    /*
     * Build a character style without shadow
     */
    public TextShadowSpan() {
        super();
        this.radius = 0.0f;
        this.dx = 0.0f;
        this.dy = 0.0f;
        this.color = Color.TRANSPARENT;
    }

    /*
     * Build a character style with a shadow
     */
    public TextShadowSpan(float shadowRadius, float shadowDx, float shadowDy, int shadowColor) {
        super();
        this.radius = shadowRadius;
        this.dx = shadowDx;
        this.dy = shadowDy;
        this.color = shadowColor;
    }

    @Override
    public void updateDrawState(TextPaint tp) {
        tp.setShadowLayer(radius, dx, dy, color);
    }

}
