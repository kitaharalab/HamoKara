import java.util.*;
import be.ac.ulg.montefiore.run.jahmm.*;

/* An extention of ViterbiCalculator in jahmm 
   Made by Tetsuro Kitahara　*/

public class CompoundHMMsViterbiCalculator {
  private double[][] delta;
  private int[][] psy, psy2nd;
  private int[] stateSequence, stateSeq2nd;
  private double lnProbability;

  public <O1 extends Observation, O2 extends Observation>
    CompoundHMMsViterbiCalculator(List<? extends O1> oseq1,
                                  Hmm<O1> hmm1, double w1,
                                  List<? extends O2> oseq2,
                                  Hmm<O2> hmm2, double w2,
                                  double[] prior) {
    if (oseq1.isEmpty() || oseq2.isEmpty()) {
      throw new IllegalArgumentException("Invalid empty sequence.");
    }
    if (oseq1.size() != oseq2.size()) {
      throw new IllegalArgumentException("oseq1 and oseq2 must have the same length.");
    }
    if (hmm1.nbStates() != hmm2.nbStates()) {
      throw new IllegalArgumentException("hmm1 and hmm2 must have the same set of states.");
    }
    if (prior != null && prior.length != hmm1.nbStates()) {
      throw new IllegalArgumentException("prior must have the same length as the number of states.");
    }

    delta = new double[oseq1.size()][hmm1.nbStates()];
    psy = new int[oseq1.size()][hmm1.nbStates()];
    psy2nd = new int[oseq1.size()][hmm1.nbStates()];
    stateSequence = new int[oseq1.size()];
    stateSeq2nd = new int[oseq1.size()];

    for (int i = 0; i < hmm1.nbStates(); i++) {
      delta[0][i] = -Math.log(hmm1.getPi(i)) -
        w1 * Math.log(hmm1.getOpdf(i).probability(oseq1.get(0))) -
        w2 * Math.log(hmm2.getOpdf(i).probability(oseq2.get(0))) -
        (prior == null ? 0.0 : Math.log(prior[i]));
      psy[0][i] = 0;
      psy2nd[0][i] = 0;
    }

    Iterator<? extends O1> it1 = oseq1.iterator();
    Iterator<? extends O2> it2 = oseq2.iterator();
    if (it1.hasNext() && it2.hasNext()) {
      it1.next();
      it2.next();
    }

    int t = 1;
    while (it1.hasNext()) {
      O1 obs1 = it1.next();
      O2 obs2 = it2.next();
      for (int i = 0; i < hmm1.nbStates(); i++)
        computeStep(hmm1, obs1, w1, hmm2, obs2, w2, prior, t, i);
      t++;
    }

    lnProbability = Double.MAX_VALUE;
    for (int i = 0; i < hmm1.nbStates(); i++) {
      double thisProbability = delta[oseq1.size()-1][i];
      if (lnProbability > thisProbability) {
        lnProbability = thisProbability;
        stateSequence[oseq1.size()-1] = i;
      }
    }
    lnProbability = -lnProbability;
    for (int t2 = oseq1.size() - 2; t2 >= 0; t2--) {
      stateSequence[t2] = psy[t2+1][stateSequence[t2+1]];
      stateSeq2nd[t2] = psy2nd[t2+1][stateSequence[t2+1]];
    }
  }

  private <O1 extends Observation, O2 extends Observation> void
    computeStep(Hmm<O1> hmm1, O1 o1, double w1,
                Hmm<O2> hmm2, O2 o2, double w2,
                double[] prior, int t, int j) {
    double minDelta = Double.MAX_VALUE;
    double minDelta2 = Double.MAX_VALUE;
    int min_psy = 0;
    int min_psy2 = 0;
    for (int i = 0; i < hmm1.nbStates(); i++) {
      double thisDelta = delta[t-1][i] - Math.log(hmm1.getAij(i, j));
      if (minDelta > thisDelta) {
        minDelta2 = minDelta;
        min_psy2 = min_psy;
        minDelta = thisDelta;
        min_psy = i;
      } else if (minDelta2 > thisDelta) {
        minDelta2 = thisDelta;
        min_psy2 = i;
      }
    }
    delta[t][j] = minDelta -
      w1 * Math.log(hmm1.getOpdf(j).probability(o1)) -
      w2 * Math.log(hmm2.getOpdf(j).probability(o2)) -
      (prior == null ? 0.0 : Math.log(prior[j]));
    psy[t][j] = min_psy;
    psy2nd[t][j] = min_psy2;
  }

  public double lnProbability() {
    return lnProbability;
  }

  public int[] stateSequence() {
    return stateSequence.clone();
  }

  public int mostLikelyState(int t) {
    return stateSequence[t];
  }

  public int secondaryState(int t) {
    return stateSeq2nd[t];
  }
  
}
