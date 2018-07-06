/*****************************************************************
// Copyright 20linemid-2014 Masanori Morise. All Rights Reserved.
// Author: Kazuki Urabe 
// 
// F0 estimation based on DIO(Distributed Inline-filter Operation)
// Referring to World(http://ml.cs.yamanashi.ac.jp/world/index.html).
 *****************************************************************/

//hmm
import be.ac.ulg.montefiore.run.jahmm.Hmm;
import be.ac.ulg.montefiore.run.jahmm.draw.GenericHmmDrawerDot;
import be.ac.ulg.montefiore.run.jahmm.learn.BaumWelchLearner;
import be.ac.ulg.montefiore.run.jahmm.Observation;
import be.ac.ulg.montefiore.run.jahmm.ObservationInteger;
import be.ac.ulg.montefiore.run.jahmm.OpdfInteger;
import be.ac.ulg.montefiore.run.jahmm.OpdfIntegerFactory;
import be.ac.ulg.montefiore.run.jahmm.*;   

//import be.ac.ulg.montefiore.run.jahmm.observables.ObservationEnum;//?
//import be.ac.ulg.montefiore.run.jahmm.observables.OpdfEnum;//?
//import be.ac.ulg.montefiore.run.jahmm.observables.OpdfEnumFactory;//?
import be.ac.ulg.montefiore.run.jahmm.toolbox.KullbackLeiblerDistanceCalculator;
import be.ac.ulg.montefiore.run.jahmm.toolbox.MarkovGenerator;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;    


import jp.crestmuse.cmx.processing.*;
import jp.crestmuse.cmx.filewrappers.*;
import jp.crestmuse.cmx.amusaj.filewrappers.*;
import jp.crestmuse.cmx.amusaj.sp.*;
import jp.crestmuse.cmx.math.*;
import jp.crestmuse.cmx.sound.*;
import jp.crestmuse.cmx.elements.*;
import jp.crestmuse.cmx.misc.*;            //add
import processing.core.*;
import com.sun.jna.Library;
import com.sun.jna.Pointer;
import com.sun.jna.*;
import com.sun.jna.ptr.*;
/////////////////////////////////

import javax.xml.transform.*;
import javax.xml.parsers.*;
import org.xml.sax.*;
import java.lang.Comparable;
import org.xml.sax.SAXException;

import jp.crestmuse.cmx.commands.MIDIXML2SMF;
import jp.crestmuse.cmx.filewrappers.*;
import jp.crestmuse.cmx.filewrappers.SCC.Annotation;

import java.io.IOException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;

import java.util.ArrayList;
import java.io.PrintWriter;
import java.io.BufferedReader;

import java.util.*;//梢追加分
import java.util.regex.Matcher;//梢追加文

import java.io.File;
import java.io.FileWriter;
import java.io.BufferedWriter;

import java.io.FilenameFilter; 


class GlobalVariable{//

	public static double notenum;


}



public class CheckHamo3_all extends PApplet{


	//static FileFilter filterM;

	static CMXController cmx = CMXController.getInstance();


	MIDIXMLWrapper midi;
	SCCXMLWrapper scc;
	//SCCXMLWrapper scc2; 

	//精度チェック用
	MIDIXMLWrapper midiCheck;
	SCCXMLWrapper sccCheck; 

	//

	//ArrayList<Lyric> lyrics = new ArrayList<Lyric>();
	//ArrayList<SCCXMLWrapper.Note> notelist_hamo = new ArrayList<SCCXMLWrapper.Note>(); 
	ArrayList<Integer> notenum_draw = new ArrayList<Integer>();
	//ArrayListの宣言,ノートナンバーの配列
	ArrayList<SCCXMLWrapper.HeaderElement> tempodoko = new ArrayList<SCCXMLWrapper.HeaderElement>();    

	boolean isKaraoke = false;
	boolean isMenu = true;
	boolean plus = true;
	boolean isEnter = true;
	boolean isHamo = true;
	boolean isStop = false;
	boolean isSeven = false;

	long position;


	//精度チェック
	long[] onsetCheck;  // 発音時間                      //
	long[] offsetCheck;  //  消音時間                      //
	long[] notenumCheck;                                     //

	long[] hamoData;	// メインメロディの音との差              //
	int division;
	int maxNum;
	int minNum;
	int aveNum;


	int half_div;
	long endtick;
	int on_off;

	int[][] code;		//伴奏の小節ごとにどの音が多いのかの分析//int->double
	int[][] code_hamo;  

	SCCXMLWrapper.Part[] partlist;
	//精度
	SCCXMLWrapper.Part[] partlistCheck;

