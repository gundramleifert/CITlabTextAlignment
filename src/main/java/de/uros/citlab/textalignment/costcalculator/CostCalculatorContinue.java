package de.uros.citlab.textalignment.costcalculator;

import de.uros.citlab.errorrate.types.PathCalculatorGraph;
import de.uros.citlab.textalignment.types.ConfMatVector;
import de.uros.citlab.textalignment.types.NormalizedCharacter;

public class CostCalculatorContinue extends CostCalculatorAbstract {

    @Override
    public PathCalculatorGraph.IDistance<ConfMatVector, NormalizedCharacter> getNeighbour(PathCalculatorGraph.DistanceSmall distanceSmall) {
        final int[] ints = distanceSmall.pointPrevious;
        final int refIdx = ints[1];
        NormalizedCharacter normChar = refs[refIdx];
        if (normChar == null) {
            return null;
        }
        int recoIdx = ints[0];
        if (recoIdx > sizeReco) {
            return null;
        }
        ConfMatVector recoPart = recos[recoIdx + 1];
        Double cost = getCost(normChar, recoPart);
        if (cost == null) {
            return null;
        }
        return new PathCalculatorGraph.Distance(distanceSmall, normChar.isNaC ? "NAC_CONTINUE" : "CHAR_CONTINUE", cost, new ConfMatVector[]{recoPart}, new NormalizedCharacter[0]);
    }

    @Override
    public PathCalculatorGraph.DistanceSmall getNeighbourSmall(int[] ints, PathCalculatorGraph.DistanceSmall distanceSmall) {
        final int refIdx = ints[1];
        final int recoIdx = ints[0];
        final int recoIdx2 = recoIdx + 1;
        final NormalizedCharacter normChar = refs[refIdx];
        if (normChar == null) {
            return null;
        }
        if (recoIdx < 1 || recos[recoIdx].isReturn) {
            return null;
        }
        if (recoIdx2 >= sizeReco) {
            return null;
        }
        final ConfMatVector recoPart = recos[recoIdx2];
        if (recoPart.isReturn) {
            return null;
        }
        //TODO: early break for characters \n?
        final Double cost = getCost(normChar, recoPart);
        if (cost == null) {
            return null;
        }
        return new PathCalculatorGraph.DistanceSmall(ints, new int[]{recoIdx2, refIdx}, distanceSmall.costsAcc + cost, this);
    }

}
