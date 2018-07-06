/*****************************************************************
// Copyright 2012-2014 Masanori Morise. All Rights Reserved.
// Author: Kazuki Urabe 
// 
// F0 vac estimation based on DIO(Distributed Inline-filter Operation)
// Referring to World(http://ml.cs.yamanashi.ac.jp/world/index.html).
*****************************************************************/
import jp.crestmuse.cmx.processing.*;
import jp.crestmuse.cmx.filewrappers.*;
import jp.crestmuse.cmx.amusaj.filewrappers.*;
import jp.crestmuse.cmx.amusaj.sp.*;
import jp.crestmuse.cmx.math.*;
import jp.crestmuse.cmx.sound.*;
import processing.core.*;
import com.sun.jna.Library;
import com.sun.jna.Pointer;
import com.sun.jna.*;
import com.sun.jna.ptr.*;

//public static double notenum;
class GlobalVariable{

	public static double notenum;
}

public class module_Dio extends PApplet {
	CMXController cmx = CMXController.getInstance();
	public void setup(){
		try {
			size(400, 800);
			background(255);
			cmx.readConfig("config.xml");

			WindowSlider inputM = cmx.createMic(48000);
			//			WindowSlider inputM = new WindowSlider(false);
			//			inputM.setInputData(WAVWrapper.readfile("scale1mono.wav"));
			cmx.addSPModule(inputM);

			//(waveファイルのサンプリングレート[Hz],　1周期あたりのデータ数)
			Diocomp dioM = new Diocomp(44100,8192);
			cmx.addSPModule(dioM);

			cmx.connect(inputM, 0, dioM, 0);
			cmx.startSP();

		} catch (Exception e){
			e.printStackTrace();
		}
	}
	public void draw(){
	    background(255);
		stroke(20);
		fill(0);
		textSize(5);
		for(int i=0;i<80;i++){
			text(i,100,i*10);
		}

		line(0,(int)(GlobalVariable.notenum*10),400,(int)(GlobalVariable.notenum*10)); 
	}
	public static void main(String[] args){
		PApplet.main(new String[] { "module_Dio" });
	}
}
class Diocomp extends SPModule {
	private int fs;	//サンプリングレート[Hz]
	private int x_length;	//1周期あたりのデータ数
	private int f0_length;	//F0のサンプル数
	private double frameperiod = 5.0;	//フレームレート
	private Pointer f0;	//推定されたF0周波数
	private Pointer time_axis;//
	public final long offset = Native.getNativeSize(Double.TYPE);//オフセット値
	World diolib; 

	public int cnt = 0;

	interface World extends Library {
		// loadLibraryの第一引数はlib***.soの***を指定
		World INSTANCE = (World) Native.loadLibrary("dio", World.class);
		// 使用するライブラリ関数を指定
		int GetSamplesForDIO(int fs, int x_length, double frame_period);
		void F0Estimation(Pointer input, int x_length, int fs, int f0_length, Pointer f0, Pointer time_axis);
	}

	Diocomp(int fs, int x_length){
		this.fs = fs;	
		this.x_length = x_length;
		diolib = World.INSTANCE;
		this.f0_length = diolib.GetSamplesForDIO(fs, x_length, this.frameperiod);
		this.f0 = new Memory(this.f0_length * this.offset);
		this.time_axis = new Memory(this.f0_length * this.offset);
	}
	
	public void execute(Object[] src, TimeSeriesCompatible[] dest) 
	throws InterruptedException {
		//src[0] -> DoubleArray -> Pointer(C++のdouble*に対応)
		DoubleArray d_array = (DoubleArray)src[0];
		Pointer input = new Memory(d_array.length() * this.offset);
		for (int i=0;i<(d_array.length()-1);i++){
			input.setDouble(i*this.offset,d_array.get(i));
		}

		//F0を推定
		diolib.F0Estimation(input, this.x_length, this.fs, this.f0_length, this.f0, this.time_axis);

		//Pointer ->double[] -> DoubleArray -> dest[0]
		double mean_f0 = 0;
		double output[] = new double[this.f0_length];
		for(int i=0;i<this.f0_length;i++){
			mean_f0 += this.f0.getDouble(i*this.offset);
			output[i] = this.f0.getDouble(i*this.offset);
		}
		dest[0].add(MathUtils.createDoubleArray(output));
		
		//推定されたF0周波数の平均を出力
		mean_f0 = mean_f0 / this.f0_length;
		//System.out.printf("meanF0[%d]: %f\n", this.cnt, Math.log(mean_f0));

        //calc hz->notenum
		
		GlobalVariable.notenum=Math.round((Math.log(mean_f0/440) / Math.log(2)*12)+69);
        System.out.printf("notenum=%f,f0=%f\n",GlobalVariable.notenum,mean_f0);

		this.cnt++;
	}
	public Class[] getInputClasses() {
		return new Class[]{ DoubleArray.class };
	}
	public Class[] getOutputClasses() {
		return new Class[]{ DoubleArray.class };
	}
}
