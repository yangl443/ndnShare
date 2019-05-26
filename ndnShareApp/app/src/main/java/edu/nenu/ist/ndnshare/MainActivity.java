package edu.nenu.ist.ndnshare;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.RadioGroup;
import android.widget.Toast;

import edu.nenu.ist.ndnshare.FileFragment.FilePage;
import edu.nenu.ist.ndnshare.SearchFragment.SearchPage;
import edu.nenu.ist.ndnshare.Service.AskService;
import edu.nenu.ist.ndnshare.Service.AskService.ErrorCode;
import edu.nenu.ist.ndnshare.Service.ShareService;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    //Fragment+RadioGroup相关参数
    private RadioGroup mainbotton;
    private int lastposition;
    private Fragment[] mFragments;

    Listener linstenr;

    //广播接收数据
    private LocalBroadcastReceiver broadcastReceiver;

    //ShareService
    private static final int NOTIFICATION_ID = 0;
    public static final int SERVICE_NOTIFICATION_ID = 1;

    private void registerBroadcastReceiver() {
        broadcastReceiver = new LocalBroadcastReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ShareService.BCAST_RECEIVED_ASK);
        intentFilter.addAction(AskService.BCAST_ERROR);
        LocalBroadcastManager.getInstance(this).registerReceiver(
                broadcastReceiver,
                intentFilter);
    }

    private class LocalBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "broadcast received");
            switch (intent.getAction()) {
                case ShareService.BCAST_RECEIVED_ASK:
                    //handleReceivedMessage(intent);
                    break;
                case AskService.BCAST_ERROR:
                    handleError(intent);
                    break;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);

        mainbotton = (RadioGroup) findViewById(R.id.mainbotton);
        mainbotton.check(R.id.filebutton);//默认选中
        //按键监听
        mainbotton.setOnCheckedChangeListener(new MainActivity.bottonchange());

        initFragment();

        registerBroadcastReceiver();



        Intent startService = new Intent(this, ShareService.class);
        startService.setAction(ShareService.ACTION_START);
        startService(startService);


    }

    public interface Listener {
        void listener(int position);
    }

    public void setLinstenr(Listener linstenr) {
        this.linstenr = linstenr;
    }

    class bottonchange implements RadioGroup.OnCheckedChangeListener{
        @Override
        public void onCheckedChanged(RadioGroup group, int checkedId){
            switch(checkedId){
                case R.id.filebutton:
                    switchFragment(0);
                    break;
                case R.id.searchbutton:
                    switchFragment(1);
                    break;
                default:
                    break;
            }
        }
    }

    private void initFragment(){
        FilePage fileFragment =new FilePage(this);
        SearchPage searchFragment = new SearchPage(this);
        FilePage switchPage = new FilePage(this);
        mFragments = new Fragment[]{fileFragment,searchFragment,switchPage};
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.add(R.id.main_list,fileFragment).commit();
        switchFragment(0);
    }

    //添加页面
    private void switchFragment(int position){
        FragmentManager manager = getSupportFragmentManager();
        FragmentTransaction ft = manager.beginTransaction();
        if (lastposition != position){
            ft.replace(R.id.main_list, mFragments[position]);
            Log.e(TAG,"replace fragment");
            lastposition = position;
        }
        ft.commit();
    }

    //菜单栏
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_quit:

                //quitApplication();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void quitApplication() {
        Intent request = new Intent(this, ShareService.class);
        request.setAction(ShareService.ACTION_STOP);
        startService(request);
        finish();
    }

    private void handleError(Intent intent) {
        ErrorCode errorCode =
                (ErrorCode) intent.getSerializableExtra(AskService.EXTRA_ERROR_CODE);
        String toastText = null;

        switch (errorCode) {
            case NFD_PROBLEM:
                Log.e(TAG,"get NFD_PROBLEM error");
                toastText = getString(R.string.error_nfd);
                break;
            case OTHER_EXCEPTION:
                toastText = getString(R.string.error_other);
                break;
        }

        Toast.makeText(getApplicationContext(), toastText, Toast.LENGTH_LONG).show();

        initFragment();

    }
}
