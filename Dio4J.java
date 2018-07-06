/*****************************************************************
// Copyright 2014-2015 Kazuki Urabe. All Rights Reserved.
// 
// F0 estimation based on DIO(Distributed Inline-filter Operation)
// Referring to World(http://ml.cs.yamanashi.ac.jp/world/index.html).
*****************************************************************/
import jp.crestmuse.cmx.amusaj.sp.*;
import jp.crestmuse.cmx.math.*;
import com.sun.jna.*;

public class Dio4J {
  private int fs;													//サンプリングレート[Hz]
  private int x_length;											//1周期あたりのデータ数
  private int f0_length;											//F0のサンプル数
  private double frameperiod = 5.0;								//フレームレート
  private Pointer f0;												//推定されたF0周波数
  private Pointer time_axis;										//
  public final long offset = Native.getNativeSize(Double.TYPE);	//オフセット値
  World diolib;

  public int cnt = 0;

  interface World extends Library {
    // loadLibraryの第一引数はlib***.soの***を指定
    World INSTANCE = (World) Native.loadLibrary("dio", World.class);
    // 使用するライブラリ関数を指定
    int GetSamplesForDIO(int fs, int x_length, double frame_period);
    void F0Estimation(Pointer input, int x_length, int fs, int f0_length, Pointer f0, Pointer time_axis);
  }

  Dio4J(int fs, int x_length){
    this.fs = fs;
    this.x_length = x_length;
    diolib = World.INSTANCE;
    this.f0_length = diolib.GetSamplesForDIO(fs, x_length, this.frameperiod);
    this.f0 = new Memory(this.f0_length * this.offset);
    this.time_axis = new Memory(this.f0_length * this.offset);
  }
  
  
  public DoubleArray calcF0(DoubleArray d_array) {
    Pointer input = new Memory(d_array.length() * this.offset);
    for (int i=0;i<(d_array.length()-1);i++){
      input.setDouble(i*this.offset,d_array.get(i));
    }
    
    //F0を推定
    diolib.F0Estimation(input, this.x_length, this.fs, this.f0_length, this.f0, this.time_axis);

    //Pointer ->double[] -> DoubleArray -> dest[0]
    double output[] = new double[this.f0_length];
    for(int i=0;i<this.f0_length;i++){
      output[i] = this.f0.getDouble(i*this.offset);
    }
    return MathUtils.createDoubleArray(output);
  }
}