package de.harmssi.thorffix;

/**
 * Abbildung der normierten Navigationskoordinaten des Spiels (x, y in 0..1, y nach oben; ff9.w_naviGetPos)
 * auf das Kartenbild: u = a + b * x (von links), v = c - d * y (von oben), jeweils als Anteil der Bildgröße.
 */
final class MapCalibration {
    private static final float[] SCHEMATIC = { 0f, 1f, 1f, 1f };
    // Beide Kartenbilder des Spiels (1273 x 1080) haben denselben Zierrahmen; die Navigationsfläche
    // liegt bei 4,0 % .. 96,5 % (x) bzw. 3,7 % .. 96,3 % (y). Gemessen am zur Laufzeit gerenderten Bild.
    private static final float[] WORLD = { 0.040f, 0.925f, 0.963f, 0.926f };
    private static final float[] MIST = WORLD;

    static float[] forKey(String key, boolean image) {
        if (!image) return SCHEMATIC;
        return "mist".equals(key) ? MIST : WORLD;
    }

    private MapCalibration() { }
}
