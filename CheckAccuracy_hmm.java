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


class GlobalVariable{//

	public static double notenum;
    

}



public class CheckAccuracy_hmm extends PApplet {

	static CMXController cmx = CMXController.getInstance();
	Karaoke karaoke;
	AnnotationLyric annotation;
	Menu menu;
	Preparation prepare;

	
	MIDIXMLWrapper midi;
	SCCXMLWrapper scc;
	//SCCXMLWrapper scc2; 
  
    //精度チェック用
	MIDIXMLWrapper midiCheck;
	SCCXMLWrapper sccCheck; 

	//

	ArrayList<Lyric> lyrics = new ArrayList<Lyric>();
	ArrayList<SCCXMLWrapper.Note> notelist_hamo = new ArrayList<SCCXMLWrapper.Note>(); 
	ArrayList<Integer> notenum_draw = new ArrayList<Integer>();
	//ArrayListの宣言,ノートナンバーの配列
	ArrayList<SCCXMLWrapper.HeaderElement> tempodoko = new ArrayList<SCCXMLWrapper.HeaderElement>();    

	boolean isKaraoke = false;
	boolean isMenu = true;
	boolean plus = false;
	boolean isEnter = true;
	boolean isHamo = true;
    boolean isStop = false;
    boolean isAccompany = true;  

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

	double[][] code;		//伴奏の小節ごとにどの音が多いのかの分析//int->double
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

	boolean checking = false;
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
    int[] hamoNote; 
    
    int mainLength; 
	int keyChangeCount=0;
    int countLoop=0;
    
	int[] keyChangeNum;
	int countLoop2=1;

    ArrayList<SCCXMLWrapper.Note> notelist_comp = new ArrayList<SCCXMLWrapper.Note>();

	static Jahmm hmmJ; 
	
	//
	static Diocomp dioM;