	boolean isTempo = false;
	int current_tempo_soeji=0;//今どこのテンポを再生してるの？tempo_tick[current_tempo_soeji][0]
	double current_tick_all=0;//.05*notenum_draw.size()*division; //今までに何tick経過したか。

	int nanmaime2=0;//今画面何枚目か


	int notenum_value;//ノートナンバー
	int drawct=0;
	int frame_ct=0; 
	int nanmaime=0;                                                

	int key = 0;

	int musicnum = 0;
	int mainvol = 64;

	int octave = 0;

	int nowLyric=0;

	boolean checking = true;
	int match= 0;
	int hamodiv;

	int noDataNum=0;
	int tickMatch=0;

	int myint(String s) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	//hmm
	int[] mainNote;
	long[] mainOnset;   
	long[] mainOffset;  //  消音時間  
	long[] hamoNote; 

	int mainLength; 
	int keyChangeCount=0;
	int countLoop=0;


	int[] keyChangeNum;
	int[] keyChange;
	int countLoop2=1;

	ArrayList<SCCXMLWrapper.Note> notelist_comp = new ArrayList<SCCXMLWrapper.Note>();
	int transition[][];  //12*12の配列を

	int transum[]; //各音(beforeの音) の分母(？)

	int before = -1;
	int current = -1;
	double aij[][];                   

	int beforeNote=-1;
	int currentNote=-1;
	int beforeMainNote=-1;
	int currentMainNote=-1;  
	int samenum=0;
	int maxSamenum=0;
	int sameCount=0;
	int samenumMain=0;
	int maxSamenumMain=0;
	int sameCountMain=0;
	int sameUnizon=0;
	int sameUnizonMain=0;

	double sameAve=0;
	double sameMainAve=0;
	double uniAve=0;
	double uniMainAve=0;

	double musicCount=0;
	
  int allSame[];  
	static Jahmm hmmJ; 

	//int count=0;

	public void setup(){ 
		try{
			cmx.readConfig("config.xml"); 
			hmmJ = new Jahmm();  
			noLoop();
		}catch(Exception e){
		}
	}


