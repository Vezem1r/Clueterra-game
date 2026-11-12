package io.clueterra.questions.config;

public record SlotDifficultyCurve(int maxTarget, int minTarget) {

    public int[] computeTargets(int totalSlots) {
        if (totalSlots <= 0) return new int[0];
        if (totalSlots == 1) return new int[]{(maxTarget + minTarget) / 2};

        int[] targets = new int[totalSlots];
        for (int i = 0; i < totalSlots; i++) {
            double progress = (double) i / (totalSlots - 1);
            double decay = 1.0 - Math.pow(progress, 1.3);
            targets[i] = (int) Math.round(minTarget + (maxTarget - minTarget) * decay);
        }
        return targets;
    }
}
