package edu.nenu.ist.ndnshare.SearchFragment;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

import edu.nenu.ist.ndnshare.FileFragment.FileAdapter;
import edu.nenu.ist.ndnshare.FileFragment.FileDetails;
import edu.nenu.ist.ndnshare.MainActivity;
import edu.nenu.ist.ndnshare.MyApplication;
import edu.nenu.ist.ndnshare.R;
import edu.nenu.ist.ndnshare.Service.ShareService;

public class SearchPage extends Fragment{
    private static final String TAG = "SearchPage";

    public final Context context;

    public View mainView;

    private TextView textView;

    private boolean networkThreadShouldStop, listviewAlreadySet = false;

    private ListView listView;
    private TextView getlist;
    private ProgressBar pb_search;
    private TextView searchhint;
    private FileAdapter fileAdapter;

    //搜索框
    private EditText search;
    private Button btn;
    private String text = "";

    private int position;

    private ArrayList<FileDetails> returnlist = new ArrayList<FileDetails>();

    public SearchPage(Context context){
        this.context = context;
        mainView = initView();
    }

    public  View initView(){
        View view = View.inflate(context, R.layout.searchlist,null);
        listView = (ListView) view.findViewById(R.id.searchlist);
        getlist = (TextView) view.findViewById(R.id.getfile);
        pb_search = (ProgressBar) view.findViewById(R.id.pb_search);
        searchhint = (TextView) view.findViewById(R.id.search_hint);
        pb_search.setVisibility(View.GONE);
        searchhint.setVisibility(View.VISIBLE);
        return view;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return mainView;}

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        Log.d(TAG,"onActivityCreated run");
        initData(((MyApplication) getActivity().getApplication()).getReturnlist());
        search = (EditText)getActivity().findViewById(R.id.search);
        btn= (Button)getActivity().findViewById(R.id.btn);
        btn.setOnClickListener(new myClick());
        super.onActivityCreated(savedInstanceState);
    }

    public void initData(ArrayList list){
        returnlist = list;
        Log.d(TAG,"start thread");
        new Thread() {
            @Override
            public void run() {
                super.run();
                handler.sendEmptyMessage(0);
            }
        }.start();
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener(){
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                //position++;
                //String showText = "点击第" + position + "项";
                //Toast.makeText(getActivity(), showText, Toast.LENGTH_LONG).show();
                AlertDialog.Builder builder  = new AlertDialog.Builder(getActivity());
                //builder.setTitle("确认" ) ;
                builder.setMessage("download "+ fileAdapter.getEachfilename(i) + " or cancel ？" ) ;
                position = i;
                builder.setNegativeButton("download",new DialogInterface.OnClickListener(){
                            public void onClick(DialogInterface paramAnonymousDialogInterface, int paramAnonymousInt){
                                Log.d(TAG,"touch 是");
                        String showText = "downloading...";
                        Toast.makeText(getActivity(), showText, Toast.LENGTH_LONG).show();
                        askFile(fileAdapter.getEachfilename(position),fileAdapter.getEachfileprefix(position));
                    }
                } );
                builder.setPositiveButton("cancel", null);
                builder.show();
            }
        });
    }

    private Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg){
            Log.v("handler","is run");
            super.handleMessage(msg);
            if(returnlist != null && returnlist.size()>0 && listviewAlreadySet == false){
                //设置适配器，把文本和progressbar隐藏
                Log.d(TAG,"First set listview");
                fileAdapter = new FileAdapter(context,returnlist);
                listView.setAdapter(fileAdapter);
                listView.setVisibility(View.VISIBLE);
                searchhint.setVisibility(View.GONE);
                getlist.setVisibility(View.GONE);
                listviewAlreadySet = true;
            }else if(returnlist != null && returnlist.size()>0 && listviewAlreadySet == true){
                Log.d(TAG,"Change listview");
                fileAdapter.setEachfile(returnlist);
                fileAdapter.notifyDataSetChanged();
                listView.setVisibility(View.VISIBLE);
            }else if(text.equals("")){
                //没有数据则显示文本
                listView.setVisibility(View.GONE);
                getlist.setVisibility(View.GONE);
                searchhint.setVisibility(View.VISIBLE);
            }else{
                listView.setVisibility(View.GONE);
                searchhint.setVisibility(View.GONE);
                getlist.setVisibility(View.VISIBLE);
            }
            pb_search.setVisibility(View.GONE);
        }
    };

    public class myClick implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            if(view == btn){
                closeKeyBoard(context,search);
                text = search.getText().toString();
                if(!text.equals("")){
                    AlertDialog.Builder builder  = new AlertDialog.Builder(getActivity());
                    builder.setTitle("Exact match or Fuzzy match ？" ) ;
                    builder.setPositiveButton("Fuzzy", new DialogInterface.OnClickListener(){
                        public void onClick(DialogInterface paramAnonymousDialogInterface, int paramAnonymousInt){
                            Log.d(TAG,"模糊搜索");
                            returnlist = null;
                            ((MyApplication) getActivity().getApplication()).setReturnlist(null);
                            listView.setVisibility(View.GONE);
                            searchhint.setVisibility(View.GONE);
                            getlist.setVisibility(View.GONE);
                            pb_search.setVisibility(View.VISIBLE);
                            askFuzzyList(text);
                            startNetworkThread();
                        }
                    } );
                    builder.setNegativeButton("Exact",new DialogInterface.OnClickListener(){
                        public void onClick(DialogInterface paramAnonymousDialogInterface, int paramAnonymousInt){
                            Log.d(TAG,"精确搜索");
                            returnlist = null;
                            ((MyApplication) getActivity().getApplication()).setReturnlist(null);
                            listView.setVisibility(View.GONE);
                            searchhint.setVisibility(View.GONE);
                            getlist.setVisibility(View.GONE);
                            pb_search.setVisibility(View.VISIBLE);
                            askExactList(text);
                            startNetworkThread();
                        }
                    } );
                    builder.show();
                }else{
                    listView.setVisibility(View.GONE);
                    getlist.setVisibility(View.GONE);
                    searchhint.setVisibility(View.VISIBLE);
                    pb_search.setVisibility(View.GONE);
                }
                Log.d(TAG,"get text:"+ text);
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

    private void askList(String text){
        Log.d(TAG, "startService");
        Intent asklist = new Intent(getActivity(), ShareService.class);
        asklist.setAction(ShareService.ACTION_ASK_LIST)
                .putExtra(ShareService.EXTRA_MESSAGE, text);
        getActivity().startService(asklist);
    }

    private void askExactList(String text){
        Log.d(TAG, "startService");
        Intent asklist = new Intent(getActivity(), ShareService.class);
        asklist.setAction(ShareService.ACTION_ASK_EXACT_LIST)
                .putExtra(ShareService.EXTRA_MESSAGE, text);
        getActivity().startService(asklist);
    }

    private void askFuzzyList(String text){
        Log.d(TAG, "startService");
        Intent asklist = new Intent(getActivity(), ShareService.class);
        asklist.setAction(ShareService.ACTION_ASK_FUZZY_LIST)
                .putExtra(ShareService.EXTRA_MESSAGE, text);
        getActivity().startService(asklist);
    }

    private void askFile(String filename, String prefix){
        Log.d(TAG, "ask file name: "+ filename);
        Log.d(TAG, "ask file prefix: "+ prefix);
        Intent askfile = new Intent(getActivity(), ShareService.class);
        askfile.setAction(ShareService.ACTION_ASK_FILE)
                .putExtra(ShareService.EXTRA_MESSAGE, filename)
                .putExtra(ShareService.EXTRA_PREFIX, prefix);
        getActivity().startService(askfile);
    }

    private void startNetworkThread() {
        if (!networkThread.isAlive()) {
            networkThreadShouldStop = false;
            networkThread.start();
        }
    }

    private void stopNetworkThread() {
        networkThreadShouldStop = true;
    }

    protected boolean networkThreadIsRunning() {
        return networkThread.isAlive();
    }

    private final Thread networkThread = new Thread(new Runnable() {
        @Override
        public void run () {
            while (!networkThreadShouldStop){
                returnlist = ((MyApplication) getActivity().getApplication()).getReturnlist();
                if(returnlist != null){
                    Log.e(TAG,"returnlist: " + returnlist.toString());
                    new Thread() {
                        @Override
                        public void run() {
                            super.run();
                            handler.sendEmptyMessage(0);
                        }
                    }.start();
                    stopNetworkThread();
                }
            }
        }
    });
}
