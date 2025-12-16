package edu.mines.mmsbot.util;

import java.util.List;

public final class ArrayUtils {
    public static boolean containsAny(List<?> heap, List<?> searchFor) {
        for (Object o : searchFor) {
            if (heap.contains(searchFor)) return true;
        }
        return false;
    }
}