	public void draw(){ 
		try{



			//探索先ディレクトリ
			File dir = new File("checkin/midi");

			File[] files = dir.listFiles(new FileFilter());


			try{
				File fileB = new File("./checkin/hamo_all.txt");
				FileWriter filewriter = new FileWriter(fileB,false);

				PrintWriter printwriter = new PrintWriter(new BufferedWriter(filewriter));
				printwriter.close(); 


			}catch(IOException e){
				System.out.println(e);
			} 

			try{
				File fileD = new File("./checkin/hamo_same.txt");
				FileWriter filewriter = new FileWriter(fileD,false);

				PrintWriter printwriter = new PrintWriter(new BufferedWriter(filewriter));
				printwriter.close(); 

			}catch(IOException e){
				System.out.println(e);
			} 
			try{

				File fileS = new File("./checkin/hamo_sameR.txt");
				FileWriter filewriter = new FileWriter(fileS,false); 
				PrintWriter printwriter = new PrintWriter(new BufferedWriter(filewriter));
				printwriter.close();   
			}catch(IOException e){
				System.out.println(e);
			}  
			try{
				File fileB = new File("./checkin/hamo_unizon.txt");
				FileWriter filewriter = new FileWriter(fileB,false);

				PrintWriter printwriter = new PrintWriter(new BufferedWriter(filewriter));
				printwriter.close(); 


			}catch(IOException e){
				System.out.println(e);
			}                                
            
			//excel

            allSame=new int[50];
			//allSameMain=new int[50]; 
			for(int i=0;i<50;i++){
				allSame[i]=0;
				//allSameMain[i]=0;
			}  
			
			//////////////////////////////////////////////////////////////
			//実行処理↓
			for(File file : files){
				midiCheck = cmx.readSMFAsMIDIXML(createInput(file));
				println(file);
				if((file.toString()).contains("01_re")){   
					midi = cmx.readSMFAsMIDIXML(createInput("../midis/XG5052G1.MID")); 
					println("../midis/XG5052G1.MID");
				}else if((file.toString()).contains("02_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X52644G2.MID"));  
				}else if((file.toString()).contains("03_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X55348G2.MID"));  
				}else if((file.toString()).contains("04_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/R00161G2.MID"));  
					//}else if((file.toString())("05_re")){midi = cmx.readSMFAsMIDIXML(createInput(""));  
			}else if((file.toString()).contains("06_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X25943G.MID"));  
			}else if((file.toString()).contains("07_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X10675G2.MID"));  
			}else if((file.toString()).contains("08_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X26451G2.MID"));  
			}else if((file.toString()).contains("09_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X52191G2.MID"));  
			}else if((file.toString()).contains("10_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X53846G2.MID"));  
			}else if((file.toString()).contains("11_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X54664G2.MID"));  		
			}else if((file.toString()).contains("12_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/R00512G2.MID"));  
			}else if((file.toString()).contains("13_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X29048G2.MID"));  
			}else if((file.toString()).contains("14_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X46920G2.MID"));  
			}else if((file.toString()).contains("15_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X54842G2.MID"));  
			}else if((file.toString()).contains("16_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X54963G2.MID"));  
			}else if((file.toString()).contains("17_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X55654G2.MID"));  
			}else if((file.toString()).contains("18_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/XG5636K1.MID"));  
			}else if((file.toString()).contains("19_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X48018G2.MID"));  
			}else if((file.toString()).contains("20_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/XG5506G.MID"));  
			}else if((file.toString()).contains("21_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/XG5415G.MID"));  
			}else if((file.toString()).contains("22_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X52862G2.MID"));  
			}else if((file.toString()).contains("23_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X54900G2.MID"));  
			}else if((file.toString()).contains("24_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X53199G2.MID"));  
			}else if((file.toString()).contains("25_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X55627G2.MID"));
			}else if((file.toString()).contains("26_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/R00088G2.MID"));   
			}else if((file.toString()).contains("27_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/R00457G2.MID"));
			}else if((file.toString()).contains("28_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X22496G.MID"));   
			}else if((file.toString()).contains("29_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X00377G2.MID"));   
			}else if((file.toString()).contains("30_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/R00327G2.MID")); 
			}else if((file.toString()).contains("31_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/R00473G2.MID")); 
			}else if((file.toString()).contains("32_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X49734G2.MID")); 
			}else if((file.toString()).contains("33_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X55527G2.MID")); 

			}else if((file.toString()).contains("34_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/R00159G2.MID")); 
			}else if((file.toString()).contains("35_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/R00224G2.MID"));    
			}else if((file.toString()).contains("36_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/XG5413G.MID"));    

			}else if((file.toString()).contains("37_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X25869G2.MID"));    
			}else if((file.toString()).contains("38_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X24585G2.MID"));    
			}else if((file.toString()).contains("39_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X01753G2.MID"));    
			}else if((file.toString()).contains("40_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X55432G2.MID"));    
			}else if((file.toString()).contains("41_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/R00325G2.MID"));    
			}else if((file.toString()).contains("42_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/R00368G2.MID"));    
			}else if((file.toString()).contains("43_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/XG8528G.MID"));    
			}else if((file.toString()).contains("44_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X52612G2.MID"));    
			}else if((file.toString()).contains("45_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/R00425G2.MID"));    
			}else if((file.toString()).contains("46_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/R00444G2.MID"));    
			}else if((file.toString()).contains("47_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/XG5037K1.MID"));    
			}else if((file.toString()).contains("48_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X53036G2.MID"));    
			}else if((file.toString()).contains("49_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X53327G2.MID"));    
			}else if((file.toString()).contains("50_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X54604G2.MID")); 
			}else if((file.toString()).contains("51_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X00594G2.MID"));   
			}else if((file.toString()).contains("52_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X52265G2.MID"));
			}else if((file.toString()).contains("53_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X54791G2.MID"));
			}else if((file.toString()).contains("54_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X55270G2.MID"));   
			}else if((file.toString()).contains("55_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/R00404G2.MID"));   
			}else if((file.toString()).contains("56_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X52402G2.MID"));   
			}else if((file.toString()).contains("57_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/R00156G2.MID"));   
			}else if((file.toString()).contains("58_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/R00419G2.MID"));   
			}else if((file.toString()).contains("59_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/XG5024K1.MID"));   
			}else if((file.toString()).contains("60_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X23154G2.MID"));   
			}else if((file.toString()).contains("61_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X26189G2.MID"));   
			}else if((file.toString()).contains("62_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X40179G2.MID"));
			}else if((file.toString()).contains("63_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X54049G2.MID"));
			}else if((file.toString()).contains("64_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X54892G2.MID"));   
			}else if((file.toString()).contains("65_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X55845G2.MID"));   
			}else if((file.toString()).contains("66_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/R00182G2.MID"));   
			}else if((file.toString()).contains("67_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/R00429G2.MID"));   //}else if((file.toString()).contains("68_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/R00419G2.MID"));   
			}else if((file.toString()).contains("69_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/XG5412G.MID"));   
			}else if((file.toString()).contains("70_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X02588G2.MID"));  
			}else if((file.toString()).contains("71_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X53641G2.MID"));  
			}else if((file.toString()).contains("72_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X54620G2.MID")); 
			}else if((file.toString()).contains("73_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/R00398G2.MID")); 
			}else if((file.toString()).contains("74_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/R00421G2.MID")); 
			}else if((file.toString()).contains("75_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/R00581G2.mid")); 
			}else if((file.toString()).contains("76_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/R00594G2.mid")); 
			}else if((file.toString()).contains("77_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/R00634G2.mid")); 
			}else if((file.toString()).contains("78_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/R00653G2.mid")); 
			}else if((file.toString()).contains("79_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/R00669G2.mid")); 
			}else if((file.toString()).contains("80_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/R00691G2.mid")); 
			}else if((file.toString()).contains("81_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/R00693G2.mid"));
			}else if((file.toString()).contains("82_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/R00735G2.mid")); 
			}else if((file.toString()).contains("83_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/R00753G2.mid")); 
			}else if((file.toString()).contains("84_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/R00796G2.mid")); 
			}else if((file.toString()).contains("85_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/R00815G2.mid")); 
			}else if((file.toString()).contains("86_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/R00824G2.mid"));}   


            musicCount++; 
			
			scc = midi.toSCCXML();

			partlist = scc.getPartList();
			division = scc.getDivision();

			if(checking){
				//精度
				sccCheck = midiCheck.toSCCXML();

				partlistCheck = sccCheck.getPartList();
				hamodiv = sccCheck.getDivision();
			}

			half_div=2*division; 

			SCCDataSet scc2=scc.toDataSet();
			SCCDataSet.Part p=scc2.addPart(4,15,15,128);

			SCCXMLWrapper.Note[] mainNotelist = null;
			//精度
			SCCXMLWrapper.Note[] notelistCheck = null;

			//add accompaniment 
			for(int i=0; i<partlist.length;i++){
				if(partlist[i].channel()!=1 && partlist[i].channel()!= 10){ 
					notelist_comp.addAll(Arrays.asList(partlist[i].getNoteOnlyList()));
					//println(partlist[i].getNoteOnlyList().notenum());
				}
			} 
			//scc2.toWrapper().toMIDIXML().writefileAsSMF("aaaa.mid");
			for(int i=0;i<partlist.length;i++){
				if(partlist[i].channel()==1){
					mainNotelist = partlist[i].getNoteOnlyList();	   
				}
			}
			//SCCXMLWrapper.HeaderElement[] = scc.getHeaderElentList();
			if (mainNotelist == null) {
				throw new RuntimeException("no part with channel 1");
			}  

			//精度チェック
			//チェックする音(上下ハモリ選択)
			if(checking){
				for(int i=0;i<partlistCheck.length;i++){ 
					if(plus){                              
						if(partlistCheck[i].channel()==2){      //上ハモリ
							notelistCheck = partlistCheck[i].getNoteOnlyList();
						}
					}else{
						if(partlistCheck[i].channel()==3){      //下ハモリ
							notelistCheck = partlistCheck[i].getNoteOnlyList();
						} 
					}
				} 

				//SCCXMLWrapper.HeaderElement[] = scc.getHeaderElentList();
				if (notelistCheck == null) {
					throw new RuntimeException("no part with Hamo");
				} 
			}

			mainLength = mainNotelist.length;
			mainNote = new int[mainNotelist.length];
			mainOnset = new long[mainNotelist.length];
			mainOffset = new long[mainNotelist.length];
			hamoNote = new long[mainNotelist.length];
			for(int i=0; i<mainNotelist.length; i++){
				mainNote[i] = (int)mainNotelist[i].notenum();
				mainOnset[i] = mainNotelist[i].onset();
				mainOffset[i] = mainNotelist[i].offset();
			} 
			/*
			   code = new int[12][mainNotelist.length];  
			   for(int i=0; i<12 ;i++){
			   for(int j=0;j<mainNotelist.length;j++){
			   code[i][j]=0;
			   }
			   }     */

			long tick =0;
			////////////// 

			//精度チェック
			if(checking){
				notenumCheck = new long[notelistCheck.length];       //
				onsetCheck = new long[notelistCheck.length];           //
				offsetCheck = new long[notelistCheck.length];            //

				for (int i=0; i<notelistCheck.length; i++) {
					notenumCheck[i]=notelistCheck[i].notenum();
					onsetCheck[i]=notelistCheck[i].onset(scc.getDivision());
					offsetCheck[i]=notelistCheck[i].offset(scc.getDivision());
				} 
			}

			hamoData = new long[mainNotelist.length];                      //              
			//分析
			//伴奏部分配列代入
			ArrayList<SCCXMLWrapper.Note> notelist_hamo = new ArrayList<SCCXMLWrapper.Note>();  
			for(int i=0;i<partlist.length;i++){
				if( partlist[i].channel()!=10 || partlist[i].channel()!=1 ){
					//notelist_hamo = partlist[i].getNoteOnlyList();
					notelist_hamo.addAll(Arrays.asList(partlist[i].getNoteOnlyList()));
				}                                                      
			}


			long tick_f = 0;     //
			long tick_e = 0;     //

			endtick=mainOffset[mainNotelist.length-1];
			println(endtick);
			int a=(int)Math.ceil(endtick/half_div);
			code = new int[12][a+2];
			//分析処理開始

			for(int i=0; i<12;i++){
				for(int j=0;j<a+1;j++){
					code[i][j] =0;
				}
			}

			for(int i=0;i<12;i++){
				code[i][a+1]=i;	//配列の末尾に音を示す値を代入
			}


			for(int j=0;j<notelist_hamo.size();j++){
				if(notelist_hamo.get(j).onset()<=endtick && notelist_hamo.get(j).offset()<=endtick ){

					if(abs( notelist_hamo.get(j).offset() / half_div 
								- notelist_hamo.get(j).onset() / half_div) == 0){

						tick_f = notelist_hamo.get(j).offset() - notelist_hamo.get(j).onset();
						code[ notelist_hamo.get(j).notenum()%12 ][ (int)notelist_hamo.get(j).onset() / half_div] += tick_f;

					}else if(abs( notelist_hamo.get(j).offset() / half_div 
								- notelist_hamo.get(j).onset() / half_div) == 1){
						tick_f=(notelist_hamo.get(j).onset() / half_div+1) * half_div - notelist_hamo.get(j).onset();
						code[notelist_hamo.get(j).notenum()%12][(int)notelist_hamo.get(j).onset() / half_div] += tick_f;

						tick_e=notelist_hamo.get(j).offset() - (notelist_hamo.get(j).offset()/half_div)*half_div;
						code[notelist_hamo.get(j).notenum()%12][(int)notelist_hamo.get(j).onset() /half_div] += tick_e;  

					}else if(abs( notelist_hamo.get(j).offset()/half_div 
								- notelist_hamo.get(j).onset()/half_div) >= 2){
						tick_f=(notelist_hamo.get(j).onset()/half_div+1)*half_div - notelist_hamo.get(j).onset();
						code[notelist_hamo.get(j).notenum()%12][(int)notelist_hamo.get(j).onset() / half_div]+= tick_f;

						for(int i = (int)(notelist_hamo.get(j).onset() / half_div) ; i<=notelist_hamo.get(j).offset() / half_div ; i++){       //
							println("dddddddddddddddddddd"+i);
							code[notelist_hamo.get(j).notenum()%12][i] += half_div;	
						}

						tick_e=notelist_hamo.get(j).offset() - (notelist_hamo.get(j).offset()/half_div)*half_div;
						code[notelist_hamo.get(j).notenum()%12][(int)notelist_hamo.get(j).onset()/half_div] += tick_e; 
								}
				}

			}   
			/*for(int j=0;j<10;j++){
			  System.out.println("````````"+j);
			  for(int i=0;i<12;i++){
			  System.out.println(code[i][j]); 	
			  }
			}
/			System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~");
			*/


			//ソート

			code_hamo = new int[4][a+1];
			for(int j=0;j<a;j++){
				Arrays.sort(code, new MyComparator(j));
				for (int[] e : code) {
					//System.out.println("====");
					for (int i = 0; i < e.length; i++) {
						//System.out.println(e[i]);
					}
				}  
				//if(code[0][j]!=0){
				code_hamo[0][j]=code[0][a+1];
				code_hamo[1][j]=code[1][a+1];
				code_hamo[2][j]=code[2][a+1];
				code_hamo[3][j]=code[3][a+1];
				// }

			}  
			/*for(int j=0;j<10;j++) {
			  System.out.println("=========================="+j);
			  for(int i=0;i<12;i++){
			  System.out.println(code[i][j]);
			  }
			}
			for(int j=0;j<a+50;j++){        
			System.out.println("======="+j);                                       
			for(int i=0;i<4;i++){
			//System.out.println(code[i][j]+"tick");
			System.out.println(code_hamo[i][j]);
			}

			}*/   



			//ハモリ決定処理

			for(int j=0;j<mainNotelist.length;j++){
				for(int i=3;i>=0;i--){
					//println("loop");
					if(plus){
						on_off=(int)((mainOnset[j]+mainOffset[j])/2/half_div); 
						//短3度
						if((mainNote[j]+3)%12 == code_hamo[i][on_off]){
							hamoData[j]=3;
						}
						//長3度
						if((mainNote[j]+4)%12 == code_hamo[i][on_off]){
							hamoData[j]=4;
						}
						//完全4度
						if((mainNote[j]+5)%12 == code_hamo[i][on_off]){
							hamoData[j]=5;
						}
						//seven
						if(isSeven){
							if((mainNote[j]+7)%12 == code_hamo[i][on_off]){
								hamoData[j]=7;
							}   
						}

					}else{

						on_off=(int)((mainOnset[j]+mainOffset[j])/2/half_div); 
						//短3度
						if((mainNote[j]-3)%12 == code_hamo[i][on_off]){
							hamoData[j]=-3;
						}
						//長3度
						if((mainNote[j]-4)%12 == code_hamo[i][on_off]){
							hamoData[j]=-4;
						}
						//完全4度
						if((mainNote[j]-5)%12 == code_hamo[i][on_off]){
							hamoData[j]=-5;
						}
						//seven
						if(isSeven){
							if((mainNote[j]-7)%12 == code_hamo[i][on_off]){
								hamoData[j]=-7;
							}   
						}
					}

				}


			}

			println("jjijijiji");

			/*for(int j=0;j<a+3;j++){ 
			  System.println(code_hamo[0][j],code_hamo[1][j],code_hamo[2][j],code_hamo[3][j])
			  }*/

			/*for(int i=0;i<12;i++){
			  System.out.println(code[i][5]);
			  }*/

			//ハモリ追加
			noDataNum=0;
			if(isHamo){
				for(int i=0;i<mainNotelist.length;i++){
					if(plus){
						if(hamoData[i]==3 || hamoData[i]==4 ||hamoData[i]==5||hamoData[i]==7){
							System.out.println("hamoNote="+(mainNote[i]+hamoData[i]) 
									+ " , +" + hamoData[i]); 
							hamoNote[i] = mainNote[i]+hamoData[i];
							p.addNoteElement((int)mainOnset[i],(int)mainOffset[i],(int)(mainNote[i]+hamoData[i]),100,100); 
						}else if(hamoData[i]==0){
							//NoDataの時の処理 
							//前データをぶち込む 
							//最初だったら+3
							if(i!=0){
								hamoNote[i] = mainNote[i]+hamoData[i-1];
								hamoData[i]=hamoData[i-1];
							}else if(i==0){
								hamoNote[i]=mainNote[i]+3;
								hamoData[i]=3;
							}  


							System.out.println("hamoNote="+(mainNote[i]+hamoData[i]) 
									+ " , NoData ->"+hamoData[i]); 
							p.addNoteElement((int)mainOnset[i],(int)mainOffset[i],(int)(mainNote[i]+hamoData[i]),100,100);  
						}

					}else{
						if(hamoData[i]==-3 || hamoData[i]==-4 ||hamoData[i]==-5||hamoData[i]==-7){

							System.out.println("hamoNote="+(mainNote[i]+hamoData[i]) 
									+ " , " + hamoData[i]); 
							hamoNote[i] = mainNote[i]+hamoData[i];
							p.addNoteElement((int)mainOnset[i],(int)mainOffset[i],(int)(mainNote[i]+hamoData[i]),100,100); 
						}else if(hamoData[i]==0){
							//NoDataの時の処理 
							//前データをぶち込む 
							//最初だったら-3
							if(i!=0){
								hamoNote[i] = mainNote[i]+hamoData[i-1];
								hamoData[i]=hamoData[i-1];
							}else if(i==0){
								hamoNote[i]=mainNote[i]-3;
								hamoData[i]=-3;
							}

							System.out.println("hamoNote="+(mainNote[i]+hamoData[i]) 
									+ " , NoData ->"+hamoData[i]); 
							p.addNoteElement((int)mainOnset[i],(int)mainOffset[i],(int)(mainNote[i]+hamoData[i]),100,100);  
							//noDataNum++;
						}                  

					}
				} 
			}  



			//
			SCCDataSet.Part[] parts = scc2.toDataSet().getPartList();         
			SCCDataSet.Part part = null;
			for (int i = 0; i < parts.length; i++) {
				if (parts[i].channel() == 1) {
					part = parts[i];
					break;
				}
			}
			if (part == null) {
				throw new IllegalStateException("no part with channel=1");
			} 

			MutableMusicEvent[] events = part.getNoteList();     
			for (MutableMusicEvent evt : events) {
				if (evt instanceof MutableProgramChange) {
					((MutableProgramChange)evt).setValue(4);       //音色変更
				} else if (evt instanceof MutableControlChange) {
					MutableControlChange c = (MutableControlChange)evt;
					c.setCtrlNum(7);                     //ボリューム変更
					c.setValue(mainvol);
				}
			}    





			//精度チェック
			if(checking){
				match=0;
				for (int i=0; i<mainLength; i++) {
					for (int j=0;j<onsetCheck.length; j++){
						if (mainOnset[i] == onsetCheck[j]  & mainOffset[i] == offsetCheck[j]){
							tickMatch++;
							if(hamoNote[i] == notenumCheck[j]){
								//println(tickMatch+"gggggggg"+match);
								match++;
							} 
						}
					}
					//}

			}

			println("match="+ match +"mainLength="+mainLength +"per(match/mainLength)=" + (double)match/mainLength);  
			try{
				File fileB = new File("./checkin/hamo_all.txt");
				FileWriter filewriter = new FileWriter(fileB,true);

				PrintWriter printwriter = new PrintWriter(new BufferedWriter(filewriter));
				printwriter.println("song name:"+file);
				printwriter.print("tickMatch="+tickMatch);
				printwriter.print("mainLength="+mainLength);
				printwriter.println("match="+ match + "per=" + (double)match/mainLength); 

				printwriter.println("noDataNum="+noDataNum);
				printwriter.println("except nodata per = "+(double)match/(mainLength-noDataNum));   
				printwriter.println("=====================");
				printwriter.close();
			}catch(IOException e){
				System.out.println(e);
			}

			try{
				File fileC = new File("./checkin/C_hmm.txt");
				FileWriter filewriterC = new FileWriter(fileC,false);

				PrintWriter printwriterC = new PrintWriter(new BufferedWriter(filewriterC));
				printwriterC.println("Main division = "+division);
				for(int i=0 ; i<mainOnset.length ; i++){
					printwriterC.println("Main"+i+":onset="+mainOnset[i]+",offset="+mainOffset[i]+"notenum="+hamoNote[i]);

				}
				printwriterC.println("==============");       
				printwriterC.println("Hamo division = "+hamodiv);
				for(int i=0; i<onsetCheck.length; i++){
					printwriterC.println("Hamo"+i+":onset="+onsetCheck[i]+",offset="+offsetCheck[i]+"notenum="+notenumCheck[i]);
				}
				printwriterC.close();
			}catch(IOException e){
				System.out.println(e);
			} 

			try{
				File fileC = new File("./checkin/hamo_same.txt");
				FileWriter filewriterC = new FileWriter(fileC,true);
				File fileS = new File("./checkin/hamo_sameR.txt");
				FileWriter filewriterS = new FileWriter(fileS,true);  

				PrintWriter printwriterD = new PrintWriter(new BufferedWriter(filewriterC));
				PrintWriter printwriterE = new PrintWriter(new BufferedWriter(filewriterS));
				beforeNote = (int)hamoNote[0];
				samenum=0;
				maxSamenum=0;
				sameCount=0;
				beforeMainNote = (int)mainNote[0];
				samenumMain=0;
				maxSamenumMain=0;
				sameCountMain=0;  
				printwriterD.println(file); 
				printwriterE.println(file);  
				for(int i=1;i<mainLength;i++){
					beforeNote=(int)hamoNote[i-1];
					currentNote=(int)hamoNote[i];
					beforeMainNote=(int)mainNote[i-1];
					currentMainNote=(int)mainNote[i];  
					printwriterD.println("num:======"+i);
					if(beforeNote!=currentNote){ 
						if(samenum!=0){
							sameCount++;
							allSame[samenum]++;
							samenum=0; 
						}
					}
					if(beforeMainNote!=currentMainNote){    
						if(samenumMain!=0){
							sameCountMain++;
							samenumMain=0; 
						} 
					}
					if(beforeNote==currentNote){
						samenum++;
						if(samenum>maxSamenum){maxSamenum=samenum;} 
						//printwriterD.println("la"+samenum);
					}
					if(beforeMainNote==currentMainNote){
						samenumMain++;
						if(samenumMain>maxSamenumMain){maxSamenumMain=samenumMain;} 
						printwriterD.println("laMM"+samenumMain);
					}    
				}
				printwriterD.println("max:"+maxSamenum);
				printwriterD.println("count:"+sameCount);
				
				printwriterE.println("Length:"+mainLength);
				printwriterE.println("max:"+maxSamenum);
				printwriterE.println("count:"+sameCount);
				printwriterE.println("per:"+(double)sameCount/mainLength);
				printwriterE.println("mainmax:"+maxSamenumMain);
				printwriterE.println("Maincount:"+sameCountMain);
				printwriterE.println("per:"+(double)sameCountMain/mainLength); 
				printwriterE.println("=============");
				/*printwriterC.println("Main division = "+division);
				  for(int i=0 ; i<mainOnset.length ; i++){
				  printwriterC.println("Main"+i+":onset="+mainOnset[i]+",offset="+mainOffset[i]+"notenum="+hamoNote[i]);

				  }
				  printwriterC.println("==============");       
				  printwriterC.println("Hamo division = "+hamodiv);
				  for(int i=0; i<onsetCheck.length; i++){
				  printwriterC.println("Hamo"+i+":onset="+onsetCheck[i]+",offset="+offsetCheck[i]+"notenum="+notenumCheck[i]);
				  }            */
				printwriterD.close();
				printwriterE.close();   
			}catch(IOException e){
				System.out.println(e);
			}  

			try{
				File fileB = new File("./checkin/hamo_unizon.txt");
				FileWriter filewriter = new FileWriter(fileB,true);

				PrintWriter printwriter = new PrintWriter(new BufferedWriter(filewriter)); 
				sameUnizon=0;
				for(int i=0;i<mainLength;i++){
					if(mainNote[i]==hamoNote[i]){
						sameUnizon++;
					}
				}
				printwriter.println("unizon:"+sameUnizon);
				printwriter.close();

			}catch(IOException e){
				System.out.println(e);
			}

			sameAve=sameAve+(double)sameCount/mainLength;
			sameMainAve=sameMainAve+(double)sameMainAve/mainLength;

			uniAve=uniAve+(double)sameUnizon/mainLength;


		}   



			/*prepare = new Preparation(mainNotelist.length);//check max and min
			  prepare.checkPitch();
			  maxNum=prepare.maxNotenum;
			  minNum=prepare.minNotenum;
			  aveNum=(int) ((prepare.maxNotenum+prepare.minNotenum)/2);
			  println(prepare.maxNotenum+","+prepare.minNotenum+","+aveNum);  */

			SCCXMLWrapper.HeaderElement[] tempodoko = scc.getHeaderElementList();

			SCCUtils.transpose(scc2, key, true).toDataSet().toWrapper().toMIDIXML().writefileAsSMF("aaaa.mid"); 
			println("///////////////////////////////////////////////////////////////");

		}

		try{
			File fileB = new File("./checkin/hamo_ave.txt");
			FileWriter filewriter = new FileWriter(fileB,false);   
			PrintWriter printwriter = new PrintWriter(new BufferedWriter(filewriter));
			printwriter.println("sameAve:"+(double)sameAve/musicCount);
			printwriter.println("sameMainAve:"+(double)sameMainAve/musicCount);
			printwriter.println("uniAve:"+(double)uniAve/musicCount);
			println("musicCount:"+musicCount);
			printwriter.close();


		}catch(IOException e){
			System.out.println(e);
		}

		try{
			File fileH = new File("./checkin/hamo_same_hist.txt");
			FileWriter filewriterH = new FileWriter(fileH,true); 
			//File fileM = new File("./checkin/hmm_same_hist_main.txt");
			//FileWriter filewriterM = new FileWriter(fileM,true); 
			PrintWriter printwriterH = new PrintWriter(new BufferedWriter(filewriterH)); 
			//PrintWriter printwriterM = new PrintWriter(new BufferedWriter(filewriterM)); 
			for(int i=0;i<50;i++){
				printwriterH.println(allSame[i]);
				//printwriterM.println(allSameMain[i]);
			}
			printwriterH.close();
			//printwriterM.close();
		}catch(IOException e){
			System.out.println(e);
		}   


	}  catch(TransformerException e) {
		//do nothing
	}
	catch(IOException e) {
		//do nothing
	}
	catch(ParserConfigurationException e) {
		//do nothing
	}
	catch(SAXException e) {
		//do nothing    
	}
}          

class MyComparator implements Comparator<int[]> {
	int num;
	MyComparator(int n) {
		num = n;
	}  
	public int compare(int[] a, int[] b) {
		return b[num] - a[num];
	}
} 


public static void main(String[] args) {
	PApplet.main(new String[] { 
		"CheckHamo3_all"
	}
	);
}
}


class FileFilter implements FilenameFilter{

	public boolean accept(File dir, String name) {

		if(name.matches(".*\\.MID$")){
			return true;
		}else if(name.matches(".*\\.mid$")){
			return true;
		}

		return false;
	}
}  
