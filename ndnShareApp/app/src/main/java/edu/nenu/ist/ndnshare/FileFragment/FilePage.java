package edu.nenu.ist.ndnshare.FileFragment;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;

import edu.nenu.ist.ndnshare.MyApplication;
import edu.nenu.ist.ndnshare.R;

public class FilePage extends Fragment implements View.OnClickListener{
    private static final String TAG = "FilePage";

    private ListView listView;
    private TextView filelist;
    private ProgressBar pb_file;
    private FileAdapter fileAdapter;

    public View mainView;
    public File file = new File("/sdcard/ndnshare/");

    //搜索框
    private EditText search;
    private Button btn;
    private String text = null;

    private ArrayList<FileDetails> eachfile = new ArrayList<FileDetails>();

    public String img[] = { "bmp", "jpg", "jpeg", "png", "tiff", "gif", "pcx", "tga", "exif", "fpx", "svg", "psd",
            "cdr", "pcd", "dxf", "ufo", "eps", "ai", "raw", "wmf" };
    public String document[] = { "txt", "doc", "docx", "xls", "htm", "html", "jsp", "rtf", "wpd", "pdf", "ppt" };
    public String video[] = { "mp4", "avi", "mov", "wmv", "asf", "navi", "3gp", "mkv", "f4v", "rmvb", "webm" };
    public String music[] = { "mp3", "wma", "wav", "mod", "ra", "cd", "md", "asf", "aac", "vqf", "ape", "mid", "ogg",
            "m4a", "vqf" };

    public final Context context;

    private TextView textView;

    public FilePage(Context context){
    //判断文件夹是否存在，如果不存在就创建，否则不创建
        if (!file.exists()) {
            //通过file的mkdirs()方法创建目录中包含却不存在的文件夹
            file.mkdirs();
        }

        /*String testfileName1="test1.txt";
        String testfileName2="test2.doc";
        String testfileName3="test3.jpg";
        String testfileName4="test4.mp4";
        writeTxtToFile("test","/sdcard/ndnshare/", testfileName1);
        writeTxtToFile("test","/sdcard/ndnshare/", testfileName2);
        writeTxtToFile("test","/sdcard/ndnshare/", testfileName3);
        writeTxtToFile("test","/sdcard/ndnshare/", testfileName4);*/


        this.context = context;
        mainView = initView();
    }

    public void writeTxtToFile(String strcontent, String filePath, String fileName) {
        //生成文件夹之后，再生成文件，不然会出错
        makeFilePath(filePath, fileName);

        String strFilePath = filePath+fileName;
        // 每次写入时，都换行写
        String strContent = strcontent + "\r\n";
        try {
            File file = new File(strFilePath);
            if (!file.exists()) {
                Log.d("TestFile", "Create the file:" + strFilePath);
                file.getParentFile().mkdirs();
                file.createNewFile();
                RandomAccessFile raf = new RandomAccessFile(file, "rwd");
                raf.seek(file.length());
                raf.write(strContent.getBytes());
                raf.close();
            }
        } catch (Exception e) {
            Log.e("TestFile", "Error on write File:" + e);
        }
    }

    public File makeFilePath(String filePath, String fileName) {
        File file = null;
        try {
            file = new File(filePath + fileName);
            if (!file.exists()) {
                file.createNewFile();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return file;
    }

    private Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg){
            Log.v("handler","is run");
            super.handleMessage(msg);
            if(eachfile != null && eachfile.size()>0){
                //设置适配器，把文本和progressbar隐藏
                Log.d(TAG,"folder has file");
                listView.setAdapter(fileAdapter);
                fileAdapter = new FileAdapter(context,eachfile);
                listView.setAdapter(fileAdapter);
                filelist.setVisibility(View.GONE);
            }else {
                //没有数据则显示文本
                filelist.setVisibility(View.VISIBLE);
            }
            pb_file.setVisibility(View.GONE);
        }
    };

    public  View initView(){
        Log.d(TAG,"initView");
        View view = View.inflate(context, R.layout.filelist,null);
        listView = (ListView) view.findViewById(R.id.filelist);
        filelist = (TextView) view.findViewById(R.id.eachfile);
        pb_file = (ProgressBar) view.findViewById(R.id.pb_file);

        return view;
    }

    public void initData(){
        Log.d(TAG,"initData");
        getDataFromFolder();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        Log.d(TAG,"onActivityCreated run");
        initData();
        search = (EditText)getActivity().findViewById(R.id.search);
        btn= (Button)getActivity().findViewById(R.id.btn);
        btn.setOnClickListener(this);
        super.onActivityCreated(savedInstanceState);
        }


    //读取共享文件
    //从内容提供者获取文件
    public void getDataFromFolder() {
        Log.d(TAG,"start thread");
        new Thread() {
            @Override
            public void run() {
                super.run();
                eachfile = new ArrayList<FileDetails>();
                //调用遍历所有文件的方法
                Log.d(TAG,"searchtext = "+ text);
                recursionFile(file, eachfile,text);
                //handler发消息
                Log.v(TAG,"send empty");
                handler.sendEmptyMessage(0);
            }
        }.start();

    }


    public void recursionFile(File dir,ArrayList<FileDetails> eachfile,String search) {
        //得到某个文件夹下所有的文件
        File needfile = new File("/sdcard/ndnshare/");
        File[] files = dir.listFiles();
        //文件为空
        if (files == null) {
            return;
        }
        //遍历当前文件下的所有文件
        for (File file : files) {
            //如果是文件夹
            if (file.isDirectory()) {
                //则递归(方法自己调用自己)继续遍历该文件夹
                recursionFile(file,eachfile,search);
            }
            else { //如果不是文件夹 则是文件
                FileDetails onefile = new FileDetails();
                String name = file.getName();
                Log.d(TAG,"search = "+ search);
                if(search==null||name.indexOf(search)>= 0||search.equals("")){
                    eachfile.add(onefile);
                    Log.d(TAG,"eachfile add." + eachfile.toString());
                    //获取文件名
                    onefile.setName(name);
                    //识别文件类型
                    String fileType = name.substring(name.lastIndexOf(".") + 1, name.length()).toLowerCase();
                    String type = null;
                    for (int i = 0; i < img.length; i++)
                        if (img[i].equals(fileType))
                            type = "图片";
                    for (int j = 0; j < document.length; j++)
                        if (document[j].equals(fileType))
                            type = "文档";
                    for (int k = 0; k < video.length; k++)
                        if (video[k].equals(fileType))
                            type = "视频";
                    for (int l = 0; l < music.length; l++)
                        if (music[l].equals(fileType))
                            type = "音频";
                    if(type==null)  type = "未知";
                    onefile.setType(type);
                    //获取文件大小
                    long size = file.length();
                    onefile.setSize(size);
                }
            }
        }
        if(search==null){
            Log.d(TAG,"setFilelist");
            ((MyApplication) getActivity().getApplication()).setFilelist(eachfile);
        }
    }

    public void onClick(View view) {
        if(view == btn){
            closeKeyBoard(context,search);
            filelist.setVisibility(View.GONE);
            text = search.getText().toString();
            Log.d(TAG,"get text:"+ text);
            eachfile = new ArrayList<FileDetails>();
            recursionFile(file, eachfile,text);
            fileAdapter.setEachfile(eachfile);
            fileAdapter.notifyDataSetChanged();
            if(fileAdapter.getCount()==0){
                filelist.setVisibility(View.VISIBLE);
            }
        }
    }

    /**
     * 关闭该控件的软键盘
     * */
    public static void closeKeyBoard(Context ctx,EditText editText) {
        try {
            InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return mainView;}
}
