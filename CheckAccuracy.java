/*****************************************************************
// Copyright 20linemid-2014 Masanori Morise. All Rights Reserved.
// Author: Kazuki Urabe 
// 
// F0 estimation based on DIO(Distributed Inline-filter Operation)
// Referring to World(http://ml.cs.yamanashi.ac.jp/world/index.html).
 *****************************************************************/
import jp.crestmuse.cmx.processing.*;
import jp.crestmuse.cmx.filewrappers.*;
import jp.crestmuse.cmx.amusaj.filewrappers.*;
import jp.crestmuse.cmx.amusaj.sp.*;
import jp.crestmuse.cmx.math.*;
import jp.crestmuse.cmx.sound.*;
import jp.crestmuse.cmx.elements.*;
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



public class CheckAccuracy extends PApplet {

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

	long position;

	long[] onsetData;  // 発音時間
	long[] offsetData;  //  消音時間
	long[] notenumData;

	//精度チェック
    long[] onsetCheck;  // 発音時間                      //
	long[] offsetCheck;  //  消音時間                      //
	long[] notenumCheck;                                     //

	long[] hamoNotenum;  // ハモリノートナンバー               //
	long[] hamoData;	// メインメロディの音との差              //
	int division;
	int maxNum;
	int minNum;
	int aveNum;
	int musicLength;

	int half_div;
	long endtick;
	int on_off;

	int[][] code;		//伴奏の小節ごとにどの音が多いのかの分析
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

    int musicnum = 10;
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


	int temposoeji=0;
	int [][] tempo_tick = new int[20][2];//[][0]はtempo,[][1]はtick


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

					SCCXMLWrapper.Note[] notelist = null;
                    //精度
                    SCCXMLWrapper.Note[] notelistCheck = null;


