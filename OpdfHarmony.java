import be.ac.ulg.montefiore.run.jahmm.*;

public class OpdfHarmony extends OpdfMultiGaussian {
  
  public OpdfHarmony(int dim, int center, double var1, double var2) {
    super(makeMean(dim, center), makeCov(dim, center, var1, var2));
  }

  private static double[] makeMean(int dim, int center) {
    double[] mean = new double[dim];
    for (int i = 0; i < dim; i++) {
      int diff = Math.abs(center - i);
      if (diff == 1 || diff == 2 || diff == dim-2 || diff == dim-1) 
        mean[i] = 0.0;
      else
        mean[i] = 0.5;
    }
    return mean;
  }

  private static double[][] makeCov(int dim, int center, double var1, double var2) {
    double[][] cov = new double[dim][dim];
    for (int i = 0; i < dim; i++) {
      int diff = Math.abs(center - i);
      if (diff == 1 || diff == 2 || diff == dim-2 || diff == dim-1)
        cov[i][i] = var1;
      else
        cov[i][i] = var2;
    }
    return cov;
  }

  public static double weight = 1;
  
  public static ObservationVector tanh(ObservationVector x) {
    double[] y = new double[x.dimension()];
    for (int i = 0; i < y.length; i++) 
      y[i] = Math.tanh(x.value(i) * weight);
    return new ObservationVector(y);
  }

  public static ObservationVector softmax(ObservationVector x) {
    double[] y = new double[x.dimension()];
    for (int i = 0; i < y.length; i++) 
      y[i] = Math.exp(x.value(i));
    double sum = 0.0;
    for (int i = 0; i < y.length; i++) 
      sum += y[i];
    for (int i = 0; i < y.length; i++) 
      y[i] = y[i] / sum;
    return new ObservationVector(y);
  }

  public static ObservationVector normalize(ObservationVector x) {
    double[] y = new double[x.dimension()];
    double sqsum = 0.0;
    for (int i = 0; i < x.dimension(); i++)
      sqsum += x.value(i) * x.value(i);
    sqsum = Math.sqrt(sqsum);
    if (sqsum != 0.0) {
	for (int i = 0; i < y.length; i++)
	    y[i] = x.value(i) / sqsum;
    }
    return new ObservationVector(y);
  }
}
    
