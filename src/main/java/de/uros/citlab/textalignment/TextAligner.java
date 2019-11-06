/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.uros.citlab.textalignment;


import de.uros.citlab.confmat.CharMap;
import de.uros.citlab.confmat.ConfMat;
import de.uros.citlab.errorrate.types.PathCalculatorGraph;
import de.uros.citlab.errorrate.util.GroupUtil;
import de.uros.citlab.textalignment.costcalculator.*;
import de.uros.citlab.textalignment.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author gundram
 */
public class TextAligner {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(TextAligner.class.getName());
    private final PathCalculatorGraph<ConfMatVector, NormalizedCharacter> impl;
//    LinkedList<NormalizedCharacter> refs;

    private String lbChars;
    //    List<PathCalculatorGraph.IDistance<ConfMatVector, NormalizedCharacter>> bestPath;
//    PathCalculatorGraph.DistanceMat<ConfMatVector, NormalizedCharacter> distMat;
    private Double costSkipWords;
    private Double costSkipConfMat;
    private Double costJumpConfMat;
    private Double costAnyChar = null;
    private int borderSize = 3;
    private double nacOffset = 0;
    private int maxVertexCount = -1;
    private String cert = "DFT";
    private boolean calcDist = false;
    HyphenationProperty hp = null;
    //    ConfMatCollection cmc = null;
    private double threshold = 0.01;

    public void setBorderSize(int borderSize) {
        this.borderSize = borderSize;
    }

    public void setCert(String cert) {
        this.cert = cert;
    }

    public void setCostAnyChar(Double costAnyChar) {
        this.costAnyChar = costAnyChar;
    }

    public void setCalcDist(boolean calcDist) {
        this.calcDist = calcDist;
    }

    public void setFilterOffset(double offset) {
        if (costJumpConfMat != null) {
            if (offset < 0.0) {
                return;
            } else {
                throw new RuntimeException("setMaxPathes together with jumpConfmat not implemented");
            }
        }
        impl.setFilter(offset < 0 ? null : new FilterOffset(offset));
    }

//    public void setMaxPathes(double offset, Mode mode) {
//        impl.setFilter(offset > 0 ? new PathFilterOffsetDefault(offset, mode) : null);
//    }

    public void setHp(HyphenationProperty hp) {
        this.hp = hp;
        init();
    }

