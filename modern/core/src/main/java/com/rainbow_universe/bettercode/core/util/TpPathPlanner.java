package com.rainbow_universe.bettercode.core.util;

import java.util.ArrayList;
import java.util.List;

public final class TpPathPlanner {
    private TpPathPlanner() {
    }

    public static List<double[]> buildAxisSteps(
        double sx,
        double sy,
        double sz,
        double tx,
        double ty,
        double tz,
        double maxStep,
        int maxSteps
    ) {
        double stepLimit = Math.max(0.001D, maxStep);
        int cap = Math.max(1, maxSteps);
        double dx = tx - sx;
        double dy = ty - sy;
        double dz = tz - sz;
        List<double[]> out = new ArrayList<double[]>();
        int safety = 0;
        for (int axis = 0; axis < 3; axis++) {
            double delta = axis == 0 ? dx : axis == 1 ? dy : dz;
            while (Math.abs(delta) >= 0.001D && safety < cap) {
                double step = Math.min(stepLimit, Math.abs(delta)) * Math.signum(delta);
                double[] move = new double[] {0.0D, 0.0D, 0.0D};
                if (axis == 0) {
                    move[0] = step;
                    dx -= step;
                    delta = dx;
                } else if (axis == 1) {
                    move[1] = step;
                    dy -= step;
                    delta = dy;
                } else {
                    move[2] = step;
                    dz -= step;
                    delta = dz;
                }
                out.add(move);
                safety++;
            }
        }
        return out;
    }
}
