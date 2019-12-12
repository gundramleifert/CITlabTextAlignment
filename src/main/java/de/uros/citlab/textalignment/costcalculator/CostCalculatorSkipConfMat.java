package de.uros.citlab.textalignment.costcalculator;

import de.uros.citlab.errorrate.types.PathCalculatorGraph;
import de.uros.citlab.textalignment.types.ConfMatVector;
import de.uros.citlab.textalignment.types.NormalizedCharacter;

import java.util.LinkedList;
import java.util.List;

public class CostCalculatorSkipConfMat extends CostCalculatorAbstract {

    private boolean[] isSkipPoint;
    private boolean[] isReturnPoint;
    List<Integer> returnPoints;
    private final double factorLength;
//    private final double factorDeleteChar;

    public CostCalculatorSkipConfMat(double factorLength) {
        this.factorLength = factorLength;
//        this.factorDeleteChar = factorDeleteChar;
    }

    @Override
    public void init(PathCalculatorGraph.DistanceMat<ConfMatVector, NormalizedCharacter> dm, ConfMatVector[] recos, NormalizedCharacter[] refs) {
        super.init(dm, recos, refs);
        isSkipPoint = new boolean[refs.length];
        for (int j = 1; j < refs.length; j++) {
            isSkipPoint[j] = refs[j].isSkipPointSoft();
        }
        returnPoints = new LinkedList<>();
        isReturnPoint = new boolean[recos.length];
        for (int i = 1; i < recos.length; i++) {
            ConfMatVector confmatVec = recos[i];
            if (confmatVec.isReturn) {
                returnPoints.add(i);
                isReturnPoint[i] = true;
            }
        }
    }

    @Override
    public PathCalculatorGraph.IDistance<ConfMatVector, NormalizedCharacter> getNeighbour(PathCalculatorGraph.DistanceSmall distanceSmall) {
        final int recoIdx2 = distanceSmall.point[0];
        final int recoIdx = distanceSmall.pointPrevious[0];
        ConfMatVector[] skips = new ConfMatVector[recoIdx2 - recoIdx];
        double cost = 0;
        for (int i = 0; i < skips.length; i++) {
            skips[i] = recos[recoIdx + i + 1];
            if (i != skips.length - 1) {
                cost += skips[i].costNaC;
            }
        }
//        if (factorDeleteChar != 0.0) {
        cost = Math.min(cost, factorLength * (skips.length - 1));
//        }
        return new PathCalculatorGraph.Distance(distanceSmall, "SKIP_CONFMAT", cost, skips, null);

    }

    @Override
    public PathCalculatorGraph.DistanceSmall getNeighbourSmall(int[] point, PathCalculatorGraph.DistanceSmall distanceSmall) {
        final int refIdx = point[1];
        final int recoIdx = point[0];
        if (!isReturnPoint[recoIdx]) {
            return null;
        }
        //actual character is skip-character. Do something!
        for (Integer returnPoint : returnPoints) {
            if (returnPoint <= recoIdx) {
                continue;//nothing to do - only make connections to skip-pints right of actual position
            }
//            ConfMatVector[] skips = new ConfMatVector[returnPoint - recoIdx];
//             cost = factorLength * skips.length;
            double cost = 0;
            for (int i = recoIdx + 1; i < returnPoint; i++) {
//                skips[i] = this.recos[recoIdx + i + 1];
                cost += this.recos[i].costNaC;
            }
//            if (factorDeleteChar != 0.0) {
//            double costDeleteChar = 0;
//            for (ConfMatVector skip : skips) {
//                cost += skip.costNaC;
//            }
            cost = Math.min(cost, factorLength * (returnPoint - recoIdx - 1));
//            System.out.println("for x = " + refIdx + " calc path from Y " + recoIdx + " to " + returnPoint + " with costs " + cost);
//            }
            return new PathCalculatorGraph.DistanceSmall(point, new int[]{returnPoint, refIdx}, distanceSmall.costsAcc + cost, this);
        }
        return null;
    }
}