    public void setUpdateScheme(PathCalculatorGraph.UpdateScheme updateScheme) {
        impl.setUpdateScheme(updateScheme);
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    private void init() {
        impl.resetCostCalculators();
        impl.addCostCalculator(new CostCalculatorCharChar(2));
        impl.addCostCalculator(new CostCalculatorCharNac(1));
        impl.addCostCalculator(new CostCalculatorNacChar(1));
        impl.addCostCalculator(new CostCalculatorContinue());
        if (costJumpConfMat != null) {
            impl.addCostCalculator(new CostCalculatorJumpConfMat(costJumpConfMat));
            impl.setFilter(new FilterJumpConfMat());
        }
        if (costSkipWords != null) {
            impl.addCostCalculator(new CostCalculatorSkipWord(costSkipWords));
        }
        impl.addCostCalculator(new CostCalculatorSkipConfMat(costSkipConfMat != null ? costSkipConfMat : Double.POSITIVE_INFINITY));
        if (hp != null) {
            int cntSkipHyphens = 2;//for the Return-Character
            boolean addCostCalculatorSkipChar = false;
            if (hp.prefixes != null && hp.prefixes.length > 0) {
                cntSkipHyphens += 2;//for the prefix
                if (hp.skipPrefix) {
                    addCostCalculatorSkipChar = true;
                }
            }
            if (hp.suffixes != null && hp.suffixes.length > 0) {
                cntSkipHyphens += 2;//for the suffix
                if (hp.skipSuffix) {
                    addCostCalculatorSkipChar = true;
                }
            }
            impl.addCostCalculator(new CostCalculatorSkipHyphenation(1 + cntSkipHyphens, 1));
            if (addCostCalculatorSkipChar) {
                //skip one character, which is 1 NaC and 1 Char
                impl.addCostCalculator(new CostCalculatorSkipChar(1 + 2, 1));
            }
        }
    }

    public void setNacOffset(double nacOffset) {
        this.nacOffset = nacOffset;
    }

    public void setMaxVertexCount(int maxVertexCount) {
        this.maxVertexCount = maxVertexCount;
    }

    public TextAligner(String lineBreakCharacters, Double costSkipWords, Double costSkipConfMat, Double costJumpConfMat, double threshold) {
        this(lineBreakCharacters, costSkipWords, costSkipConfMat, costJumpConfMat);
        setThreshold(threshold);
    }

    public TextAligner(String lineBreakCharacters, Double costSkipWords, Double costSkipConfMat, Double costJumpConfMat) {
        this.lbChars = lineBreakCharacters;
        this.impl = new PathCalculatorGraph<>();
        this.costSkipWords = costSkipWords;
        this.costSkipConfMat = costSkipConfMat;
        this.costJumpConfMat = costJumpConfMat;
        init();
//        setHyphenSuffix(new Character[]{'-', '¬', '=', ':'});
//        setHyphenPrefix(new Character[0]);
    }

    private List<NormalizedCharacter> getReference(List<String> references, CharMap charMap) {
        if (charMap == null) {
            throw new RuntimeException("apply setRecognition(..) first");
        }
        int indexNaC = charMap.get(CharMap.NaC);
        char returnSymbol = CharMap.Return;
        int indexReturn = charMap.get(returnSymbol);
        NormalizedCharacter ncNaC = new NormalizedCharacter(CharMap.NaC, new char[]{CharMap.NaC}, new int[]{indexNaC}, false, NormalizedCharacter.Type.Dft, false);
        NormalizedCharacter ncNaCIsHyphen = new NormalizedCharacter(CharMap.NaC, new char[]{CharMap.NaC}, new int[]{indexNaC}, true, NormalizedCharacter.Type.Dft, false);
        NormalizedCharacter ncLineBreak = new NormalizedCharacter('\n', new char[]{'\n'}, new int[]{indexReturn}, false, NormalizedCharacter.Type.Return, false);
        NormalizedCharacter ncRet = hp != null ? new NormalizedCharacter(returnSymbol, new char[]{returnSymbol}, new int[]{indexReturn}, true, hp.hypCosts, false) : null;
        NormalizedCharacter prefix = hp != null ? new NormalizedCharacter(hp.prefixes != null && hp.prefixes.length > 0 ? hp.prefixes[0] : '¬', hp.prefixes, getIndexes(charMap, hp.prefixes), true, NormalizedCharacter.Type.Dft, hp.skipPrefix) : null;
        NormalizedCharacter suffix = hp != null ? new NormalizedCharacter(hp.suffixes != null && hp.suffixes.length > 0 ? hp.suffixes[0] : '¬', hp.suffixes, getIndexes(charMap, hp.suffixes), true, NormalizedCharacter.Type.Dft, hp.skipSuffix) : null;
        List<NormalizedCharacter> refs = new LinkedList<>();
        refs.add(ncLineBreak);//first sign in ConfMatCollection will be a return - so it will match the linebreak
        for (String line : references) {
            if (!line.trim().equals(line)) {
                LOG.warn("line '{}' has spaces at the beginning or end. The line will be trimmed.", line);
                line = line.trim();
            }
            List<String> hypenParts = hp == null ? Arrays.asList(line) : Hyphenator.getInstance(hp.pattern).hyphenate(line);
            for (int k = 0; k < hypenParts.size(); k++) {
                char[] ref = hypenParts.get(k).toCharArray();
                for (int i = 0; i < ref.length; i++) {
                    char c = ref[i];
                    refs.add(ncNaC);
                    refs.add(new NormalizedCharacter(c,
                            charMap.get(c) == null ? null : new char[]{c},
                            getIndexes(charMap, new char[]{c}),
                            false,
                            lbChars.indexOf(c) >= 0 ? NormalizedCharacter.Type.SpaceLineBreak : NormalizedCharacter.Type.Dft,
                            false));
                }
                //add optional hyphenation
                if (k < hypenParts.size() - 1) {
                    if (hp.suffixes != null && hp.suffixes.length > 0) {
                        refs.add(ncNaCIsHyphen);
                        refs.add(suffix);
                    }
                    refs.add(ncNaCIsHyphen);
                    refs.add(ncRet);
                    if (hp.prefixes != null && hp.prefixes.length > 0) {
                        refs.add(ncNaCIsHyphen);
                        refs.add(prefix);
                    }
                }
            }
            refs.add(ncNaC);
            refs.add(ncLineBreak);//last sign in ConfMatCollection will be a return - so it will match the linebreak
        }
        return refs;
    }

    private static int[] getIndexes(CharMap cm, char[] chars) {
        if (chars == null) {
            return null;
        }
        List<Integer> res = new LinkedList<>();
        for (char aChar : chars) {
            Integer key = cm.get(aChar);
            if (key != null) {
                res.add(key);
            }
        }
        if (res.isEmpty()) {
            LOG.debug("character '" + String.valueOf(chars) + "' is not in CharMap - use anyChar fallback");
            return null;
//            throw new RuntimeException("cannot find any channel for chars '" + String.copyValueOf(chars) + "'");
        }
        int[] res1 = new int[res.size()];
        for (int i = 0; i < res.size(); i++) {
            res1[i] = res.get(i);
        }
        return res1;
    }

    public void setDynMatViewer(PathCalculatorGraph.DynMatViewer dynMatViewer) {
        impl.setDynMatViewer(dynMatViewer);
    }

    public List<LineMatch> getAlignmentResult(List<String> refs, List<ConfMat> recos) {
        ConfMatCollection cmc = ConfMatCollection.newInstance(
                1.0,
                recos,
                nacOffset);

        CharMap charMap = cmc.getCharMap();
//        double[][] recoVecs = cmc.getLLHMat().copyTo(null);
        double[][] recoVecs = cmc.getMatrix();
        List<ConfMatVector> recosNormalized = new ArrayList<>(recoVecs.length);
        for (int i = 0; i < recoVecs.length; i++) {
            double[] recoVec = recoVecs[i];
            recosNormalized.add(new ConfMatVector(recoVec, charMap, lbChars, costAnyChar == null ? Double.POSITIVE_INFINITY : costAnyChar, calcDist));
        }
        List<NormalizedCharacter> refsNormalized = getReference(refs, charMap);
        List<PathCalculatorGraph.IDistance<ConfMatVector, NormalizedCharacter>> bestPath = impl.calcBestPath(recosNormalized, refsNormalized);
        if (bestPath == null) {
            return null;
        }
        if (LOG.isDebugEnabled()) {
            for (PathCalculatorGraph.IDistance<ConfMatVector, NormalizedCharacter> distance : bestPath) {
                LOG.debug(distance.toString());
            }
        }
        List<BestPathPart> grouping = GroupUtil.getGrouping(bestPath,
                new GroupUtil.Joiner<PathCalculatorGraph.IDistance<ConfMatVector, NormalizedCharacter>>() {

                    @Override
                    public boolean isGroup(
                            List<PathCalculatorGraph.IDistance<ConfMatVector, NormalizedCharacter>> list,
                            PathCalculatorGraph.IDistance<ConfMatVector, NormalizedCharacter> element) {
                        return keepElement(element);
                    }

                    @Override
                    public boolean keepElement(PathCalculatorGraph.IDistance<ConfMatVector, NormalizedCharacter> element) {
                        if (element.getManipulation().startsWith("SKIP_")) {
                            return false;
                        }
                        ConfMatVector[] recos = element.getRecos();
                        if (recos == null || recos.length == 0) {
                            return false;
                        }
//                        int returnIndex = charMap.get(cmc.getReturnSymbol());
                        for (ConfMatVector cmVec : recos) {
                            if (cmVec.isReturn) {
                                if (recos.length != 1) {
                                    throw new RuntimeException("unexpected length");
                                }
                                return false;
                            }
                        }
                        return true;
                    }
                },
                new GroupUtil.Mapper<PathCalculatorGraph.IDistance<ConfMatVector, NormalizedCharacter>, BestPathPart>() {
                    @Override
                    public BestPathPart map(List<PathCalculatorGraph.IDistance<ConfMatVector, NormalizedCharacter>> elements) {
                        BestPathPart bestPathPart = BestPathPart.newInstance(elements, borderSize);
                        if (bestPathPart.reference.indexOf(CharMap.Return) >= 0) {
                            LOG.warn("in reference '{}' is still the character '{}' - delete this character.", bestPathPart.reference, CharMap.Return);
                            bestPathPart.reference = bestPathPart.reference.replace("" + CharMap.Return, "");
                        }
                        return bestPathPart;
                    }
                });
        HashMap<ConfMat, LineMatch> refMap = new LinkedHashMap<>();
        HashMap<ConfMat, LineMatch> refMapSecond = new LinkedHashMap<>();
        HashSet<ConfMat> badConfMats = new LinkedHashSet<>();
        for (BestPathPart cmp : grouping) {
            ConfMat cmOld = cmc.getOrigConfMat(cmp.startReco);
            double cost = 0;
            switch (cert) {
                case "MAX":
                    cost = Math.max(cmp.cost, Math.max(cmp.costEnd, cmp.costStart));
                    break;
                case "DFT":
                    cost = cmp.cost;
                    break;
                case "SOFTMAX":
                    cost = cmp.cost + cmp.costEnd + cmp.costStart;
                    break;
                default:
                    throw new RuntimeException("not a proper confidence calculation '" + cert + "'.");
            }
            double conf = Math.exp(-cost);
//            if (conf > threshold) {
            //found match!
            List<LineMatch> matches = new LinkedList<>();
            LineMatch candidate = new LineMatch(cmOld, cmp.reference, conf);
            matches.add(candidate);
            if (refMap.containsKey(cmOld)) {
                if (!candidate.getReference().equals(refMap.get(cmOld))) {
                    matches.add(refMap.get(cmOld));
                }
                if (refMapSecond.containsKey(cmOld) && !candidate.getReference().equals(refMapSecond.get(cmOld)) && !refMap.get(cmOld).getReference().equals(refMapSecond.get(cmOld))) {
                    matches.add(refMapSecond.get(cmOld));
                }
            }
            matches.sort((o1, o2) -> Double.compare(o2.getConfidence(), o1.getConfidence()));
            if (matches.size() > 1) {
                LOG.warn("found {} matches for one baseline with confidences {} and {}", matches.size(), matches.get(0).getConfidence(), matches.get(1).getConfidence());
            }
            refMap.put(cmOld, matches.get(0));
            if (matches.size() > 1) {
                refMapSecond.put(cmOld, matches.get(1));
            }
//            }
        }
        if (costJumpConfMat != null && !costJumpConfMat.isInfinite()) {
            boolean isFinal = false;
            //define subproblem
            List<ConfMat> confmatsSub = new LinkedList<>();
            for (ConfMat reco : recos) {
                if (!refMap.containsKey(reco)) {
                    confmatsSub.add(reco);
                }
            }
            //otherwise, the result was already perfect - all confmats are used or for no confmat a match is found
            if (!confmatsSub.isEmpty() && confmatsSub.size() != recos.size()) {

                List<String> refSub = new LinkedList<>(refs);
                List<LineMatch> values = new LinkedList<>(refMap.values());
                values.sort(new Comparator<LineMatch>() {
                    @Override
                    public int compare(LineMatch o1, LineMatch o2) {
                        return Integer.compare(o2.getReference().length(), o1.getReference().length());
                    }
                });
                for (LineMatch value : values) {
                    String reference = value.getReference();
                    boolean found = false;
                    for (int i = 0; i < refSub.size(); i++) {
                        String s = refSub.get(i).trim();
                        if (s.contains(reference)) {
                            int i1 = s.indexOf(reference, 0);
                            String s1 = s.substring(0, i1).trim();
                            String s2 = s.substring(i1 + reference.length(), s.length()).trim();
                            refSub.set(i, s1);
                            if (!s2.isEmpty()) {
                                refSub.add(i + 1, s2);
                            }
                            if (s1.isEmpty()) {
                                refSub.remove(i);
                            }
//                            String s2 = (s.substring(0, i1) + s.substring(i1 + reference.length(), s.length())).replaceAll("\\s+", "" + lbChars.charAt(0)).trim();
//                            refSub.set(i, s2);
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        LOG.warn("sequence '{}' is not deleted because it is not found as raw string", reference);
                    }
                }
                LOG.info("reduce CMs from {} to {} and references from {} to {}", recos.size(), confmatsSub.size(), cntChars(refs), cntChars(refSub));
                List<LineMatch> alignmentResult = getAlignmentResult(refSub, confmatsSub);
                for (LineMatch lineMatch : alignmentResult) {
                    if (lineMatch != null && lineMatch.getCm() != null) {
                        refMap.put(lineMatch.getCm(), lineMatch);
                    }
                }
            }


        }
        List<LineMatch> res = new LinkedList<>();
//        double costThresh = -Math.log(threshold);
        for (int i = 0; i < recos.size(); i++) {
            LineMatch m1 = refMap.get(recos.get(i));
            LineMatch m2 = refMapSecond.get(recos.get(i));
            if (m1 != null) {
                double alternativeConf = 0;
                if (m2 != null) {
                    alternativeConf = m2.getConfidence();
//                double cost1 = -Math.log(m1.getConfidence());
//                double cost2 = -Math.log(m2.getConfidence());
//                double costboth = cost1+(cost2-costThresh)
                }
                if (costSkipWords != null) {
                    alternativeConf = Math.max(alternativeConf, Math.exp(-m1.getReference().length() * costSkipWords));
                }
                m1.setConfidence(Math.max(0, m1.getConfidence() - alternativeConf));//\TODO: or also -threshold??
                if (m1.getConfidence() > threshold) {
//                    ConfMat cm = m1.getCm();
//                    double[][] matrix = cm.getMatrix();
//                    double sum = 0;
//                    for (double[] doubles : matrix) {
//                        sum += doubles[0];
//                    }
//                    sum /= cm.toString();
//                    m1.setConfidence(Math.max(0, m1.getConfidence() + Math.log(-sum)));
                    res.add(m1);
                } else {
                    res.add(null);
                }
            } else {
                res.add(null);
            }
//            System.out.println(res.get(i)==null?null:res.get(i).getReference());
//            System.out.println(recos.get(i).toString());
        }
//        System.out.println(res.size()+" vs. "+recos.size());
        return res;
    }

    private int cntChars(List<String> list) {
        int cnt = 0;
        for (String s : list) {
            cnt += s.length();
        }
        return cnt;
    }

//    private void setRecognition(List<ConfMat> confMats) {
//        cmc = ConfMatCollection.newInstance(
//                1.0,
//                confMats,
//                nacOffset);
//
////        double[][] recoVecs = cmc.getLLHMat().copyTo(null);
//        double[][] recoVecs = cmc.getMatrix();
//        recos = new ArrayList<>(recoVecs.length);
//        for (int i = 0; i < recoVecs.length; i++) {
//            double[] recoVec = recoVecs[i];
//            recos.add(new ConfMatVector(recoVec, charMap, lbChars, costAnyChar == null ? Double.POSITIVE_INFINITY : costAnyChar, calcDist));
//        }
//    }

}