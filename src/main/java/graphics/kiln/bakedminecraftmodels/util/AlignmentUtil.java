/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels.util;

public class AlignmentUtil {

    // multiple must be a power of 2
    public static long alignPowerOf2(long numToRound, long multiple) {
        // TODO: make sure this always works
        //noinspection UnnecessaryParentheses
        return (numToRound + multiple - 1) & -multiple;
    }
}
