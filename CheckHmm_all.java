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
    public static boolean C =false;   

}



public class CheckHmm_all extends PApplet{


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
	ArrayList<SCCXMLWrapper.Note> notelist_hamo = new ArrayList<SCCXMLWrapper.Note>(); 
	ArrayList<Integer> notenum_draw = new ArrayList<Integer>();
	//ArrayListの宣言,ノートナンバーの配列
	ArrayList<SCCXMLWrapper.HeaderElement> tempodoko = new ArrayList<SCCXMLWrapper.HeaderElement>();    

	boolean isKaraoke = false;
	boolean isMenu = true;
	boolean plus = true;
	boolean isEnter = true;
	boolean isHamo = true;
    boolean isStop = false;
    boolean noCode=false;
    boolean exsistCode =false;
                        
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
    int[] hamoNote; 
    
    int mainLength; 
	int keyChangeCount=0;
    int countLoop=0;
    
	int[] keyChangeNum;
	int[] keyChange;
	int countLoop2=1;

    //ArrayList<SCCXMLWrapper.Note> notelist_comp = new ArrayList<SCCXMLWrapper.Note>();
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
				File fileB = new File("./checkin/hmm_all.txt");
				FileWriter filewriter = new FileWriter(fileB,false);

				PrintWriter printwriter = new PrintWriter(new BufferedWriter(filewriter));
			   	printwriter.close();
			}catch(IOException e){
				System.out.println(e);
			}                           
			//////////////////////////////////////////////////////////////
			//実行処理↓
			for(File file : files){
				GlobalVariable.C=false;
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
                GlobalVariable.C=true;
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
                ArrayList<SCCXMLWrapper.Note> notelist_comp = new ArrayList<SCCXMLWrapper.Note>();  
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
				if(GlobalVariable.C){
                for(int i=0;i<code.length;i++){
                    for(int j=0;j<code[0].length;j++){
					//println("code:"+code[i][j]);
					}
				}  }
				//println(mainNote);
				SCCXMLWrapper.HeaderElement[] musicKey = scc.getHeaderElementList();   
				keyChangeCount=0;
				exsistCode=false;
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
						exsistCode=true;
						//tempo_tick[temposoeji][1]=(int)tempodoko[i].time();                  //      
						//temposoeji++;
						//System.out.println("マッチしません");
					}//else if(){ 
                   
					
				} 
				if(exsistCode==false){
					keyChangeCount=1;    //noCode=true;
				}   
				String[] keyChangeMode = new String[keyChangeCount+1];
				keyChangeNum = new int[keyChangeCount+1];
				keyChange = new int[keyChangeCount+1];
				countLoop=0;
				for(int i=0; i<musicKey.length;i++){
					if(musicKey[i].name().equals("KEY")){
						noCode=false;
						KeySymbol keyS = KeySymbol.parse(musicKey[i].content());
						NoteSymbol root = keyS.root();
						KeySymbol.Mode mode = keyS.mode();
						println("countLoop"+countLoop);
						keyChangeNum[countLoop]=(int)(musicKey[i].time());
						keyChange[countLoop]=root.number();
						keyChangeMode[countLoop]=mode.toString();
						countLoop++;

					}
				} 
                if(exsistCode==false){
					noCode=true;
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
				if(noCode){
					keyChangeMode[0]="MAJ";
					keyChange[0]=0;
					keyChangeNum[1]=mainLength;
				}else{
					keyChangeNum[countLoop]=mainLength;  
				}

				for(int i=0;i<keyChangeNum.length;i++){
                    if(GlobalVariable.C){
					println("Change"+keyChangeNum[i]); }
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
					/*System.out.println(before+","+current);
					  System.out.println(before%12+","+current%12);
					  System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"); */
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
						if(GlobalVariable.C){System.out.println("trans:"+transition[i][j]+"transum:"+transum[i]+
						  	"per:"+(double)transition[i][j] / (double)transum[i]);    }
						aij[i][j] = (double)transition[i][j] / (double)transum[i];
					}
				}
				for(int i=0;i<mainNote.length;i++) {
					println("mainNote"+"["+i+"]="+mainNote[i]);
				}
				println("mainLength="+mainLength);
				for(int i=0;i<keyChangeNum.length;i++) {   
					println("keyChangeNum"+"["+i+"]="+keyChangeNum[i]);     
				}
				for(int i=0;i<keyChange.length;i++) {   
					println("keyChange"+"["+i+"]="+keyChange[i]); 
				}
				for(int i=0;i<keyChangeMode.length;i++) {   
					println("keyChangeMode"+"["+i+"]="+keyChangeMode[i]);
				}  
				//hmm
				hmmJ.main(mainNote,code,mainLength,keyChangeNum,keyChange,keyChangeMode,plus,aij);              if(GlobalVariable.C){
				try{
						File fileB = new File("./checkin/hmm_08.txt");
						FileWriter filewriter = new FileWriter(fileB,false);

						PrintWriter printwriter = new PrintWriter(new BufferedWriter(filewriter));
						//printwriter.println("song name:"+file);
						for(int i=0;i<mainNote.length;i++) {
							printwriter.println("mainNote"+"["+i+"]="+mainNote[i]);
						}
						printwriter.println("mainLength="+mainLength);
				        for(int i=0;i<keyChangeNum.length;i++) {   
						printwriter.println("keyChangeNum"+"["+i+"]="+keyChangeNum[i]);     
						}
						for(int i=0;i<keyChange.length;i++) {   
						printwriter.println("keyChange"+"["+i+"]="+keyChange[i]); 
						}
						for(int i=0;i<keyChangeMode.length;i++) {   
							printwriter.println("keyChangeMode"+"["+i+"]="+keyChangeMode[i]);
						}

						printwriter.close();
					}catch(IOException e){
						System.out.println(e);
					}     
				}
		   
                println("hamohamo:");  
				for(int i=0;i<mainLength;i++){
				println("["+i+"]:"+hmmJ.hamohamo.get(i));   }
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
					tickMatch=0;
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
						File fileB = new File("./checkin/hmm_all.txt");
						FileWriter filewriter = new FileWriter(fileB,true);

						PrintWriter printwriter = new PrintWriter(new BufferedWriter(filewriter));
						printwriter.println("song name:"+file);
						printwriter.print("tickMatch="+tickMatch);
						printwriter.print("mainLength="+mainLength);
						printwriter.println("match="+ match + "per=" + (double)match/mainLength); 
					   /* printwriter.println("草生えた");
						printwriter.println("noDataNum="+noDataNum);
						printwriter.println("except nodata per = "+(double)match/(mainLength-noDataNum));   */
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




	public static void main(String[] args) {
		PApplet.main(new String[] { 
			"CheckHmm_all"
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
	private static double[] TRANS_PROB_BASE_PLUS ={0.014285714,0.028571429,0.064285714,0.214285714,0.285714286,0.214285714,0.071428571,0,0,0,0,0};  //->ver2
	//{0.014285714,0.028571429,0.064285714,0.214285714,0.285714286,0.214285714,0.071428571,0.035714286,0.28571429,0.021428571,0.014285714,0.007142857}; //->ver1

	//{0.006666667, 0.033333333,0.066666667,0.2,0.266666667,0.2,0.1,0.066666667,0.066666667,0.02,0.006666667,0.006666667};   ->gomi
	private static double[] TRANS_PROB_BASE_MINUS;
	//{0.1, 0.0, 0.0, 0.2, 0.3, 0.3, 0.1, 0.0, 0.0, 0.0, 0.0, 0.0};

	/* ハ長調の際の事前確率。ハ長調のダイアトニックスケール以外の音の
	   確率が低くなるように手動で設定。
	   実際には、MIDIファイルから調の情報を取得して決める必要あり */
	private static int prior_code;
	private static double[] PRIOR_MAJOR={0.15,0.03,0.1,0.03,0.15,0.1,0.03,0.15,0.03,0.1,0.03,0.1};
		//{0.13,0.03,0.12,0.03,0.12,0.12,0.03,0.12,0.03,0.12,0.03,0.12};
	private static double[] PRIOR_MINOR={0.1,0.03,0.15,0.1,0.03,0.15,0.03,0.1,0.03,0.2,0.15,0.03};
		//{0.13,0.03,0.12,0.12,0.03,0.12,0.03,0.12,0.12,0.03,0.12,0.03};
	private static double[] prior={0.13, 0.03, 0.12, 0.03, 0.12, 0.12, 0.03, 0.12, 0.03, 0.12, 0.03, 0.12};



	public static void main(int[] mainNote,double[][] code,int mainLength,int[] keyChangeNum,int[] keyChange,String[] keyChangeMode,boolean plus,double[][] aij) {
		TRANS_PROB_BASE_MINUS = new double[DIM];
		TRANS_PROB_BASE_MINUS[0] = TRANS_PROB_BASE_PLUS[0];
		//System.out.println("aaa");
		hamohamo.clear();   
		
		for(int i=1;i<DIM;i++){ 
			System.out.println("i="+i);
			TRANS_PROB_BASE_MINUS[i] = TRANS_PROB_BASE_PLUS[DIM-i]; 
			//System.out.println("hahah");
		}
		for(int i=0;i<DIM;i++){
			System.out.println("MINUS"+TRANS_PROB_BASE_MINUS[i]);
		}


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
		/*for (int i = 0; i < DIM; i++)
		  for (int j = 0; j < DIM; j++)
		  hmm1.setAij(i, j, 1.0/DIM); //遷移確率*/
		//遷移確率(MIDより) 
        /*
		hmm1.setAij(0,0,0.3091328147507923);
		hmm1.setAij(0,1,0.04407951598962835);
		hmm1.setAij(0,2,0.15211754537597233);
		hmm1.setAij(0,3,0.04523192163641602);
		hmm1.setAij(0,4,0.036012676462114666);
		hmm1.setAij(0,5,0.033707865168539325);
		hmm1.setAij(0,6,0.0);
		hmm1.setAij(0,7,0.08066839527513685);
		hmm1.setAij(0,8,0.0316911552866609);
		hmm1.setAij(0,9,0.052434456928838954);
		hmm1.setAij(0,10,0.1267646211466436);
		hmm1.setAij(0,11,0.0881590319792567);
		hmm1.setAij(1,0,0.0597473540457494);
		hmm1.setAij(1,1,0.2376237623762376);
		hmm1.setAij(1,2,0.09730283373164902);
		hmm1.setAij(1,3,0.1614885626493684);
		hmm1.setAij(1,4,0.06623420962785934);
		hmm1.setAij(1,5,0.023898941618299762);
		hmm1.setAij(1,6,0.02867872994195971);
		hmm1.setAij(1,7,0.0);
		hmm1.setAij(1,8,0.045749402526459544);
		hmm1.setAij(1,9,0.03789689313758962);
		hmm1.setAij(1,10,0.05360191191532947);
		hmm1.setAij(1,11,0.18777739842949812);
		hmm1.setAij(2,0,0.17277200656096228);
		hmm1.setAij(2,1,0.07763805358119191);
		hmm1.setAij(2,2,0.2793876435210498);
		hmm1.setAij(2,3,0.04045926735921269);
		hmm1.setAij(2,4,0.1957353745215965);
		hmm1.setAij(2,5,0.026517222525970476);
		hmm1.setAij(2,6,0.05057408419901586);
		hmm1.setAij(2,7,0.03335155822854018);
		hmm1.setAij(2,8,8.201202843083652E-4);
		hmm1.setAij(2,9,0.05084745762711865);
		hmm1.setAij(2,10,0.01913613996719519);
		hmm1.setAij(2,11,0.05276107162383816);
		hmm1.setAij(3,0,0.05432336869173899);
		hmm1.setAij(3,1,0.14239794278367085);
		hmm1.setAij(3,2,0.058823529411764705);
		hmm1.setAij(3,3,0.28704596592735454);
		hmm1.setAij(3,4,0.08968177434908389);
		hmm1.setAij(3,5,0.20186435229829636);
		hmm1.setAij(3,6,0.046930247508839604);
		hmm1.setAij(3,7,0.023465123754419802);
		hmm1.setAij(3,8,0.03407264545162327);
		hmm1.setAij(3,9,9.643201542912247E-4);
		hmm1.setAij(3,10,0.03985856637737062);
		hmm1.setAij(3,11,0.020572163291546125);
		hmm1.setAij(4,0,0.02500534302201325);
		hmm1.setAij(4,1,0.056636033340457366);
		hmm1.setAij(4,2,0.19961530241504594);
		hmm1.setAij(4,3,0.05193417396879675);
		hmm1.setAij(4,4,0.27783714468903614);
		hmm1.setAij(4,5,0.04808719811925625);
		hmm1.setAij(4,6,0.1754648429151528);
		hmm1.setAij(4,7,0.04530882667236589);
		hmm1.setAij(4,8,0.02521906390254328);
		hmm1.setAij(4,9,0.03825603761487497);
		hmm1.setAij(4,10,0.002992092327420389);
		hmm1.setAij(4,11,0.053643941013036975);
		hmm1.setAij(5,0,0.04385682038052241);
		hmm1.setAij(5,1,0.040632054176072234);
		hmm1.setAij(5,2,0.026443082876491456);
		hmm1.setAij(5,3,0.2147694292163818);
		hmm1.setAij(5,4,0.10061270557884554);
		hmm1.setAij(5,5,0.2637858755240245);
		hmm1.setAij(5,6,0.05707836181876814);
		hmm1.setAij(5,7,0.13028055465978716);
		hmm1.setAij(5,8,0.0654627539503386);
		hmm1.setAij(5,9,0.018381167365366012);
		hmm1.setAij(5,10,0.038697194453402126);
		hmm1.setAij(5,11,0.0);
		hmm1.setAij(6,0,0.0);
		hmm1.setAij(6,1,0.034911525585844094);
		hmm1.setAij(6,2,0.02654232424677188);
		hmm1.setAij(6,3,0.03706360593017695);
		hmm1.setAij(6,4,0.23529411764705882);
		hmm1.setAij(6,5,0.046389287422285985);
		hmm1.setAij(6,6,0.26040172166427544);
		hmm1.setAij(6,7,0.09469153515064563);
		hmm1.setAij(6,8,0.148493543758967);
		hmm1.setAij(6,9,0.06025824964131994);
		hmm1.setAij(6,10,0.01673840267814443);
		hmm1.setAij(6,11,0.0392156862745098);
		hmm1.setAij(7,0,0.06902887139107612);
		hmm1.setAij(7,1,0.001837270341207349);
		hmm1.setAij(7,2,0.03464566929133858);
		hmm1.setAij(7,3,0.01653543307086614);
		hmm1.setAij(7,4,0.047244094488188976);
		hmm1.setAij(7,5,0.14645669291338584);
		hmm1.setAij(7,6,0.11391076115485564);
		hmm1.setAij(7,7,0.294750656167979);
		hmm1.setAij(7,8,0.08372703412073491);
		hmm1.setAij(7,9,0.13858267716535433);
		hmm1.setAij(7,10,0.027296587926509186);
		hmm1.setAij(7,11,0.025984251968503937);
		hmm1.setAij(8,0,0.008403361344537815);
		hmm1.setAij(8,1,0.029411764705882353);
		hmm1.setAij(8,2,2.8011204481792715E-4);
		hmm1.setAij(8,3,0.045098039215686274);
		hmm1.setAij(8,4,0.04145658263305322);
		hmm1.setAij(8,5,0.06750700280112044);
		hmm1.setAij(8,6,0.19467787114845939);
		hmm1.setAij(8,7,0.08403361344537816);
		hmm1.setAij(8,8,0.25322128851540615);
		hmm1.setAij(8,9,0.09663865546218488);
		hmm1.setAij(8,10,0.13949579831932774);
		hmm1.setAij(8,11,0.039775910364145656);
		hmm1.setAij(9,0,0.03671649619722004);
		hmm1.setAij(9,1,0.023341201153947024);
		hmm1.setAij(9,2,0.045371098872279046);
		hmm1.setAij(9,3,2.6226068712300026E-4);
		hmm1.setAij(9,4,0.05848413322842906);
		hmm1.setAij(9,5,0.022292158405455023);
		hmm1.setAij(9,6,0.06346708628376606);
		hmm1.setAij(9,7,0.15368476265407816);
		hmm1.setAij(9,8,0.08811959087332809);
		hmm1.setAij(9,9,0.2958300550747443);
		hmm1.setAij(9,10,0.02753737214791503);
		hmm1.setAij(9,11,0.1848937844217152);
		hmm1.setAij(10,0,0.16653696498054474);
		hmm1.setAij(10,1,0.0377431906614786);
		hmm1.setAij(10,2,0.013618677042801557);
		hmm1.setAij(10,3,0.03501945525291829);
		hmm1.setAij(10,4,0.0038910505836575876);
		hmm1.setAij(10,5,0.02840466926070039);
		hmm1.setAij(10,6,0.050972762645914396);
		hmm1.setAij(10,7,0.0688715953307393);
		hmm1.setAij(10,8,0.20894941634241246);
		hmm1.setAij(10,9,0.048638132295719845);
		hmm1.setAij(10,10,0.2848249027237354);
		hmm1.setAij(10,11,0.05252918287937743);
		hmm1.setAij(11,0,0.08731280022605256);
		hmm1.setAij(11,1,0.14523876801356314);
		hmm1.setAij(11,2,0.04888386549872845);
		hmm1.setAij(11,3,0.01949703306018649);
		hmm1.setAij(11,4,0.059338796270132804);
		hmm1.setAij(11,5,0.0);
		hmm1.setAij(11,6,0.05001412828482622);
		hmm1.setAij(11,7,0.0384289347273241);
		hmm1.setAij(11,8,0.050861825374399545);
		hmm1.setAij(11,9,0.20118677592540266);
		hmm1.setAij(11,10,0.03758123763775078);
		hmm1.setAij(11,11,0.26165583498163325);  
		//
		//     */
		
		for(int i=0;i<12;i++){
			for(int j=0;j<12;j++){
				hmm1.setAij(i,j,aij[i][j]);
			}
		}  
		for (int i = 0; i < DIM; i++) {
			double[] opdfvalues = new double[DIM];
			if(plus){
				for (int j = 0; j < DIM; j++) 
					opdfvalues[j] = TRANS_PROB_BASE_PLUS[(i + (12-j)) % DIM];
				hmm1.setOpdf(i, new OpdfInteger(opdfvalues));  //出力確率 
			}else{
				for (int j = 0; j < DIM; j++) 
					opdfvalues[j] = TRANS_PROB_BASE_MINUS[(i + (12-j)) % DIM];
				hmm1.setOpdf(i, new OpdfInteger(opdfvalues));  //出力確率   
			}

			System.out.println("OOOOOOOO"+opdfvalues[i]);      
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


		//処理開始（変調の回数るーぷ）
		for(int j=0;j<keyChangeNum.length-1;j++){
			System.out.println(keyChangeNum.length+"HAAAAAAAAAAAAAAAAAAAAA"); 
			List<ObservationInteger> oseq1 = new ArrayList<ObservationInteger>();
			System.out.println("MODE"+keyChangeMode[j]);
			/*if(keyChangeMode[j].equals("MAJ")){
				if(keyChange[j]==0){prior_code=0;} 
				else if(keyChange[j]==7 || keyChange[j]==-5||keyChange[j]==251){prior_code=1;} 
				else if(keyChange[j]==2){prior_code=2;} 
				else if(keyChange[j]==-3||keyChange[j]==253){prior_code=3;} 
				else if(keyChange[j]==4){prior_code=4;} 
				else if(keyChange[j]==-1||keyChange[j]==255){prior_code=5;} 
				else if(keyChange[j]==6){prior_code=6;} 
				else if(keyChange[j]==1){prior_code=7;} 
				else if(keyChange[j]==-4||keyChange[j]==252){prior_code=8;} 
				else if(keyChange[j]==3){prior_code=9;} 
				else if(keyChange[j]==-2||keyChange[j]==254){prior_code=10;} 
				else if(keyChange[j]==5){prior_code=11;}
			}else{
				if(keyChange[j]==-3||keyChange[j]==253 ){prior_code=0;} 
				else if(keyChange[j]==4){prior_code=1;} 
				else if(keyChange[j]==-1||keyChange[j]==255){prior_code=2;} 
				else if(keyChange[j]==6 || keyChange[j]==-6||keyChange[j]==250){prior_code=3;} 
				else if(keyChange[j]==1){prior_code=4;} 
				else if(keyChange[j]==-4||keyChange[j]==252){prior_code=5;} 
				else if(keyChange[j]==3){prior_code=6;} 
				else if(keyChange[j]==-2||keyChange[j]==254){prior_code=7;} 
				else if(keyChange[j]==5||keyChange[j]==-7||keyChange[j]==249){prior_code=8;} 
				else if(keyChange[j]==0){prior_code=9;} 
				else if(keyChange[j]==-5||keyChange[j]==251){prior_code=10;} 
				else if(keyChange[j]==2){prior_code=11;}   
			} */
			//prior = new double[12];
			//System.out.println("prior_code="+prior_code);
			prior_change(keyChange[j],keyChangeMode[j]);
            System.out.print("prior:");
			for(int i=0;i<DIM;i++){
				System.out.print("["+i+"]"+prior[i]);
			} 
			System.out.println(""); 
			for(int i=keyChangeNum[j];i<keyChangeNum[j+1];i++){
				oseq1.add(new ObservationInteger(mainNote[i]%12));
			}
            /*
			for (int i = 0; i < code_all.length; i++) {
			  for (int g = 0; g < code_all[i].length; g++){ 
				System.out.print((double)code_all[i][g] + ","); }
			  System.out.println();    }
			 

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
			if(GlobalVariable.C){
				System.out.println("oseq1: "+oseq1.get(i)+"\toseq2: "+oseq2.get(i));   
			}
			}

			/* Viterbiアルゴリズムを実行 */ 

			CompoundHMMsViterbiCalculator viterbi =
				new CompoundHMMsViterbiCalculator(oseq1, hmm1, 1.0, oseq2, hmm2, 1.0,
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
		  System.out.println(states[i]); */ 




	}

	public static void prior_change(int pc,String mode){
		if(mode.equals("MAJ")){
			for (int i = 0; i < DIM; i++) {
				prior[i]= PRIOR_MAJOR[(i - pc+12) % DIM];
				//System.out.println("====================->"+mode+":"+pc);

			}
			for(int i=0;i<DIM;i++){ System.out.println("prior="+prior[i]); }  
		}else{
			for (int i = 0; i < DIM; i++) {
				prior[i]= PRIOR_MINOR[(i - pc+12) % DIM];
				//System.out.println("====================->"+mode+":"+pc); 
			} 
			for(int i=0;i<DIM;i++){ System.out.println("prior="+prior[i]); }
		}
	}
} 
