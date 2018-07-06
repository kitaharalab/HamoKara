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



public class CheckHamo_all extends PApplet{


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
	boolean plus = false;
	boolean isEnter = true;
	boolean isHamo = true;
    boolean isStop = false;
    boolean isSeven = true;
	
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
				}else if((file.toString()).contains("25_re")){midi = cmx.readSMFAsMIDIXML(createInput("../midis/X55627G2.MID"));    }



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
   

                /*
				for(int i=0;i<mainNotelist.length;i++){
					for(int j=0;j<notelist_comp.size();j++){
						if(notelist_comp.get(j).onset() >= mainOnset[i]//////////////// 
								&& notelist_comp.get(j).onset()<=mainOffset[i]){ 

							if(mainOffset[i]>=notelist_comp.get(j).offset()){
								//1
								tick=notelist_comp.get(j).offset() - notelist_comp.get(j).onset();          
								code[notelist_comp.get(j).notenum() % 12][i] += tick;

							}else if(mainOffset[i] <= notelist_comp.get(j).offset()){
								//2
								tick=mainOffset[i] - notelist_comp.get(j).onset();
								code[notelist_comp.get(j).notenum() % 12][i] += tick;
							}

						}else if(notelist_comp.get(j).onset() <= mainOnset[i]
								&& notelist_comp.get(j).offset() >= mainOnset[i]){ ////////////// 

							if(mainOffset[i]>=notelist_comp.get(j).offset()){  ///////////////////
								//3
								tick=notelist_comp.get(j).offset() - mainOnset[i];
								code[notelist_comp.get(j).notenum() % 12][i] += tick;

							}else if(mainOffset[i] <= notelist_comp.get(j).offset()){
								//4
								tick=mainOffset[i] - mainOnset[i];
								code[notelist_comp.get(j).notenum() % 12][i] += tick;
							}
						}
					}
				}   
                for(int i=0;i<code.length;i++){
                    for(int j=0;j<code[0].length;j++){
					println("code:"+code[i][j]);
					}
				}
				//println(mainNote);
				SCCXMLWrapper.HeaderElement[] musicKey = scc.getHeaderElementList();   
				keyChangeCount=0;
				for(int i=0; i<musicKey.length;i++){
					if(musicKey[i].name().equals("KEY")){
						print(musicKey[i].content());
						println("->"+musicKey[i].time());
						KeySymbol key = KeySymbol.parse(musicKey[i].content());
						NoteSymbol root = key.root();
						System.out.println("root: " + root.toString() + " (string type)");
						int root_notenum = root.number();
						System.out.println("root: " + root_notenum + " (int type)");
						KeySymbol.Mode mode = key.mode();
						System.out.println("mode: " + mode.toString()); 
						keyChangeCount++;
						//tempo_tick[temposoeji][1]=(int)tempodoko[i].time();                  //
						//temposoeji++;
						//System.out.println("マッチしません");
					}
				}   		
				String[] keyChangeMode = new String[keyChangeCount+1];
				keyChangeNum = new int[keyChangeCount+1];
				keyChange = new int[keyChangeCount+1];
				countLoop=0;
				for(int i=0; i<musicKey.length;i++){
					if(musicKey[i].name().equals("KEY")){
						KeySymbol keyS = KeySymbol.parse(musicKey[i].content());
						NoteSymbol root = keyS.root();
						KeySymbol.Mode mode = keyS.mode(); 
						keyChangeNum[countLoop]=(int)(musicKey[i].time());
						keyChange[countLoop]=root.number();
						keyChangeMode[countLoop]=mode.toString();
						countLoop++;
					}
				} 

				//tick->notenum of num?
				countLoop2 = 0;
				keyChangeNum[0]=0;    
				//println(mainOnset);
				for(int i=0; i<mainOnset.length; i++){
					//print("i"+i);
			    	//println("NUM"+keyChangeNum[countLoop2]+"ONSET"+mainOnset[i]);
					if(keyChangeNum[countLoop2]<mainOnset[i] && countLoop2<keyChangeCount){
						//println("CC");
						//println("i"+i);
						keyChangeNum[countLoop2]=i;
						countLoop2++;
					}
				}
				//println(mainLength+"AA"+countLoop);
				keyChangeNum[countLoop]=mainLength;
				for(int i=0;i<keyChangeNum.length;i++){

					//println("Change"+keyChangeNum[i]);
				}


				transition= new int[12][12];//12*12の配列をていぎ
				for(int i=0; i<12;i++){      //初期値を0に設定
					for(int j=0;j<12;j++){   //遷移がわかったら+1していく
						transition[i][j] =0;
					}
				} 
				aij= new double[12][12];//12*12の配列をていぎ


				transum = new int[12];
				for(int i=0; i<12 ;i++){ //初期値0
					transum[i] = 0;
				}
				for(int i=0;i<mainNotelist.length-1;i++){

					before = mainNotelist[i].notenum();
					current = mainNotelist[i+1].notenum();
					//System.out.println(before+","+current);
					//System.out.println(before%12+","+current%12);
					//System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"); 
					if((before != -1) && (current != -1)){
						transition[before % 12][current % 12] += 1;
					}
				} 
				for(int i=0;i<12;i++){
					for(int j=0;j<12;j++){
						//println("gggggggggggggggggggg");
						transum[i] += transition[i][j];
					}
				}
				for(int i=0;i<12;i++){
					for(int j=0;j<12;j++){
						//System.out.println("trans:"+transition[i][j]+"transum:"+transum[i]+
						//  	"per:"+(double)transition[i][j] / (double)transum[i]);
						aij[i][j] = (double)transition[i][j] / (double)transum[i];
					}
				}*/

				//hmm
				/*hmmJ.main(mainNote,code,mainLength,keyChangeNum,keyChange,keyChangeMode,plus,aij); 

				for(int i=0 ;i< mainLength;i++){
					if(plus){
						if(mainNote[i]%12 <= hmmJ.hamohamo.get(i)){
							hamoNote[i] = (mainNote[i]/12) * 12 +hmmJ.hamohamo.get(i);
						}else if(mainNote[i]%12 > hmmJ.hamohamo.get(i)){
							hamoNote[i] = (mainNote[i]/12 + 1) * 12 +hmmJ.hamohamo.get(i);
						}
					}else{
						if(mainNote[i]%12 < hmmJ.hamohamo.get(i)){
							hamoNote[i] = (mainNote[i]/12 -1) * 12 +hmmJ.hamohamo.get(i);
						}else if(mainNote[i]%12 >= hmmJ.hamohamo.get(i)){
							hamoNote[i] = (mainNote[i]/12) * 12 +hmmJ.hamohamo.get(i);
						}
					}
				}   
				//println(hamoNote);
				for(int i=0;i<mainLength;i++){
					p.addNoteElement((int)mainOnset[i],(int)mainOffset[i],(int)(hamoNote[i]),100,100); 
				}*/ 

				//精度チェック
				if(checking){
					notenumCheck = new long[notelistCheck.length];       //
					onsetCheck = new long[notelistCheck.length];           //
					offsetCheck = new long[notelistCheck.length];            //

					for (int i=0; i<notelistCheck.length; i++) {
						notenumCheck[i]=notelistCheck[i].notenum();
						onsetCheck[i]=notelistCheck[i].onset();
						offsetCheck[i]=notelistCheck[i].offset();
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
				System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~");
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

								   hamoNote[i] = -1;
								   System.out.println("hamoNote="+(mainNote[i]+hamoData[i]) 
										   + " , NoData");     
								    noDataNum++;  
							   }
						   }else{
							   if(hamoData[i]==-3 || hamoData[i]==-4 ||hamoData[i]==-5||hamoData[i]==-7){

								   System.out.println("hamoNote="+(mainNote[i]+hamoData[i]) 
										   + " , " + hamoData[i]); 
								   hamoNote[i] = mainNote[i]+hamoData[i];
								   p.addNoteElement((int)mainOnset[i],(int)mainOffset[i],(int)(mainNote[i]+hamoData[i]),100,100); 
							   }else if(hamoData[i]==0){

								   hamoNote[i] = -1;
								   System.out.println("hamoNote="+(mainNote[i]+hamoData[i]) 
										   + " , NoData");     
								   noDataNum++;
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
			"CheckHamo_all"
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
