package util;

public final class GeoUtil {

    // ======= Affine coefficients and image height =======
    public static double a1, b1, c1;
    public static double a2, b2, c2;
    public static double imgH;

    // ================= Public APIs =====================

    /** Pixel (x,y) -> [lat, lon] */
    public static double[] scr2geo(double x, double y) {
        y = imgH - y;                           // flip y
        double lat = a1 * x + b1 * y + c1;
        double lon = a2 * x + b2 * y + c2;
        return new double[]{lat, lon};
    }

    /** [lat, lon] -> Pixel (x,y) */
    public static double[] geo2scr(double lat, double lon) {
        double det = a1 * b2 - a2 * b1;
        double x = (c1 * b2 - c2 * b1 + b1 * lon - b2 * lat) / det;
        double y = (c1 * a2 - c2 * a1 + a1 * lat - a2 * lon) / det;
        return new double[]{x, imgH - y};
    }

    /** Calibration with N control points */
    public static void calibrate(double[] px, double[] py,
                                 double[] lat, double[] lon,
                                 double imageHeight) {

        imgH = imageHeight;
        int n = px.length;
        double[][] A = new double[n][3];
        for (int i = 0; i < n; i++) {
            py[i] = imgH - py[i];               // flip y
            A[i][0] = px[i];
            A[i][1] = py[i];
            A[i][2] = 1.0;
        }
        double[] latP = linearFitAffine(A, lat);
        double[] lonP = linearFitAffine(A, lon);

        a1 = latP[0]; b1 = latP[1]; c1 = latP[2];
        a2 = lonP[0]; b2 = lonP[1]; c2 = lonP[2];
    }

    // ================= Linear Fit =====================

    private static double[] linearFitAffine(double[][] A, double[] B) {
        int n = A.length;
        int m = 3;
        double[][] At = new double[m][n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < m; j++)
                At[j][i] = A[i][j];

        double[][] AtA = new double[m][m];
        for (int i = 0; i < m; i++)
            for (int j = 0; j < m; j++) {
                double sum = 0;
                for (int k = 0; k < n; k++) sum += At[i][k] * A[k][j];
                AtA[i][j] = sum;
            }

        double[][] inv = invertMatrix(AtA);

        double[] AtB = new double[m];
        for (int i = 0; i < m; i++) {
            double sum = 0;
            for (int k = 0; k < n; k++) sum += At[i][k] * B[k];
            AtB[i] = sum;
        }

        double[] X = new double[m];
        for (int i = 0; i < m; i++) {
            double sum = 0;
            for (int j = 0; j < m; j++) sum += inv[i][j] * AtB[j];
            X[i] = sum;
        }
        return X;
    }

    // ============ Matrix Inverse (Gauss-Jordan) ============

    private static double[][] invertMatrix(double[][] mat) {
        int n = mat.length;
        double[][] A = new double[n][n];
        double[][] I = new double[n][n];

        // init
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                A[i][j] = mat[i][j];
                I[i][j] = (i == j) ? 1.0 : 0.0;
            }

        for (int i = 0; i < n; i++) {
            double diag = A[i][i];
            for (int j = 0; j < n; j++) {
                A[i][j] /= diag;
                I[i][j] /= diag;
            }
            for (int k = 0; k < n; k++) if (k != i) {
                double factor = A[k][i];
                for (int j = 0; j < n; j++) {
                    A[k][j] -= factor * A[i][j];
                    I[k][j] -= factor * I[i][j];
                }
            }
        }
        return I;
    }
}