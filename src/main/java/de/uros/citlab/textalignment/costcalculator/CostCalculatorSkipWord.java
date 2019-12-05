package de.uros.citlab.textalignment.costcalculator;

import de.uros.citlab.errorrate.types.PathCalculatorGraph;
import de.uros.citlab.textalignment.types.ConfMatVector;
import de.uros.citlab.textalignment.types.NormalizedCharacter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;

public class CostCalculatorSkipWord implements PathCalculatorGraph.ICostCalculatorMulti<ConfMatVector, NormalizedCharacter> {
    private static Logger LOG = LoggerFactory.getLogger(CostCalculatorSkipWord.class);
    private boolean[] isSkipPoint;
    //    private boolean[] isSkipPointHard;
    private boolean[] isHyphenSkipPoint;
    private int[] sumCharacter;
    private boolean[] isReturnPoint;
    private ConfMatVector[] recos;
    private NormalizedCharacter[] refs;
    boolean cntHyphenSuffix;
    boolean cntHyphenPrefix;
    //    List<Integer> skipPoints;
//    List<Integer> returnPoints;
    private final double charCosts;

    public CostCalculatorSkipWord(double charCosts, boolean cntHyphenSuffix, boolean cntHyphenPrefix) {
        this.charCosts = charCosts;
        this.cntHyphenPrefix = cntHyphenPrefix;
        this.cntHyphenSuffix = cntHyphenSuffix;
    }

//    public CostCalculatorSkipWord(double charCosts) {
//        this.charCosts = charCosts;
//    }

    @Override
    public void init(PathCalculatorGraph.DistanceMat<ConfMatVector, NormalizedCharacter> dm, ConfMatVector[] recos, NormalizedCharacter[] refs) {
        this.recos = recos;
        this.refs = refs;
//        super.init(dm, recos, refs);
        isSkipPoint = new boolean[refs.length];
//        isSkipPointHard = new boolean[refs.length];
        isHyphenSkipPoint = new boolean[refs.length];
        sumCharacter = new int[refs.length];
        for (int j = 1; j < refs.length; j++) {
            NormalizedCharacter.Type type = refs[j].type;
            isSkipPoint[j] = type.equals(NormalizedCharacter.Type.SpaceLineBreak) || type.equals(NormalizedCharacter.Type.ReturnLineBreak);
//            isSkipPointHard[j] = type.equals(NormalizedCharacter.Type.ReturnLineBreak) || type.equals(NormalizedCharacter.Type.HyphenLineBreak);
            isHyphenSkipPoint[j] = type.equals(NormalizedCharacter.Type.HyphenLineBreak);
            sumCharacter[j] = sumCharacter[j - 1] + (!refs[j].isNaC && !refs[j].isHyphen ? 1 : 0);
        }
        isReturnPoint = new boolean[recos.length];
        for (int i = 1; i < recos.length; i++) {
            isReturnPoint[i] = recos[i].isReturn;
        }
    }

    private int nextSpaceNoHyphen(int idxStart) {
        while (idxStart < isSkipPoint.length) {
            if (isSkipPoint[idxStart]) {
                break;
            }
            idxStart++;
        }
        return idxStart;
    }

    @Override
    public List<PathCalculatorGraph.DistanceSmall> getNeighboursSmall(int[] point, PathCalculatorGraph.DistanceSmall distanceSmall) {
        int recoIdx = point[0];
        if (!isReturnPoint[recoIdx]) {
            //nothing to do - actual position is no skip-character
            return null;
        }
        int refIdx = point[1];
        if (!isSkipPoint[refIdx] && !isHyphenSkipPoint[refIdx]) {
            //nothing to do - actual character is no skip-character
            return null;
        }

        //only make one connection to next hard skip point
        int end = nextSpaceNoHyphen(refIdx + 1);
        if (end >= isSkipPoint.length) {
            return null;
        }
        List<PathCalculatorGraph.DistanceSmall> res = new LinkedList<>();
        int offsetHyphen = isHyphenSkipPoint[refIdx] && cntHyphenPrefix ? 1 : 0;
        {
            double costs = (sumCharacter[end] - sumCharacter[refIdx] + offsetHyphen) * charCosts;
            res.add(new PathCalculatorGraph.DistanceSmall(point, new int[]{recoIdx, end}, distanceSmall.costsAcc + costs, this));
        }
        int idxRun = refIdx + 1;
        while (idxRun < end) {
            if (isHyphenSkipPoint[idxRun]) {
                int offsetHyphen2 = isHyphenSkipPoint[idxRun] && cntHyphenSuffix ? 1 : 0;
                double costs = (sumCharacter[idxRun] - sumCharacter[refIdx] + offsetHyphen + offsetHyphen2) * charCosts;
                res.add(new PathCalculatorGraph.DistanceSmall(point, new int[]{recoIdx, idxRun}, distanceSmall.costsAcc + costs, this));
            }
            idxRun++;
        }
        return res;
    }

    @Override
    public PathCalculatorGraph.IDistance<ConfMatVector, NormalizedCharacter> getNeighbour(PathCalculatorGraph.DistanceSmall distanceSmall) {
        int refIdx = distanceSmall.pointPrevious[1];
        int refIdx2 = distanceSmall.point[1];
        //only make one connection to next hard skip point
        int offsetHyphen = isHyphenSkipPoint[refIdx] && cntHyphenPrefix ? 1 : 0;
        int offsetHyphen2 = isHyphenSkipPoint[refIdx2] && cntHyphenSuffix ? 1 : 0;
        double costs = (sumCharacter[refIdx2] - sumCharacter[refIdx] + offsetHyphen + offsetHyphen2) * charCosts;
        NormalizedCharacter[] skips = new NormalizedCharacter[refIdx2 - refIdx];
        for (int i = 0; i < skips.length; i++) {
            NormalizedCharacter normedChar = refs[refIdx + i + 1];
            skips[i] = normedChar;
        }
        return new PathCalculatorGraph.Distance<>(distanceSmall, "SKIP_WORD", costs, null, skips);
    }

}
