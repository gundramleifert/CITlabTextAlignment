package de.uros.citlab.textalignment.types;

import de.uros.citlab.confmat.CharMap;
import org.apache.commons.math3.util.Pair;

public class ConfMatVector {

    public final double[] costVec;
    public final double[] vecOrig;
    public final double offset;
    public final double costAnyChar;
    public final double costNaC;
    public final double costReturn;
    //    public final double costOffset;
    public final boolean isReturn;
    //    public int maxIndex;
    public Pair<Integer, Integer> origPos;

    @Override
    public String toString() {
        return String.format("ConfMatVec{" + "cAnyChar=%5.2f, cNaC=%5.2f, isRet=%5b}", costAnyChar, costNaC, isReturn);
    }

    public ConfMatVector(double[] vectorIn, CharMap cm, String lineBreakChars, double costAnyChar, boolean toDist) {
        int maxIndex = 0;
        vecOrig = vectorIn;
        int idxNaC = cm.get(CharMap.NaC);
        int idxReturn = cm.get(CharMap.Return);
        double[] probVec = new double[vectorIn.length];
        double shift = 0;
        double max = Double.NEGATIVE_INFINITY;
        double maxChar = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < probVec.length; i++) {
            double v = vectorIn[i];
            double prob = Math.exp(v);
            probVec[i] = prob;
            shift += prob;
            if (v > max) {
                max = v;
                maxIndex = i;
            }
            //not NaC, Return, linebreak-characters
            if (v > maxChar && i != idxNaC && i != idxReturn && lineBreakChars.indexOf(cm.get(i).charAt(0)) < 0) {
                maxChar = v;
            }
        }
        this.offset = toDist ? -/*Math.log(shift);//*/max : 0;
        shift = -Math.log(shift) + offset;
        this.costVec = new double[vectorIn.length];
        for (int i = 0; i < costVec.length; i++) {
            costVec[i] = -vectorIn[i] - shift;
        }
        costNaC = costVec[idxNaC];
        costReturn = costVec[idxReturn];
        isReturn = maxIndex == idxReturn;
        this.costAnyChar = -maxChar - shift + costAnyChar;
        if (costAnyChar < 0.0) {
            throw new RuntimeException("cost of arbitrary character is negative (" + costAnyChar + ") - this is not possible");
        }
    }

}
