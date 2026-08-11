// Copyright 2026 Archos SA
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class FidelityTransformationTest {
    @Test
    public void constructorRejectsNonPositiveTargetDimensions() {
        assertThrows(IllegalArgumentException.class, () -> new FidelityTransformation(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new FidelityTransformation(1, 0));
        assertThrows(IllegalArgumentException.class, () -> new FidelityTransformation(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> new FidelityTransformation(1, -1));
    }

    @Test
    public void cropWidthIsClampedWhenItRoundsBelowOnePixel() {
        assertEquals(1, FidelityTransformation.boundedCropDimension(0.5f, 100));
    }

    @Test
    public void cropHeightIsClampedWhenItRoundsBelowOnePixel() {
        assertEquals(1, FidelityTransformation.boundedCropDimension(0.49f, 100));
    }

    @Test
    public void cropDimensionNeverExceedsSource() {
        assertEquals(100, FidelityTransformation.boundedCropDimension(150.0f, 100));
    }
}
