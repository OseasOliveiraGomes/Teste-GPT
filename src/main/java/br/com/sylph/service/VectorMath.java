package br.com.sylph.service;

public final class VectorMath {
    private VectorMath() { }
    public static double cosine(double[] a, double[] b) {
        if (!valid(a) || !valid(b) || a.length != b.length) return -1;
        // Scaling prevents overflow/underflow with finite but very large/small vectors.
        double scaleA = 0, scaleB = 0;
        for (int i = 0; i < a.length; i++) {
            scaleA = Math.max(scaleA, Math.abs(a[i]));
            scaleB = Math.max(scaleB, Math.abs(b[i]));
        }
        if (scaleA == 0 || scaleB == 0) return -1;
        double ab = 0, aa = 0, bb = 0;
        for (int i = 0; i < a.length; i++) {
            double av = a[i] / scaleA, bv = b[i] / scaleB;
            ab += av * bv; aa += av * av; bb += bv * bv;
        }
        return Math.max(-1, Math.min(1, ab / (Math.sqrt(aa) * Math.sqrt(bb))));
    }

    public static boolean valid(double[] vector) {
        if (vector == null || vector.length == 0) return false;
        boolean nonzero = false;
        for (double value : vector) {
            if (!Double.isFinite(value)) return false;
            nonzero |= value != 0;
        }
        return nonzero;
    }
}
