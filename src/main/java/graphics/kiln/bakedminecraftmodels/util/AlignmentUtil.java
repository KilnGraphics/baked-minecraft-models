package graphics.kiln.bakedminecraftmodels.util;

public class AlignmentUtil {

    // multiple must be a power of 2
    public static long alignPowerOf2(long numToRound, long multiple) {
        // TODO: make sure this always works
        //noinspection UnnecessaryParentheses
        return (numToRound + multiple - 1) & -multiple;
    }
}
