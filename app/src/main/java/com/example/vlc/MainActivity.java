package com.example.vlc;



import android.app.Activity;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;


public class MainActivity extends Activity  implements DetectionFragment.OnRecordBtnClickListener{

    protected static final String TAG = "MainActivity";

    private ImageButton mDetection,mPosition,mSetting;
    private View currentButton;

//	public TextView showIDView; 
//	public TextView showStatusView;

    private boolean isRecording = false;// ����Ƿ��ڲ�������
    public long VLCID = 0;// ������
    public int frequency = 32000;// ����Ƶ�ʣ�����ʱ�̶���32k�����ǵ�ĳЩ������֧��32k����������ѭ��ѡ�����Ƶ��
    public short audioEncoding = AudioFormat.ENCODING_PCM_16BIT;// ����λ�����̶�Ϊ16λ
    public short channelConfiguration = AudioFormat.CHANNEL_IN_MONO;// ��ͨ��
    private Handler handler,recordHandler;// �������߳������ݽ�����

    RecordThread recordThread;
    DetectionFragment detectfm = null;
    PositionFragment positionfm = null;
    SettingFragment settingfm = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findView();
        init();

    }

    private void findView(){

        mDetection=(ImageButton) findViewById(R.id.buttom_detection);
        //mPosition=(ImageButton) findViewById(R.id.buttom_position);
        mSetting=(ImageButton) findViewById(R.id.buttom_setting);

    }

    private void init(){
        mDetection.setOnClickListener(detectionOnClickListener);
        //mPosition.setOnClickListener(positionOnClickListener);
        mSetting.setOnClickListener(settingOnClickListener);
        mDetection.performClick();

        handler = new Handler() {// �������̵߳Ľ���������ʾ
            public void handleMessage(Message msg) {
                String message = (String) msg.obj;
                //	if (isRecording)
                //		showIDView.setText(message);
            }

        };

        recordHandler = new Handler() {// �����̵߳Ľ���������ʾ
            public void handleMessage(Message msg) {
                //long result = (Long) msg.obj;
                String  result = (String) msg.obj;
                //	System.out.println("result =" + result);

                if(detectfm != null) {
                    detectfm.sendVlcId(result,isRecording);
                }
                /*
				else if(positionfm != null){
					MapView mapview = (MapView)findViewById(R.id.mapview);
					if(mapview!= null)
						mapview.sendVlcId(result,isRecording);
				}
									*/

            }

        };

    }

    public Handler getHandler() {// �ƺ�û�õ�
        return this.handler;
    }



    private OnClickListener detectionOnClickListener=new OnClickListener() {
        @Override
        public void onClick(View v) {
            positionfm = null;
            settingfm = null;
			/* ���� Bundle ����, Activity ���ݸ� Fragment �Ĳ�����Ҫ���ö�����д��� */
            Bundle recordstate = new Bundle();
	        /* ��װ���ݵ� Bundle ������, ע����ǰ����ü�ֵ */
            recordstate.putBoolean(DetectionFragment.TAG_ID, isRecording);

            FragmentManager fm=getFragmentManager();
            FragmentTransaction ft=fm.beginTransaction();
            //	DetectionFragment detectfm = new DetectionFragment();
            detectfm = new DetectionFragment();
			
			/* �� Activity Ҫ���ݵ����� ���ݸ� Fragment ���� */
            detectfm.setArguments(recordstate);

            ft.replace(R.id.fl_content,detectfm,MainActivity.TAG);
            ft.commit();
            setButton(v);

        }

    };

    private OnClickListener positionOnClickListener=new OnClickListener() {
        @Override
        public void onClick(View v) {
            detectfm = null;
            settingfm = null;
            FragmentManager fm=getFragmentManager();
            FragmentTransaction ft=fm.beginTransaction();
            //	PositionFragment positionfm = new PositionFragment();
            positionfm = new PositionFragment();
            ft.replace(R.id.fl_content,positionfm,MainActivity.TAG);
            ft.commit();
            setButton(v);
        }
    };

    private OnClickListener settingOnClickListener=new OnClickListener() {
        @Override
        public void onClick(View v) {
            detectfm = null;
            positionfm = null;
            FragmentManager fm=getFragmentManager();
            FragmentTransaction ft=fm.beginTransaction();
            //	SettingFragment settingfm = new SettingFragment();
            settingfm = new SettingFragment();
            ft.replace(R.id.fl_content,settingfm,MainActivity.TAG);
            ft.commit();
            setButton(v);
        }
    };


    private void setButton(View v){
        if(currentButton!=null&&currentButton.getId()!=v.getId()){
            currentButton.setEnabled(true);
        }
        v.setEnabled(false);
        currentButton=v;
    }


    // 求采样数据均值，用于判决高低电平
    private int avg(int[] data) {
        int length = data.length;
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum = sum + data[i];
        }
        sum = sum / length;
        return sum;
    }


    public String getCode(int dataBinary,int numberBinary,String resultBinary){
        int repeat = 0;
        if(numberBinary%4==3) repeat = numberBinary/4+1;
        else if((numberBinary%4==1)||(numberBinary%4==0)) repeat = numberBinary/4;
        if(repeat > 0)
            for (int i = 0;i<repeat;i++){
                resultBinary = resultBinary + dataBinary;
            }
        return resultBinary;
    }


    public void onStartBtnClicked(boolean Record){

        isRecording = Record;

//		showIDView = (TextView) findViewById(R.id.detected_id); 
//		showStatusView = (TextView) findViewById(R.id.detect_status_tv);

        if(isRecording)
        {
            recordThread = new RecordThread();
            recordThread.start();
        }
        else
        {
            //	showIDView.setText("");
            //	showStatusView.setText("");
        }

    }

    class RecordThread extends Thread {

        RecordThread() {
            super();
        }
        @Override
        public void run() {

            // �ڵ�ǰ���������£���ȡ��С��buffer����
            final int bufferSize = AudioRecord.getMinBufferSize(frequency,
                    channelConfiguration, audioEncoding);
            // ��ʼ��AudioRecord
            final AudioRecord audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC, frequency,
                    channelConfiguration, audioEncoding, bufferSize);
            audioRecord.startRecording();

            while (isRecording) {
                short[] buffer = new short[bufferSize];
                int bufferReadResult = audioRecord.read(buffer, 0, bufferSize);// ����bufferSize���ȵ�����



                // ���״β���ʱ��������������ǰ�λ��кܶ�0����flag�궨0�ĸ�����len�ó���0�ĸ���
                // �����0���ݹ��٣������˴β������
                // �����0���ݽ϶࣬�����˴β�������������Խ���
                // �ڶ��β����������0
                // �˴���������޸�Ϊֱ���ж��Ƿ��ǵ�һ�β����������Ƿ������ݷ�0����
                int flag = 0;
                for (int i = 0; i < bufferReadResult; i++) {
                    if (buffer[i] != 0) {
                        flag = i;
                        break;
                    }
                }
                int len = bufferReadResult - flag - 10;
                //	System.out.println(bufferReadResult);
                //	System.out.println(len);
                int[] tmp;


                short temp[];
                if (len < 50) {
                    tmp = new int[50];

                    temp = new short[50];
                    for (int i = 0; i < 50; i++)
                        tmp[i] = 0;
                    continue;
                } else {// ��������������16λ�ģ��Ұ�����ת����int���ͣ���������tmp
                    tmp = new int[len];
                    temp = new short[len];
                    for (int i = flag; i < flag + len; i++) {
                        tmp[i - flag] = buffer[i];
                    }
                }

                int maxBuffer = 0;
                for (int i = 0; i < buffer.length; i++) {
                    maxBuffer = Math.max(maxBuffer,buffer[i]);
                }

                //判断高低电平
                //String outputBinary = "";

                System.out.println("maxBuffer: "+maxBuffer);

                int data[] = new int[buffer.length];


                    for (int i = 1; i < buffer.length; i++) {

                        if (buffer[i-1] - buffer[i]>= maxBuffer/6) {

                            //outputBinary = outputBinary +"0";

                            data[i] = 0;
                        }

                        else if (buffer[i-1] - buffer[i]< -maxBuffer/6) {

                            //outputBinary = outputBinary + "1";

                            data[i] = 1;
                        }
                        else {

                            //outputBinary = outputBinary + "2";

                            data[i] = 2;
                        }
                    }

                    int startCount=0;
                    for (int i=1,pass=0;i<buffer.length;i++){
                        if (pass<=40){
                            if(data[i]==2) pass++;
                            else pass=0;
                        }

                        //pass>40 start reading data
                        else if ((data[i-1]==0)&&((data[i]==1)||(data[i]==2))) {
                            startCount=i;
                            break;
                        }
                        //else pass = 0;
                    }
                    System.out.println("startCount:"+startCount);
                    String showBinary = "";
                int readData=2;
                    for(int i=startCount+3;i<buffer.length;i=i+4){


                            if(data[i]!=2)  {

                                readData=data[i];

                            }
                        else{
                                if(data[i-4]==0) readData=1;
                                else if(data[i-4]==1) readData=0;

                            }
                        showBinary=showBinary+readData;
                    }



                //System.out.println(outputBinary);


                System.out.println(showBinary);


                //decode
                String showLetter = "";

                for(int i=9,m=1,n=1;i<showBinary.length();i++){
/*
                    if((showBinary.charAt(i)=='0')&&(showBinary.charAt(i-1)=='0')){
                        m++;
                        if(m>=10) n=1;
                    }
*/

                    if ((showBinary.charAt(i) == '0') && (showBinary.charAt(i - 9) == '1')&&(n>0)) {

                        char charBinary;
                        int ascNumber = 0;
                        for (int j = 8, k = 0; j > 0; j--, k++) {
                            charBinary = showBinary.charAt(i - j);
                            int add = (int) Math.pow(2, k);
                            if (charBinary == '0') ascNumber = ascNumber + add;

                        }
                        n++;
                        if(ascNumber<128)
                            showLetter = showLetter + (char) ascNumber;
                        else n=0;
                        i = i + 9;

                    }
                    //else if((showBinary.charAt(i-9)=='0')&&(n>1)) break;
                    else break;


                }


                if(detectfm !=null){
                    SoundWave soundwave = (SoundWave)findViewById(R.id.soundwave);
                    if(soundwave != null)
                        soundwave.sndAudioBuf(buffer,bufferReadResult);
                }

                // tmp.length约1910

                System.out.println(showLetter);


                Message message = Message.obtain();
                message.obj = String.valueOf(showLetter);

                recordHandler.sendMessage(message);

            }
            audioRecord.stop();
            audioRecord.release();
        }
    }



}