	public void setup() {
		try {
			//size(400, 400);
			//background(255);
			cmx.readConfig("config.xml");

			WindowSlider inputM = cmx.createMic(48000);
			//WindowSlider inputM = new WindowSlider(false);
			//inputM.setInputData(WAVWrapper.readfile("scale1mono.wav"));
			cmx.addSPModule(inputM);

			//(waveファイルのサンプリングレート[Hz],　1周期あたりのデータ数)
			dioM = new Diocomp(44100,8192);
			cmx.addSPModule(dioM);

			cmx.connect(inputM, 0, dioM, 0);
			cmx.startSP();

			//
			println(displayWidth + " : " + displayHeight);
			size((int)(displayWidth*0.95), (int)(displayHeight*0.95));
			cmx.showMidiOutChooser(null);
			menu = new Menu();
			hmmJ = new Jahmm();
		} 
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	//追加




	public void draw() {

		background(255);
		textSize(75);
		if (isMenu) {
			menu.displayAll();
		}
		if (isKaraoke) {

			position = cmx.getTickPosition();
			karaoke.displayLyric();
			karaoke.displayToneLine();
            karaoke.displayVol();
			
		}
	}




	public void keyPressed() {

		if(key == '+'){ plus = true;}
		else if (key == '-'){ plus = false;} 

		if(isMenu) {
			if(keyCode == UP){
				if(musicnum>0){ musicnum--;  }
			}else if(keyCode == DOWN){
				if(musicnum<9){ musicnum++; }
			}

			if (keyCode == ENTER) {

				if(musicnum==0){
					midi = cmx.readSMFAsMIDIXML(createInput("R00088G2.MID")); //RPG  
				}else if(musicnum==1){
					midi = cmx.readSMFAsMIDIXML(createInput("R00156G2.MID")); //only my raligun
				}else if(musicnum==2){   
					midi = cmx.readSMFAsMIDIXML(createInput("R00368G2.MID")); //にじいろ 
				}else if(musicnum==3){   
					midi = cmx.readSMFAsMIDIXML(createInput("R00457G2.MID"));//オリオンをなぞる
				}else if(musicnum==4){   
					midi = cmx.readSMFAsMIDIXML(createInput("X22496G.MID"));//TUNAMI
				}else if(musicnum==5){   
					midi = cmx.readSMFAsMIDIXML(createInput("X46920G2.MID"));//明日への扉
				}else if(musicnum==6){   
					midi = cmx.readSMFAsMIDIXML(createInput("X52265G2.MID"));//3月9日
				}else if(musicnum==7){   
					midi = cmx.readSMFAsMIDIXML(createInput("X52402G2.MID"));//奏
				}else if(musicnum==8){   
					midi = cmx.readSMFAsMIDIXML(createInput("X52612G2.MID"));//青いベンチ
				}else if(musicnum==9){   
					midi = cmx.readSMFAsMIDIXML(createInput("X54791G2.MID"));//愛唄
				}else if(musicnum==10){
				    midi = cmx.readSMFAsMIDIXML(createInput("X55348G2.MID"));//366日
				}


				//精度
				if(checking){
					midiCheck = cmx.readSMFAsMIDIXML(createInput("./checkin/midi/03_re.mid"));
				}

				try {
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
					hamoNote = new int[mainNotelist.length];
					for(int i=0; i<mainNotelist.length; i++){
						mainNote[i] = (int)mainNotelist[i].notenum();
						mainOnset[i] = mainNotelist[i].onset();
						mainOffset[i] = mainNotelist[i].offset();
					}
					code = new double[12][mainNotelist.length];  
					for(int i=0; i<12 ;i++){
						for(int j=0;j<mainNotelist.length;j++){
							code[i][j]=0;
						}
					}

					long tick =0;
					//////////////
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


					//println(mainNote);
					SCCXMLWrapper.HeaderElement[] musicKey = scc.getHeaderElementList();                                  			for(int i=0; i<musicKey.length;i++){
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
					String[][] keyChange = new String[keyChangeCount+1][2];
					keyChangeNum = new int[keyChangeCount+1];
					for(int i=0; i<musicKey.length;i++){
						if(musicKey[i].name().equals("KEY")){
							KeySymbol keyS = KeySymbol.parse(musicKey[i].content());
							NoteSymbol root = keyS.root();
							KeySymbol.Mode mode = keyS.mode(); 
							keyChangeNum[countLoop]=(int)(musicKey[i].time());
							keyChange[countLoop][0]=root.toString();
							keyChange[countLoop][1]=mode.toString();
							//println("DDDDDDDDD"+(root.number()).getClass());
							countLoop++;
						}
					}
					//tick->notenum of num?
					//countLoop = 0;
					keyChangeNum[0]=0;    
					println(mainOnset);
					for(int i=0; i<mainOnset.length; i++){
						//print("i"+i);
						println("NUM"+keyChangeNum[countLoop2]+"ONSET"+mainOnset[i]);
						if(keyChangeNum[countLoop2]<mainOnset[i] && countLoop2<keyChangeCount){
							println("CC");
							println("i"+i);
							keyChangeNum[countLoop2]=i;
							countLoop2++;
						}
					}
					println(mainLength+"AA"+countLoop);
					keyChangeNum[countLoop]=mainLength;
					for(int i=0;i<keyChangeNum.length;i++){

						println("Change"+keyChangeNum[i]);
					}
					//hmm
					hmmJ.main(mainNote,code,mainLength,keyChangeNum,keyChange); 
                    
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
					} 

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

					//hamoData = new long[notelist.length];                      //              
					//分析
					//伴奏部分配列代入
					/*
					for(int i=0;i<partlist.length;i++){
						if( partlist[i].channel()!=10 || partlist[i].channel()!=1 ){
							//notelist_hamo = partlist[i].getNoteOnlyList();
							notelist_hamo.addAll(Arrays.asList(partlist[i].getNoteOnlyList()));
						}                                                      
					}
					
						 
					long tick_f = 0;     //
					long tick_e = 0;     //

					endtick=mainOffset[notelist.length-1];
					println(endtick);
					int a=(int)Math.ceil(endtick/half_div);
					code = new int[12][a+100];
					//分析処理開始

					for(int i=0; i<12;i++){
						for(int j=0;j<a+50;j++){
							code[i][j] =0;
						}
					}

					for(int i=0;i<12;i++){
						code[i][a+49]=i;	//配列の末尾に音を示す値を代入
					}


					for(int j=0;j<notelist_hamo.size();j++){

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
								code[notelist_hamo.get(j).notenum()%12][i] += half_div;	
							}

							tick_e=notelist_hamo.get(j).offset() - (notelist_hamo.get(j).offset()/half_div)*half_div;
							code[notelist_hamo.get(j).notenum()%12][(int)notelist_hamo.get(j).onset()/half_div] += tick_e; 
						}

					}   */
					/*for(int j=0;j<10;j++){
					  System.out.println("````````"+j);
					  for(int i=0;i<12;i++){
					  System.out.println(code[i][j]); 	
					  }
					}
					System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~");
					*/


					//ソート
					/*
					code_hamo = new int[4][a+50];
					for(int j=0;j<a+3;j++){
						Arrays.sort(code, new MyComparator(j));
						for (int[] e : code) {
							//System.out.println("====");
							for (int i = 0; i < e.length; i++) {
								//System.out.println(e[i]);
							}
						}  
						//if(code[0][j]!=0){
						code_hamo[0][j]=code[0][a+49];
						code_hamo[1][j]=code[1][a+49];
						code_hamo[2][j]=code[2][a+49];
						code_hamo[3][j]=code[3][a+49];
						// }

					}  */
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
					/*
					for(int j=0;j<notelist.length;j++){
						for(int i=3;i>=0;i--){

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
							}

						}


					}
                    */

					/*for(int j=0;j<a+3;j++){ 
					  System.println(code_hamo[0][j],code_hamo[1][j],code_hamo[2][j],code_hamo[3][j])
					  }*/

					/*for(int i=0;i<12;i++){
					  System.out.println(code[i][5]);
					  }*/

					//ハモリ追加
					/*
					if(isHamo){
						for(int i=0;i<notelist.length;i++){
							if(plus){
								if(hamoData[i]==3 || hamoData[i]==4 ||hamoData[i]==5){
									System.out.println("hamoNote="+(mainNote[i]+hamoData[i]) 
											+ " , +" + hamoData[i]); 
									hamoNote[i] = mainNote[i]+hamoData[i];
									p.addNoteElement((int)mainOnset[i],(int)mainOffset[i],(int)(mainNote[i]+hamoData[i]),100,100); 
								}else if(hamoData[i]==0){

									hamoNote[i] = -1;
									System.out.println("hamoNote="+(mainNote[i]+hamoData[i]) 
											+ " , NoData");     
								}
							}else{
								if(hamoData[i]==-3 || hamoData[i]==-4 ||hamoData[i]==-5){

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
					} */
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
							//for (int j=0;j<onsetCheck.length; j++){
								if (mainOnset[i] == onsetCheck[i]  & mainOffset[i] == offsetCheck[i])
                                    tickMatch++;
									if(hamoNote[i] == notenumCheck[i]){
									    println(tickMatch+"gggggggg"+match);
										match++;
								}            
							//}

						}

						println("match="+ match + "per=" + match/mainLength);  
						try{
							File file = new File("./checkin/result_match_hmm.txt");
							FileWriter filewriter = new FileWriter(file,false);

							PrintWriter printwriter = new PrintWriter(new BufferedWriter(filewriter));
							printwriter.println("tickMatch="+tickMatch);
							printwriter.println("haha");
							printwriter.println("mainLength="+mainLength);
							printwriter.println("match="+ match + "per=" + (double)match/mainLength); 
							printwriter.println("草生えた");
							printwriter.println("noDataNum="+noDataNum);
							printwriter.println("except nodata per = "+(double)match/(mainLength-noDataNum));
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
					   


					prepare = new Preparation(mainNotelist.length);//check max and min
					prepare.checkPitch();
					maxNum=prepare.maxNotenum;
					minNum=prepare.minNotenum;
					aveNum=(int) ((prepare.maxNotenum+prepare.minNotenum)/2);
					println(prepare.maxNotenum+","+prepare.minNotenum+","+aveNum);

					SCCXMLWrapper.HeaderElement[] tempodoko = scc.getHeaderElementList();

					SCCUtils.transpose(scc2, key, true).toDataSet().toWrapper().toMIDIXML().writefileAsSMF("aaaa.mid"); 

				}
				catch(TransformerException e) {
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
				annotation = new AnnotationLyric();
				karaoke = new Karaoke();
				isKaraoke = true;
				isMenu = false;
                karaoke.checkEnd();
				//PlayMusic
				cmx.smfread(createInput("aaaa.mid"));
				cmx.playMusic();
			}


		}else if(isKaraoke){
			if(keyCode == UP){
				octave += 1;
			}else if(keyCode == DOWN){
				octave -= 1;
			} 
			//一定位置からの再生
			if(keyCode == ENTER){
				if(!isStop){
					cmx.stopMusic();
					isStop = true;
				}else if(isStop){
					cmx.playMusic();
					isStop = false;
				}
			}
			

		
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
       

	class AnnotationLyric {                          
		int[][] kanso = new int[10][2];  //  間奏[番号][0:始まり 1:終わり]
		int kansocount = 0;
		AnnotationLyric() {
			makeLyrics();
			readAnnotation();
		}
		void makeLyrics() {
			PrintWriter writer;
			Annotation[] lyrics = scc.getLyricList();

			writer = createWriter("Midi.xml");
			writer.println(scc);
			writer.flush();
			writer.close();
		}

		void readAnnotation() {
			BufferedReader reader = createReader("Midi.xml");
			BufferedReader reader1 = createReader("Midi.xml");
			String line1;
			String line;
			int kansostart = 0;

			boolean start = false;
			int startDisplay = 0;
			int endDisplay = 0;
			String[][] sentence = new String[10000][4];
			int wordcount = 0;
			try {
				line1 = reader1.readLine();
			} 
			catch (IOException e) {
				line1 = null;
			}
			while (true) {
				try {
					line = reader.readLine();
				} 
				catch (IOException e) {
					line = null;
				}
				try {
					line1 = reader1.readLine();
				} 
				catch (IOException e) {
					line1 = null;
				}
				if (line != null && line1 != null) {
					String[] g = splitTokens(line, " ");
					String[] g0 = splitTokens(line1, " ");
					String[] p = splitTokens(line, "<>");
					if (p.length > 1 && p[1].equals("lyric")) {
						p = split(g[2], "</lyric>");
						if (g0.length > 2) {
							String[]p1=split(g0[2], "</lyric>");
							String[] q = split(p[0], "間奏");
							if (p[0].equals("イントロ")) {
								kanso[kansocount][0] = kansostart;
								kanso[kansocount][1] = Integer.parseInt(g0[1]);          
								kansocount++;
							} else if (p[0].equals("エンディング")) {
								kanso[kansocount][0] = kansostart;
								kanso[kansocount][1] = Integer.parseInt(g0[1]);             
								kansocount++;
							} else if (p[0].length() != 0 && q[0].length() == 0) {
								kanso[kansocount][0] = kansostart;
								kanso[kansocount][1] = Integer.parseInt(g0[1]);             
								kansocount++;
							} else if (p[0].length() == 0 && p1[0].length() != 0) {
								if (wordcount == 0) {
									startDisplay=Integer.parseInt(g[1]);
								} else {
									endDisplay = Integer.parseInt(g[1]);
									lyrics.add(new Lyric(startDisplay, endDisplay, sentence, wordcount));
									sentence = new String[50][4];
									startDisplay = Integer.parseInt(g[1]);
									wordcount = 0;
								}
							} else if (p[0].length() == 0 && p1[0].length() == 0) {
								endDisplay = Integer.parseInt(g[1]);
								lyrics.add(new Lyric(startDisplay, endDisplay, sentence, wordcount));
								sentence = new String[50][4];
								wordcount = 0;
							} else {
								if (g.length >= 4) {
									q=split(g[3], "</lyric>");
									sentence[wordcount][3] = q[0];
								}
								sentence[wordcount][0] = g[1];
								sentence[wordcount][1] = g0[1];
								sentence[wordcount][2] = p[0];
								//println(wordcount+" : "+sentence[wordcount][2]+" : "+sentence[wordcount][3]);
								wordcount++;
							}
						} else {
							endDisplay = Integer.parseInt(g[1]);
							lyrics.add(new Lyric(startDisplay, endDisplay, sentence, wordcount));
							sentence = new String[50][4];
							wordcount = 0;
						}
					}
					if (g.length>1) kansostart = myint(g[1]);
				} else break;
			}
		}
	}
	class Karaoke {
		PGraphics wpg, rpg;
		PFont font = createFont("MS Gothic", 48);
		int lyriccount = 0;
		int minnote = 0;  
		int dilay = 0;

		int start, end;
		int dist;

		int div = 16 * division;

		int q=1;

		int endLine=300;
		int lineNum=30;
		int subst;
		int linemid=10;

		int startw=0;

		long[] lyricPosition = new long[lyrics.size()]; 

		int toLyricP=0;
		//int j =0;


        void displayVol(){
			line(50,height,50,height-2*mainvol);
		}
        
		
		
	      
		void checkEnd(){
			
			for(int i=0;i<lyrics.size();i++){
				Lyric lyricEnd = (Lyric) lyrics.get(i);
				println("ENDSS"+lyricEnd.getEndDisplay());
			    lyricPosition[i]=lyricEnd.getEndDisplay();
			}
              
		
		}
		
		void displayLyric( ) {
			int textstart = 200;
			int onsetwordw = 0;
			int onsetwordh = 0;
			int onsettextX = 0;
			int dilay = 0;
            
            

			/**白文字設定**/
			wpg = createGraphics(width, 250, JAVA2D);
			wpg.beginDraw();
			wpg.background(143, 195, 31);
			wpg.textFont(font);
			wpg.textAlign(LEFT, BASELINE);
			wpg.fill(255);
			/**赤文字設定**/
			rpg = createGraphics(width, 250, JAVA2D);
			rpg.beginDraw();
			rpg.background(143, 195, 31);
			rpg.textFont(font);
			rpg.textAlign(LEFT, BASELINE);
			rpg.fill(255, 0, 0);

            
			for (int i=0; i<2; i++) {
				if (lyriccount+i < lyrics.size()) {
					//println("==========lyriccount=======:"+lyriccount);
					Lyric lyric = (Lyric) lyrics.get(lyriccount+i);
					textstart = 200;
					for(int j=0;j < lyric.getWordCount ();j++) {
						textSize(50);
						wpg.textSize(50);
						wpg.text(lyric.getCharactor(j), textstart+(lyriccount+i)%2*70, (lyriccount+i)%2*100+100);
						rpg.textSize(50);
						rpg.text(lyric.getCharactor(j), textstart+(lyriccount+i)%2*70, (lyriccount+i)%2*100+100);//red
						float charWidth = textWidth(lyric.getCharactor(j));
						if (lyric.getKana(j) != null) {//2
							textSize(30);
							wpg.textSize(30);
							rpg.textSize(30);
							wpg.text(lyric.getKana(j), textstart+charWidth/2-textWidth(lyric.getKana(j))/2+(lyriccount+i)%2*70, (lyriccount+i)%2*100-60+100);
							rpg.text(lyric.getKana(j), textstart+charWidth/2-textWidth(lyric.getKana(j))/2+(lyriccount+i)%2*70, (lyriccount+i)%2*100-60+100);
						}
						//println(lyric.getStartDisplay()+","+lyric.getEndDisplay());
						if (lyric.getOnSet(j)<= position+dilay && position+dilay < lyric.getOffSet(j)) {
							onsetwordw = j;
							onsetwordh = (lyriccount+i)%2;
							onsettextX = textstart;
						}
						textSize(50);
						textstart+=textWidth(lyric.getCharactor(j));
						
					}
				   
					if (position+dilay>lyric.getEndDisplay()) {
                        //どこで歌詞が変わるかを記録
						//lyricPosition[lyriccount]=position;
						lyriccount++;
					
					
					}
					dist=lyric.getEndDisplay()-lyric.getStartDisplay();

					fill(0);

					stroke(0);

					if (div*q < position) {
						q++;
					}
					subst=(aveNum+key)-(lineNum/2+1);
					//println(subst);
					//println((position-(div*(q-1)))*width/div);                                                        
					for (int p=0; p<mainLength; p++) {
	                    //ピアノロール(メイン)
						if(isHamo){
							fill(230, 230, 230,100);
						}else{
							fill(255,78,107);
						}
						displayRect((int)((mainOnset[p] - div * (q-1))*width/div), (int)(linemid*(lineNum-((mainNote[p]+key)-subst)-1)), 
								(int)((mainOffset[p] -div*(q-1))*width/div), (int)(linemid*(lineNum-((mainNote[p]+key)-subst))));           ///
						//ピアノロール(はもり)
						if(hamoNote[p] != -1){
							if(isHamo){
								fill(255, 78, 107); 
								displayRect((int)((mainOnset[p] - div * (q-1))*width/div), (int)(linemid*(lineNum-((hamoNote[p]+key)-subst)-1)), 
										(int)((mainOffset[p] -div*(q-1))*width/div), (int)(linemid*(lineNum-((hamoNote[p]+key)-subst)))); ///
							}
						} 

					}
					if(checking){  
						for(int p=0;p<onsetCheck.length;p++){

							//精度チェック
							stroke(0);
							line(((onsetCheck[p] - div * (q-1))*width/div),(linemid * (lineNum - (notenumCheck[p] - subst)-1)),
									((mainOffset[p] -div*(q-1))*width/div),(linemid * (lineNum - (notenumCheck[p] - subst))));
						}
					}


					stroke(100);
					line((position-(div*(q-1)))*width/div, 0, (position-(div*(q-1)))*width/div, endLine);
                    // draw F0
					for (int p = 1; p < CheckAccuracy_hmm.dioM.ticks.size(); p++) {
						double x0 = (CheckAccuracy_hmm.dioM.ticks.get(p-1) - div * (q-1)) * width / div;
						double x1 = (CheckAccuracy_hmm.dioM.ticks.get(p) - div * (q-1)) * width / div;
						double y0 = linemid * (lineNum - ((CheckAccuracy_hmm.dioM.pitches.get(p-1) + octave*12) - subst)-2);
						double y1 = linemid * (lineNum - ((CheckAccuracy_hmm.dioM.pitches.get(p) + octave*12) - subst)-2);
						if (Double.isFinite(y0) && Double.isFinite(y1)) {
							line((int)x0 , (int)y0, (int)x1 , (int)y1);
						}
					}
                    //オクターブ変更
					rectMode(CENTER);
					noStroke();
					fill(255,100,150);
					rect( width-100,height-100,100,100 );
					fill(255);
				   
					if(octave >= 0){
						text( "+"+ octave,width-100,height-70);
					}else if(octave < 0){
						text( octave,width-100,height-70); 
					}
                    textSize(22);
					fill(255);
					text("オクターブ",width-100,height-120);

					//再生位置

					fill(255,200,100);
					rect(100,height-100,100,100);
					fill(255);
					text("はじめから",100,height-100);

					fill(150,50,200);
                    rect(250,height-100,100,100);
					rect(400,height-100,100,100);
					fill(255);
                    text("巻き戻し",250,height-100);
                    text("早送り",400,height-100);
				}
			}
			wpg.endDraw();
			rpg.endDraw();
			image(wpg, 0, 300);
			if (lyriccount<lyrics.size()) {
				paintLyric(position+dilay, onsetwordw, onsetwordh, onsettextX);
			}
			textSize(20);
			textAlign(CENTER);

			startw = onsetwordw;
		}

		void paintLyric(long position, int wordnum, int col, int onsettextX) {
			Lyric lyric = (Lyric) lyrics.get(lyriccount);
			textSize(50);
			float charX = (float)((int)(position) - lyric.getOnSet(wordnum))/(float)(lyric.getOffSet(wordnum) - lyric.getOnSet(wordnum))*(textWidth(lyric.getCharactor(wordnum))-lyric.getCharactor(wordnum).length()*20);
			copy(rpg, 200+col*70, col*100+100-40-5-40, onsettextX-200+(int)(charX), 100, 200+col*70, col*100+height-370-5-40, onsettextX-200+(int)(charX), 100);
		}

		/*void displayMoveLine() {
		  line(position-div*(q-1),0,position-div*(q-1),endLine);
		  }*/

		/////////////////////////////////////////////////////////////////////////

		void displayToneLine() {
			stroke(230); //color of line
			
			for (int i=0; i<=lineNum; i++) {  
				line(0, endLine/lineNum*i, width, endLine/lineNum*i);
			}
		} 

		void displayRect(int x1, int y1, int x2, int y2 ) {
			noStroke();
			rectMode(CORNERS);
			//fill(255, 153, 0);
			rect(x1, y1, x2, y2);
		}
	}
	class Lyric {
		int startDisplay;  //  表示開始tick
		int endDisplay;  //  表示終了tick
		String[] charactor;  //  文字
		int[] onset;  // 発音時間
		int[] offset;  //  消音時間
		String[] kana;  //   ルビ
		int wordcount;  //文字数

		Lyric(int startDisplay, int endDisplay, String[][] charactor, int wordcount) {
			this.startDisplay = startDisplay;
			this.endDisplay = endDisplay;
			this.wordcount = wordcount;
			this.charactor = new String[wordcount];
			this.kana = new String[wordcount];
			onset = new int[wordcount];
			offset = new int[wordcount];
			for (int i=0; i<wordcount; i++) {
				onset[i] = Integer.parseInt(charactor[i][0]);
				offset[i] = Integer.parseInt(charactor[i][1]);
				this.charactor[i] = charactor[i][2];
				kana[i] = charactor[i][3];
			}
		}

		void deb() {
			//println(startDisplay  + " : " + endDisplay);
			/**** デバッグ歌詞表示 ****/
			for (int i=0; i < wordcount; i++) {
				println(getCharactor(i)+"("+getKana(i)+")" + " : " + getOnSet(i) + " : " + getOffSet(i));
			}
			/************************/
		}

		int getStartDisplay() {
			return startDisplay;
		}

		int getEndDisplay() {
			return endDisplay;
		}

		String getCharactor(int count) {
			if (charactor.length < 1) return "";
			return charactor[count];
		}

		String getKana(int count) {
			if (kana[count]==null) return "";
			return kana[count];
		}

		int getOnSet(int count) {
			if (onset.length < 1)return 0;
			return (int)(onset[count]);
		}

		int getOffSet(int count) {
			if (onset.length < 1)return 0;
			return (int)(offset[count]);
		}

		int getWordCount() {
			return wordcount;
		}
	}
	class Menu {
        PFont font = createFont("MS Gothic", 48); 
		void displayAll() {
			displayBackGround();
			displayText();
			choosemusic();
		}

		void displayBackGround() {
			background(100, 200, 255);
		}
		void displayText() {
			fill(50,50,255);
			textSize(50);
			textAlign(CENTER);                              
			rectMode(CENTER);
			//text("Press ENTER!", width/2, height/2);
			
			noStroke();
			
			rect(width/2,height*(musicnum+1)/11-20,width/2,height/12);

			fill(255);
			textFont(font);

            //楽曲選択
            text("RPG",width/2,height/11); 
            text("only my railgun",width/2,height*2/11);   
			text("にじいろ",width/2,height*3/11); 
 			text("オリオンをなぞる",width/2,height*4/11);
 			text("TSUNAMI",width/2,height*5/11);
 			text("明日への扉",width/2,height*6/11);
 			text("3月9日",width/2,height*7/11);
 			text("奏",width/2,height*8/11);
 			text("青いベンチ",width/2,height*9/11);
 			text("愛唄",width/2,height*10/11);

			//ボリューム設定///////////////////////////////////////

			textSize(30);
			
			rectMode(CENTER);
 
			fill(50,50,255); 
			if(mainvol==127){
				rect(100,550,105,50);   
			}else if (mainvol==64){
				rect(100,600,105,50);
			}else if(mainvol==20){
				rect(100,650,105,50);
			}
            
            fill(255,230,230);
			rect(100,550,100,44);
			rect(100,600,100,44); 
			rect(100,650,100,44); 


            fill(120,120,255);   

			if( abs(mouseX-100)<=50 & abs(mouseY-550)<=22){
				rect(100,550,100,44);    
			}else if(abs(mouseX-100)<=50 & abs(mouseY-600)<=22){
				rect(100,600,100,44);    
			}else if(abs(mouseX-100)<=50 & abs(mouseY-650)<=22){
				rect(100,650,100,44);    
			} 
            
			fill(50);
			text("小",100,660);
			text("大",100,560);        
			text("中",100,610);     
			textSize(20);
			text("メインメロディ大きさ",100,500);    
	
            //上下ハモリ選択/////////////////////////////////////

            fill(50,50,255); 
			if(plus == true){
				rect(100,400,105,50);   
			}else if (plus == false){
				rect(100,450,105,50);
			}
            
			fill(255,230,230);
			rect(100,400,100,44);
			rect(100,450,100,44); 
		   
			fill(120,120,255); 
			if( abs(mouseX-100)<=50 & abs(mouseY-400)<=22){
				rect(100,400,100,44);    
			}else if(abs(mouseX-100)<=50 & abs(mouseY-450)<=22){
				rect(100,450,100,44);    
			}

			fill(50);
			text("上",100,400);
			text("下",100,450);            
			textSize(20);
			text("ハモリ上下",100,350);

			//if( abs(mouseX-100)<=50 & abs(mouseY-550)<=22){
			//text("YEAH",500,500);}
            
			//キー選択//////////////////////////////////////////////
			textSize(30);
			text("キー",width-140,520);
			if(key>=0){
				text("+"+key,width-90,520);
			}else{
				text(key,width-90,520);
			}            
			fill(255,230,230);
			textSize(30);
			rect(width-120,550,100,44);
			rect(width-120,600,100,44);

			fill(120,120,255); 
			if( abs(mouseX-(width-120))<=50 & abs(mouseY-550)<=22){
				rect(width-120,550,100,44);    
			}else if(abs(mouseX-(width-120))<=50 & abs(mouseY-600)<=22){
				rect(width-120,600,100,44);   
			} 

			fill(50);
			text("+",width-120,560);
			text("-",width-120,610);

			//普通のカラオケ?ハモリ練習?////////////////////////////
            textSize(25);
			text("モード選択",width-120,370);

            fill(50,50,255); 
			if(isHamo == false){
				rect(width-120,400,105,50);    
			}else if (isHamo == true){
				rect(width-120,450,105,50);
			} 

			fill(255,230,230);
			rect(width-120,400,100,44);
			rect(width-120,450,100,44);
			
			fill(120,120,255);
			if( abs(mouseX-(width-120))<=50 & abs(mouseY-400)<=22){
				rect(width-120,400,100,44);    
			}else if(abs(mouseX-(width-120))<=50 & abs(mouseY-450)<=22){
				rect(width-120,450,100,44);   
			} 
			
			fill(50);
            textSize(18);
			text("主旋律練習",width-120,410);
			text("ハモリ練習",width-120,460);   
		
		}

        void choosemusic(){           
		
		}
	
	}

	public void mouseClicked() {
		if(isMenu){
			//メイン音量
			if( abs(mouseX-100)<=50 & abs(mouseY-550)<=22){
				mainvol=127;
			}else if(abs(mouseX-100)<=50 & abs(mouseY-600)<=22){
				mainvol=64;
			}else if(abs(mouseX-100)<=50 & abs(mouseY-650)<=22){
				mainvol=20;
			}
            //ハモリ上下
			if(abs(mouseX-100)<=50 & abs(mouseY-400)<=22){
				plus = true;
			}else if(abs(mouseX-100)<=50 & abs(mouseY-450)<=22){
				plus = false;
			}
            //キー
			if(abs(mouseX-(width-100))<=50 & abs(mouseY-550)<=22){
				key ++;
			}else if(abs(mouseX-(width-100))<=50 & abs(mouseY-600)<=22){
				key --;
			}
			//モード選択
            if(abs(mouseX-(width-100))<=50 & abs(mouseY-400)<=22){
				isHamo = false;
			}else if(abs(mouseX-(width-100))<=50 & abs(mouseY-450)<=22){
				isHamo = true;
			}  

		
		}else if(isKaraoke){
            //初めから
			
			if(abs(mouseX-100)<=50 & abs(mouseY-(height-100))<=50){
				cmx.stopMusic();
				cmx.setTickPosition(0);
				cmx.playMusic();
				karaoke.q=1;
				karaoke.lyriccount=0;
			   
			}else if(abs(mouseX-250)<=50 & abs(mouseY-(height-100))<=50){
			    
				if(karaoke.q>1){
				//println(position-karaoke.div+","+karaoke.q+","+karaoke.lyriccount);
				cmx.stopMusic();
				cmx.setTickPosition(position - karaoke.div);
				cmx.playMusic();
				karaoke.q -= 1; }
				for (int i=0;i<lyrics.size()-1;i++){
					if(position-karaoke.div > karaoke.lyricPosition[i] & 
							position - karaoke.div < karaoke.lyricPosition[i+1]){
						nowLyric = i;
					}
				}
				
				karaoke.lyriccount = nowLyric; 
				
				
			
			}else if(abs(mouseX-400)<=50 & abs(mouseY-(height-100))<=50){
				cmx.stopMusic();
				cmx.setTickPosition(position + karaoke.div);
				cmx.playMusic();
				karaoke.q += 1;
				for (int i=0;i<lyrics.size()-1;i++){
					if(position + karaoke.div > karaoke.lyricPosition[i] & 
							position + karaoke.div < karaoke.lyricPosition[i+1]){
						nowLyric = i;
					}
				}    
			    karaoke.lyriccount = nowLyric;
			}
		}
	}


	class Preparation {
		int maxNotenum=0;
		int minNotenum=100;
		int mainLength;
		Preparation(int mainLength) {
			this.mainLength=mainLength;
		}
		void checkPitch() {
			for (int i=0; i<mainLength; i++) {
				if (maxNotenum<mainNote[i]) {  
					maxNotenum=(int)mainNote[i];
				} else if (minNotenum>mainNote[i]) {  
					minNotenum=(int)mainNote[i];
				}
			}
		}
	}


	public static void main(String[] args) {
		PApplet.main(new String[] { 
			"CheckAccuracy_hmm"
		}
		);
	}
}
class Diocomp extends SPModule {
	private int fs; //サンプリングレート[Hz]
	private int x_length; //1周期あたりのデータ数
	private int f0_length;  //F0のサンプル数
	private double frameperiod = 5.0; //フレームレート
	private Pointer f0; //推定されたF0周波数
	private Pointer time_axis;//
	public final long offset = Native.getNativeSize(Double.TYPE);//オフセット値
	World diolib; 

	public int cnt = 0;

	List<Long> microseconds;
	List<Long> ticks;
	List<Double> pitches;

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
		microseconds = new ArrayList<Long>();
		ticks = new ArrayList<Long>();
		pitches = new ArrayList<Double>();
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
		//    double mean_f0 = 0;
		double output[] = new double[this.f0_length];
		for(int i=0;i<this.f0_length;i++){
			//      mean_f0 += this.f0.getDouble(i*this.offset);
			output[i] = this.f0.getDouble(i*this.offset);
		}
		DoubleArray output2 = MathUtils.createDoubleArray(output);
		dest[0].add(output2);
		double median_f0 = Operations.median(output2);

		//推定されたF0周波数の平均を出力
		//mean_f0 = mean_f0 / this.f0_length;
		//System.out.printf("meanF0[%d]: %f\n", this.cnt, Math.log(mean_f0));

		//calc hz->notenum

		pitches.add((Math.log(median_f0/440) / Math.log(2)*12 )+69  + 12);
		microseconds.add(Hamo6.cmx.getMicrosecondPosition());
		ticks.add(Hamo6.cmx.getTickPosition());

		System.out.println(ticks.get(ticks.size()-1) + "   " + pitches.get(pitches.size()-1));
		/*System.out.printf("notenum=%f,f0=%f\n",GlobalVariable.notenum,mean_f0);*/

		this.cnt++;
	}
	public Class[] getInputClasses() {
		return new Class[]{ DoubleArray.class };
	}
	public Class[] getOutputClasses() {
		return new Class[]{ DoubleArray.class };
	}


}

class Jahmm {

	public static final int DIM = 12;
    public static int[] states;

    public static ArrayList<Integer> hamohamo= new ArrayList<Integer>();  

	private static ObservationVector pack(double[] values) {
		return OpdfHarmony.normalize(new ObservationVector(values));
	}

	public static double code_all[][];

	/* 遷移確率の元となる配列。これを隠れ状態（ハモリパートの音高）に合わせて
	   ローテーションさせて、各状態の遷移確率を作る */
	private static double[] TRANS_PROB_BASE = {0.006666667, 0.033333333,0.066666667,0.2,0.266666667,0.2,0.1,0.066666667,0.266666667,0.02,0.006666667,0.006666667};
	//{0.1, 0.0, 0.0, 0.2, 0.3, 0.3, 0.1, 0.0, 0.0, 0.0, 0.0, 0.0};

	/* ハ長調の際の事前確率。ハ長調のダイアトニックスケール以外の音の
	   確率が低くなるように手動で設定。
	   実際には、MIDIファイルから調の情報を取得して決める必要あり */
	private static double[] prior =
	{0.13, 0.03, 0.12, 0.03, 0.12, 0.12, 0.03, 0.12, 0.03, 0.12, 0.03, 0.12};

	public static void main(int[] mainNote,double[][] code,int mainLength,int[] keyChangeNum,String[][] keyChange) {


        code_all = new double[code[0].length][code.length];
		for (int i = 0; i < code.length; i++) {
			for (int j = 0; j < code[i].length; j++) {
				//System.out.print(code[i][j] + ",");
				code_all[j][i] = code[i][j];
			}
			//System.out.println();
		}

		/* 1つ目のHMMを定義。1つ目のHMMは主旋律の音高（0〜11）を出力する。
		   各状態に対応する各音高の出力確率は、上の配列を元に決定。
		   ここでは、とりあえず初期確率と遷移確率は等確率 */
		Hmm<ObservationInteger> hmm1 =
			new Hmm<ObservationInteger>(DIM, new OpdfIntegerFactory(DIM));
		for (int i = 0; i < DIM; i++)
			hmm1.setPi(i, 1.0/DIM);   //初期確率
		for (int i = 0; i < DIM; i++)
		  for (int j = 0; j < DIM; j++)
		  hmm1.setAij(i, j, 1.0/DIM); //遷移確率*/
		//遷移確率(MIDより) 
		/*
		hmm1.setAij(0,0,0.32142857142857145);
		hmm1.setAij(0,1,0.06493506493506493);
		hmm1.setAij(0,2,0.05194805194805195);
		hmm1.setAij(0,3,0.07792207792207792);
		hmm1.setAij(0,4,0.0);
		hmm1.setAij(0,5,0.05844155844155844);
		hmm1.setAij(0,6,0.0);
		hmm1.setAij(0,7,0.06818181818181818);
		hmm1.setAij(0,8,0.06493506493506493);
		hmm1.setAij(0,9,0.0);
		hmm1.setAij(0,10,0.2857142857142857);
		hmm1.setAij(0,11,0.006493506493506494);
		hmm1.setAij(1,0,0.03732303732303732);
		hmm1.setAij(1,1,0.2651222651222651);
		hmm1.setAij(1,2,0.07207207207207207);
		hmm1.setAij(1,3,0.21492921492921493);
		hmm1.setAij(1,4,0.04504504504504504);
		hmm1.setAij(1,5,0.010296010296010296);
		hmm1.setAij(1,6,0.05791505791505792);
		hmm1.setAij(1,7,0.0);
		hmm1.setAij(1,8,0.01673101673101673);
		hmm1.setAij(1,9,0.023166023166023165);
		hmm1.setAij(1,10,0.06563706563706563);
		hmm1.setAij(1,11,0.19176319176319176);
		hmm1.setAij(2,0,0.04816955684007707);
		hmm1.setAij(2,1,0.11368015414258188);
		hmm1.setAij(2,2,0.4624277456647399);
		hmm1.setAij(2,3,0.046242774566473986);
		hmm1.setAij(2,4,0.11368015414258188);
		hmm1.setAij(2,5,0.0019267822736030828);
		hmm1.setAij(2,6,0.08863198458574181);
		hmm1.setAij(2,7,0.0019267822736030828);
		hmm1.setAij(2,8,0.0);
		hmm1.setAij(2,9,0.057803468208092484);
		hmm1.setAij(2,10,0.036608863198458574);
		hmm1.setAij(2,11,0.028901734104046242);
		hmm1.setAij(3,0,0.03836317135549872);
		hmm1.setAij(3,1,0.17902813299232737);
		hmm1.setAij(3,2,0.06649616368286446);
		hmm1.setAij(3,3,0.2877237851662404);
		hmm1.setAij(3,4,0.014066496163682864);
		hmm1.setAij(3,5,0.21355498721227623);
		hmm1.setAij(3,6,0.07800511508951406);
		hmm1.setAij(3,7,0.03836317135549872);
		hmm1.setAij(3,8,0.0370843989769821);
		hmm1.setAij(3,9,0.0025575447570332483);
		hmm1.setAij(3,10,0.017902813299232736);
		hmm1.setAij(3,11,0.026854219948849106);
		hmm1.setAij(4,0,0.0);
		hmm1.setAij(4,1,0.12979351032448377);
		hmm1.setAij(4,2,0.26548672566371684);
		hmm1.setAij(4,3,0.06784660766961652);
		hmm1.setAij(4,4,0.19174041297935104);
		hmm1.setAij(4,5,0.014749262536873156);
		hmm1.setAij(4,6,0.15339233038348082);
		hmm1.setAij(4,7,0.061946902654867256);
		hmm1.setAij(4,8,0.0058997050147492625);
		hmm1.setAij(4,9,0.06784660766961652);
		hmm1.setAij(4,10,0.0);
		hmm1.setAij(4,11,0.04129793510324484);
		hmm1.setAij(5,0,0.01981981981981982);
		hmm1.setAij(5,1,0.021621621621621623);
		hmm1.setAij(5,2,0.0036036036036036037);
		hmm1.setAij(5,3,0.34954954954954953);
		hmm1.setAij(5,4,0.0);
		hmm1.setAij(5,5,0.25765765765765763);
		hmm1.setAij(5,6,0.08828828828828829);
		hmm1.setAij(5,7,0.10990990990990991);
		hmm1.setAij(5,8,0.0918918918918919);
		hmm1.setAij(5,9,0.0);
		hmm1.setAij(5,10,0.05765765765765766);
		hmm1.setAij(5,11,0.0);
		hmm1.setAij(6,0,0.0);
		hmm1.setAij(6,1,0.10619469026548672);
		hmm1.setAij(6,2,0.01991150442477876);
		hmm1.setAij(6,3,0.04314159292035398);
		hmm1.setAij(6,4,0.13716814159292035);
		hmm1.setAij(6,5,0.051991150442477874);
		hmm1.setAij(6,6,0.375);
		hmm1.setAij(6,7,0.05420353982300885);
		hmm1.setAij(6,8,0.11393805309734513);
		hmm1.setAij(6,9,0.061946902654867256);
		hmm1.setAij(6,10,0.022123893805309734);
		hmm1.setAij(6,11,0.014380530973451327);
		hmm1.setAij(7,0,0.04377880184331797);
		hmm1.setAij(7,1,0.002304147465437788);
		hmm1.setAij(7,2,0.03225806451612903);
		hmm1.setAij(7,3,0.018433179723502304);
		hmm1.setAij(7,4,0.01152073732718894);
		hmm1.setAij(7,5,0.20506912442396313);
		hmm1.setAij(7,6,0.16129032258064516);
		hmm1.setAij(7,7,0.2626728110599078);
		hmm1.setAij(7,8,0.14285714285714285);
		hmm1.setAij(7,9,0.06221198156682028);
		hmm1.setAij(7,10,0.055299539170506916);
		hmm1.setAij(7,11,0.002304147465437788);
		hmm1.setAij(8,0,0.004724409448818898);
		hmm1.setAij(8,1,0.026771653543307086);
		hmm1.setAij(8,2,0.0);
		hmm1.setAij(8,3,0.07244094488188976);
		hmm1.setAij(8,4,0.0015748031496062992);
		hmm1.setAij(8,5,0.10708661417322834);
		hmm1.setAij(8,6,0.2283464566929134);
		hmm1.setAij(8,7,0.06771653543307087);
		hmm1.setAij(8,8,0.2078740157480315);
		hmm1.setAij(8,9,0.029921259842519685);
		hmm1.setAij(8,10,0.2031496062992126);
		hmm1.setAij(8,11,0.050393700787401574);
		hmm1.setAij(9,0,0.0);
		hmm1.setAij(9,1,0.0611439842209073);
		hmm1.setAij(9,2,0.04930966469428008);
		hmm1.setAij(9,3,0.0);
		hmm1.setAij(9,4,0.05719921104536489);
		hmm1.setAij(9,5,0.0);
		hmm1.setAij(9,6,0.07297830374753451);
		hmm1.setAij(9,7,0.11439842209072978);
		hmm1.setAij(9,8,0.07100591715976332);
		hmm1.setAij(9,9,0.378698224852071);
		hmm1.setAij(9,10,0.0);
		hmm1.setAij(9,11,0.1952662721893491);
		hmm1.setAij(10,0,0.15752212389380532);
		hmm1.setAij(10,1,0.05309734513274336);
		hmm1.setAij(10,2,0.0035398230088495575);
		hmm1.setAij(10,3,0.047787610619469026);
		hmm1.setAij(10,4,0.0);
		hmm1.setAij(10,5,0.01592920353982301);
		hmm1.setAij(10,6,0.035398230088495575);
		hmm1.setAij(10,7,0.06548672566371681);
		hmm1.setAij(10,8,0.29557522123893804);
		hmm1.setAij(10,9,0.007079646017699115);
		hmm1.setAij(10,10,0.24601769911504426);
		hmm1.setAij(10,11,0.07256637168141593);
		hmm1.setAij(11,0,0.0038314176245210726);
		hmm1.setAij(11,1,0.22796934865900384);
		hmm1.setAij(11,2,0.007662835249042145);
		hmm1.setAij(11,3,0.01532567049808429);
		hmm1.setAij(11,4,0.019157088122605363);
		hmm1.setAij(11,5,0.0);
		hmm1.setAij(11,6,0.07471264367816093);
		hmm1.setAij(11,7,0.0);
		hmm1.setAij(11,8,0.038314176245210725);
		hmm1.setAij(11,9,0.25862068965517243);
		hmm1.setAij(11,10,0.09386973180076628);
		hmm1.setAij(11,11,0.26053639846743293);   */

		for (int i = 0; i < DIM; i++) {
			double[] opdfvalues = new double[DIM];
			for (int j = 0; j < DIM; j++) 
				opdfvalues[j] = TRANS_PROB_BASE[(i + j) % DIM];
			hmm1.setOpdf(i, new OpdfInteger(opdfvalues));  //出力確率
		}

		/* 2つ目のHMMを定義。2つ目のHMMは、伴奏における各音高の出現の度合いを
		   表す12次元のベクトルを出力する。
		   各状態に対応する（ハモリパートの）音高と短2度か長2度でぶつかる音の
		   出現の度合いが0に近いほど出力確率が高くなるようにする。
		   初期確率と遷移確率はプログラム上で無視されるので、設定を省略 */
		Hmm<ObservationVector> hmm2 =
			new Hmm<ObservationVector>(DIM, new OpdfMultiGaussianFactory(DIM));    
		for (int i = 0; i < DIM; i++)                                  
			hmm2.setOpdf(i, new OpdfHarmony(DIM, i, 0.1, 1.0));

		/* 主旋律の方の観測系列を定義 */
		
		/*oseq1.add(new ObservationInteger(0));
		oseq1.add(new ObservationInteger(4));
		oseq1.add(new ObservationInteger(7));
		oseq1.add(new ObservationInteger(0)); */
		
		for(int j=0;j<keyChangeNum.length-1;j++){
             System.out.println(keyChangeNum.length+"HAAAAAAAAAAAAAAAAAAAAA"); 
			List<ObservationInteger> oseq1 = new ArrayList<ObservationInteger>();
			for(int i=keyChangeNum[j];i<keyChangeNum[j+1];i++){
				oseq1.add(new ObservationInteger(mainNote[i]%12));
			}

			/*for (int i = 0; i < code_all.length; i++) {
			  for (int j = 0; j < code_all[i].length; j++) {
			  System.out.print(code_all[i][j] + ",");
			  }
			  System.out.println();
			  }*/

			/* 伴奏の方の観測時系列を定義 */
			List<ObservationVector> oseq2 = new ArrayList<ObservationVector>();
			/*oseq2.add(pack(new double[]{94, 0, 10, 1, 40, 10, 60, 20, 40, 30, 10, 10}));
			  oseq2.add(pack(new double[]{23, 10, 78, 6, 40, 40, 20, 30, 10, 50, 15, 4}));
			  ;oseq2.add(pack(new double[]{64, 0, 29, 40, 10, 20, 30, 60, 10, 1, 30, 50}));
			  oseq2.add(pack(new double[]{76, 10, 20, 50, 30, 40, 20, 10, 7, 4, 5, 20}));*/
			for(int i=keyChangeNum[j];i<keyChangeNum[j+1];i++){
				oseq2.add(pack(code_all[i]));
			}

			System.out.println("wwwwwwwwwwwwwwwww"+oseq1.size()+":"+oseq2.size());
			/* 観測時系列を画面表示 */
			for (int i = 0; i < oseq1.size(); i++) {
				//System.out.println("oseq1: "+oseq1.get(i)+"\toseq2: "+oseq2.get(i));
			}

			/* Viterbiアルゴリズムを実行 */ 
			
			CompoundHMMsViterbiCalculator viterbi =
				new CompoundHMMsViterbiCalculator(oseq1, hmm1, 1.0, oseq2, hmm2, 3.0,
						prior); 
			/* Viterbiの結果の獲得 */
			states = viterbi.stateSequence();

			/* Viterbiの結果を画面出力 */
			for (int i = 0; i < states.length; i++){
			  System.out.println(states[i]);
              hamohamo.add(states[i]);
			}  

			System.out.println("==============");

		}

		/* Viterbiの結果の獲得 */
		//states = viterbi.stateSequence();

		/* Viterbiの結果を画面出力 */
		/*for (int i = 0; i < states.length; i++)
			System.out.println(states[i]);  */



	
	}
} 