					//scc2.toWrapper().toMIDIXML().writefileAsSMF("aaaa.mid");
					for(int i=0;i<partlist.length;i++){
						if(partlist[i].channel()==1){
							notelist = partlist[i].getNoteOnlyList();	   
						}
					}
					//SCCXMLWrapper.HeaderElement[] = scc.getHeaderElentList();
					if (notelist == null) {
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

					notenumData = new long[notelist.length];     //
					onsetData = new long[notelist.length];         //
					offsetData = new long[notelist.length];          //
					hamoNotenum = new long[notelist.length];           //
					musicLength=notelist.length;

					

					for (int i=0; i<notelist.length; i++) {
						notenumData[i]=notelist[i].notenum();
						onsetData[i]=notelist[i].onset();
						offsetData[i]=notelist[i].offset();
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

					hamoData = new long[notelist.length];                      //
					//分析
					//伴奏部分配列代入
					for(int i=0;i<partlist.length;i++){
						if( partlist[i].channel()!=10 || partlist[i].channel()!=1 ){
							//notelist_hamo = partlist[i].getNoteOnlyList();
							notelist_hamo.addAll(Arrays.asList(partlist[i].getNoteOnlyList()));
						}                                                      
					}
					
						 
					long tick_f = 0;     //
					long tick_e = 0;     //

					endtick=offsetData[notelist.length-1];
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
					for(int j=0;j<notelist.length;j++){
						for(int i=3;i>=0;i--){

							if(plus){
								on_off=(int)((onsetData[j]+offsetData[j])/2/half_div); 
								//短3度
								if((notenumData[j]+3)%12 == code_hamo[i][on_off]){
									hamoData[j]=3;
								}
								//長3度
								if((notenumData[j]+4)%12 == code_hamo[i][on_off]){
									hamoData[j]=4;
								}
								//完全4度
								if((notenumData[j]+5)%12 == code_hamo[i][on_off]){
									hamoData[j]=5;
								} 

							}else{

								on_off=(int)((onsetData[j]+offsetData[j])/2/half_div); 
								//短3度
								if((notenumData[j]-3)%12 == code_hamo[i][on_off]){
									hamoData[j]=-3;
								}
								//長3度
								if((notenumData[j]-4)%12 == code_hamo[i][on_off]){
									hamoData[j]=-4;
								}
								//完全4度
								if((notenumData[j]-5)%12 == code_hamo[i][on_off]){
									hamoData[j]=-5;
								}  
							}

						}


					}


					/*for(int j=0;j<a+3;j++){ 
					  System.println(code_hamo[0][j],code_hamo[1][j],code_hamo[2][j],code_hamo[3][j])
					  }*/

					/*for(int i=0;i<12;i++){
					  System.out.println(code[i][5]);
					  }*/

					//ハモリ追加
					if(isHamo){
						for(int i=0;i<notelist.length;i++){
							if(plus){
								if(hamoData[i]==3 || hamoData[i]==4 ||hamoData[i]==5){
									System.out.println("hamoNotenum="+(notenumData[i]+hamoData[i]) 
											+ " , +" + hamoData[i]); 
									hamoNotenum[i] = notenumData[i]+hamoData[i];
									p.addNoteElement((int)onsetData[i],(int)offsetData[i],(int)(notenumData[i]+hamoData[i]),100,100); 
								}else if(hamoData[i]==0){

									hamoNotenum[i] = -1;
									System.out.println("hamoNotenum="+(notenumData[i]+hamoData[i]) 
											+ " , NoData");     
								}
							}else{
								if(hamoData[i]==-3 || hamoData[i]==-4 ||hamoData[i]==-5){

									System.out.println("hamoNotenum="+(notenumData[i]+hamoData[i]) 
											+ " , " + hamoData[i]); 
									hamoNotenum[i] = notenumData[i]+hamoData[i];
									p.addNoteElement((int)onsetData[i],(int)offsetData[i],(int)(notenumData[i]+hamoData[i]),100,100); 
								}else if(hamoData[i]==0){

									hamoNotenum[i] = -1;
									System.out.println("hamoNotenum="+(notenumData[i]+hamoData[i]) 
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
						for (int i=0; i<onsetData.length; i++) {
							for (int j=0;j<onsetCheck.length; j++){
								if (onsetData[i] == onsetCheck[j]  & offsetData[i] == offsetCheck[j]){
                                    tickMatch++;
									if(hamoNotenum[i] == notenumCheck[j]){
									
										match++;
									}
								}
							}

						}

						println("match="+ match + "per=" + match/musicLength);  
						try{
							File file = new File("./checkin/result_match.txt");
							FileWriter filewriter = new FileWriter(file,false);

							PrintWriter printwriter = new PrintWriter(new BufferedWriter(filewriter));
							printwriter.println("tickMatch="+tickMatch);
							printwriter.println("haha");
							printwriter.println("musicLength="+musicLength);
							printwriter.println("match="+ match + "per=" + (double)match/musicLength); 
							printwriter.println("草生えた");
							printwriter.println("noDataNum="+noDataNum);
							printwriter.println("except nodata per = "+(double)match/(musicLength-noDataNum));
							printwriter.close();
						}catch(IOException e){
							System.out.println(e);
						}

						try{
							File fileC = new File("./checkin/C.txt");
							FileWriter filewriterC = new FileWriter(fileC,false);

							PrintWriter printwriterC = new PrintWriter(new BufferedWriter(filewriterC));
							printwriterC.println("Main division = "+division);
							for(int i=0 ; i<onsetData.length ; i++){
								printwriterC.println("Main"+i+":onset="+onsetData[i]+",offset="+offsetData[i]+"notenum="+hamoNotenum[i]);
								
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
					   


					prepare = new Preparation(notelist.length);//check max and min
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
					for (int p=0; p<musicLength; p++) {
	                    //ピアノロール(メイン)
						if(isHamo){
							fill(230, 230, 230,100);
						}else{
							fill(255,78,107);
						}
						displayRect((int)((onsetData[p] - div * (q-1))*width/div), (int)(linemid*(lineNum-((notenumData[p]+key)-subst)-1)), 
								(int)((offsetData[p] -div*(q-1))*width/div), (int)(linemid*(lineNum-((notenumData[p]+key)-subst))));           ///
						//ピアノロール(はもり)
						if(hamoNotenum[p] != -1){
							if(isHamo){
								fill(255, 78, 107); 
								displayRect((int)((onsetData[p] - div * (q-1))*width/div), (int)(linemid*(lineNum-((hamoNotenum[p]+key)-subst)-1)), 
										(int)((offsetData[p] -div*(q-1))*width/div), (int)(linemid*(lineNum-((hamoNotenum[p]+key)-subst)))); ///
							}
						} 

					}
					if(checking){  
						for(int p=0;p<onsetCheck.length;p++){

							//精度チェック
							stroke(0);
							line(((onsetCheck[p] - div * (q-1))*width/div),(linemid * (lineNum - (notenumCheck[p] - subst)-1)),
									((offsetData[p] -div*(q-1))*width/div),(linemid * (lineNum - (notenumCheck[p] - subst))));
						}
					}


					stroke(100);
					line((position-(div*(q-1)))*width/div, 0, (position-(div*(q-1)))*width/div, endLine);
                    // draw F0
					for (int p = 1; p < CheckAccuracy.dioM.ticks.size(); p++) {
						double x0 = (CheckAccuracy.dioM.ticks.get(p-1) - div * (q-1)) * width / div;
						double x1 = (CheckAccuracy.dioM.ticks.get(p) - div * (q-1)) * width / div;
						double y0 = linemid * (lineNum - ((CheckAccuracy.dioM.pitches.get(p-1) + octave*12) - subst)-2);
						double y1 = linemid * (lineNum - ((CheckAccuracy.dioM.pitches.get(p) + octave*12) - subst)-2);
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
			}else if(mainvol==0){
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
				mainvol=0;
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
		int musicLength;
		Preparation(int musicLength) {
			this.musicLength=musicLength;
		}
		void checkPitch() {
			for (int i=0; i<musicLength; i++) {
				if (maxNotenum<notenumData[i]) {  
					maxNotenum=(int)notenumData[i];
				} else if (minNotenum>notenumData[i]) {  
					minNotenum=(int)notenumData[i];
				}
			}
		}
	}


	public static void main(String[] args) {
		PApplet.main(new String[] { 
			"CheckAccuracy"
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
