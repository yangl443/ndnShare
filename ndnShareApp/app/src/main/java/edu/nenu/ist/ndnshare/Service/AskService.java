package edu.nenu.ist.ndnshare.Service;

import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Base64;
import android.util.Log;

import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.Interest;
import net.named_data.jndn.InterestFilter;
import net.named_data.jndn.Name;
import net.named_data.jndn.NetworkNack;
import net.named_data.jndn.OnData;
import net.named_data.jndn.OnInterestCallback;
import net.named_data.jndn.OnNetworkNack;
import net.named_data.jndn.OnRegisterFailed;
import net.named_data.jndn.OnRegisterSuccess;
import net.named_data.jndn.OnTimeout;
import net.named_data.jndn.security.KeyChain;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.security.identity.IdentityManager;
import net.named_data.jndn.security.identity.MemoryIdentityStorage;
import net.named_data.jndn.security.identity.MemoryPrivateKeyStorage;
import net.named_data.jndn.sync.ChronoSync2013;
import net.named_data.jndn.util.Blob;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import edu.nenu.ist.ndnshare.FileFragment.FileDetails;
import edu.nenu.ist.ndnshare.MyApplication;
import edu.nenu.ist.ndnshare.R;

public abstract class AskService extends Service {
    private static final String TAG = "AskService";

    private boolean networkThreadShouldStop,
                    syncInitialized = false;

    //jndn
    protected Face face;
    private Name dataPrefix, broadcastPrefix;
    boolean askExactFileList ,askFuzzyFileList , askFile;
    private int session;
    String baseFileListPrefix, baseFilePrefix, baseDataPrefix, extraFileListPrefix , extraFilePrefix;
    final int datasize = 5000;
    String base64file;

    /* Intent constants */
    public static final String
            INTENT_PREFIX = "edu.nenu.ist.NDNShare." + TAG + ".",
            BCAST_ERROR = INTENT_PREFIX + "BCAST_ERROR",
            EXTRA_ERROR_CODE = INTENT_PREFIX + "EXTRA_ERROR_CODE";

    //线程相关
    private KeyChain keyChain;
    public enum ErrorCode { NFD_PROBLEM, OTHER_EXCEPTION }

    private ErrorCode raisedErrorCode = null;

    //Sync2013test
    private ChronoSync2013 sync;
    private static final double SYNC_LIFETIME = 5000.0;

    //全局变量
    private MyApplication app;
    private ArrayList<FileDetails> eachfile, returnlistfile;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private final Thread networkThread = new Thread(new Runnable() {
        @Override
        public void run () {
            Log.d(TAG, "network thread started");
            try {
                initializeKeyChain();
                setCommandSigningInfo();
                registerBroadcastPrefix();
                registerDataPrefix();
                doApplicationSetup();
                //setUpChronoSync();
            } catch (Exception e) {
                raiseError("error during network thread initialization",
                        ErrorCode.OTHER_EXCEPTION, e);
            }
            while (!networkThreadShouldStop) {
                askExactFileListIfNeeded();
                askFuzzyFileListIfNeeded();
                askFileIfNeeded();
                //getFileIfNeeded();
                try {
                    face.processEvents();
                    Thread.sleep(100); // avoid hammering the CPU
                } catch (IOException e) {
                    raiseError("error in processEvents loop", ErrorCode.NFD_PROBLEM, e);
                } catch (Exception e) {
                    raiseError("error in processEvents loop", ErrorCode.OTHER_EXCEPTION, e);
                }
            }
            doFinalCleanup();
            handleAnyRaisedError();
            Log.d(TAG, "network thread stopped");
        }
    });



    protected void initializeService(String dataPrefixStr, String broadcastPrefixStr) {
        Log.d(TAG, "initializing service...");
        stopNetworkThreadAndBlockUntilDone();
        face = new Face(getString(R.string.face_uri));
        baseDataPrefix = dataPrefixStr;
        baseFileListPrefix = broadcastPrefixStr;
        dataPrefix = new Name(dataPrefixStr);
        broadcastPrefix = new Name(broadcastPrefixStr);
        askExactFileList = false;
        askFuzzyFileList = false;
        askFile = false;
        session = (int) (System.currentTimeMillis() / 1000);
        startNetworkThread();
        Log.d(TAG, "service initialized");
    }

    private void stopNetworkThreadAndBlockUntilDone() {
        stopNetworkThread();
        Log.d(TAG, "waiting for network thread to stop...");
        while(networkThread.isAlive()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Log.e(TAG, "interruption while waiting for network thread to stop", e);
            }
        }
    }

    private void stopNetworkThread() {
        networkThreadShouldStop = true;
    }

    private void startNetworkThread() {
        if (!networkThread.isAlive()) {
            networkThreadShouldStop = false;
            networkThread.start();
        }
    }

    protected boolean networkThreadIsRunning() {
        return networkThread.isAlive();
    }

    //Thread networkThread相关函数
    private void initializeKeyChain() {
        Log.d(TAG, "initializing keychain");
        MemoryIdentityStorage identityStorage = new MemoryIdentityStorage();
        MemoryPrivateKeyStorage privateKeyStorage = new MemoryPrivateKeyStorage();
        IdentityManager identityManager = new IdentityManager(identityStorage, privateKeyStorage);
        keyChain = new KeyChain(identityManager);
        keyChain.setFace(face);
    }

    private void setCommandSigningInfo() {
        Log.d(TAG, "setting command signing info");
        Name defaultCertificateName;
        try {
            defaultCertificateName = keyChain.getDefaultCertificateName();
        } catch (SecurityException e) {
            Log.d(TAG, "unable to get default certificate name");

            // NOTE: This is based on apps-NDN-Whiteboard/helpers/Utils.buildTestKeyChain()...
            Name testIdName = new Name("/test/identity");
            try {
                defaultCertificateName = keyChain.createIdentityAndCertificate(testIdName);
                keyChain.getIdentityManager().setDefaultIdentity(testIdName);
                Log.d(TAG, "created default ID: " + defaultCertificateName.toString());
            } catch (SecurityException e2) {
                defaultCertificateName = new Name("/bogus/certificate/name");
                raiseError("unable to create default identity", ErrorCode.OTHER_EXCEPTION, e2);
            }
        }
        face.setCommandSigningInfo(keyChain, defaultCertificateName);
    }

    private void registerBroadcastPrefix () {
        Log.d(TAG, "registering data prefix:" + baseFileListPrefix + "/" +"yui");
        Name test = new Name("/");
        try {
            Name testPrefix = new Name(baseFileListPrefix);
            face.registerPrefix(testPrefix, OnAskInterest,
                    OnAskPrefixRegisterFailed, OnAskPrefixRegisterSuccess);
        } catch (IOException | SecurityException e) {
            // should also be handled in callback, but in just in case...
            raiseError("exception registering data prefix", ErrorCode.NFD_PROBLEM, e);
        }

    }

    private void registerDataPrefix () {
        Log.d(TAG, "registering data prefix:" + baseFileListPrefix + "/" +"yui");
        String prefix = "askfile/" + baseFileListPrefix;
        try {
            Name testPrefix = new Name(prefix);
            face.registerPrefix(testPrefix, OnAskFileInterest,
                    OnAskPrefixRegisterFailed, OnAskPrefixRegisterSuccess);
        } catch (IOException | SecurityException e) {
            // should also be handled in callback, but in just in case...
            raiseError("exception registering data prefix", ErrorCode.NFD_PROBLEM, e);
        }

    }

    private void askExactFileListIfNeeded() {
        if (askExactFileList == true){
            //Name askFileListPrefix = new Name(baseFileListPrefix + "/"
            //+ "query/" + extraFileListPrefix);
            Name askFileListPrefix = new Name(baseFileListPrefix + "/" + baseDataPrefix + "/"
                    + extraFileListPrefix);
            expressAskInterest(askFileListPrefix);
            //publishSequenceNo();
            askExactFileList = false;
        }
    }

    private void askFuzzyFileListIfNeeded() {
        if (askFuzzyFileList == true){
            //Name askFileListPrefix = new Name(baseFileListPrefix + "/"
            //+ "query/" + extraFileListPrefix);
            Name askFileListPrefix = new Name(baseFileListPrefix + "/" + baseDataPrefix + "/query/"
                    + extraFileListPrefix);
            expressAskInterest(askFileListPrefix);
            //publishSequenceNo();
            askFuzzyFileList = false;
        }
    }

    private void askFileIfNeeded() {
        if (askFile == true){
            //Name askFileListPrefix = new Name(baseFileListPrefix + "/"
            //+ "query/" + extraFileListPrefix);
            Name askFilePrefix = new Name("askfile/"+ baseFilePrefix + "/"
                    + extraFilePrefix);
            expressFileInterest(askFilePrefix);
            //publishSequenceNo();
            askFile = false;
        }
    }

    private void expressAskInterest(Name BroadcastPrefix){
        Log.e(TAG, "expressAskInterest");
        try {
            Interest interest = new Interest(BroadcastPrefix);
            Log.e(TAG, "interest name:"+ interest.getName());
            face.expressInterest(interest, OnReceivedAskData,
                    OnInterestTimeout, OnInterestNack);
            //testOnAskInterest(interest);
        } catch (IOException e) {
            raiseError("failed to express data interest", ErrorCode.NFD_PROBLEM, e);
        }
    }

    private final void testOnAskInterest (Interest interest){
        Name interestName = interest.getName();
        Log.d(TAG, "OnInterestCallback() get askInterest from: "+ interestName);
        String searchname = (interestName.get(-1)).toEscapedString();
        Log.d(TAG, "search name: "+ searchname);
        app = (MyApplication) getApplication();
        eachfile = app.getFilelist();
        ArrayList<FileDetails> datalist = new ArrayList<FileDetails>();
        StringBuffer list = new StringBuffer();
        int size = eachfile.size();
        for(int i=0;i<size;i++){
            if(eachfile.get(i).getName().indexOf(searchname)>= 0){
                Log.d(TAG, "find a file: "+ eachfile.get(i).getName());
                datalist.add(eachfile.get(i));
                list.append("/");
                list.append(eachfile.get(i));
            }
        }
        list.append("/");
        Name testData = new Name(interestName);
        Data test = new Data(testData);
        Blob testlist = new Blob(list.toString());
        test.setContent(testlist);
        testOnData(test);
    }

    private final void testOnData(Data data){
        Log.d(TAG, "get Data." + data.getName().toString());
        returnlistfile = new ArrayList<FileDetails>();
        byte[] datalist = data.getContent().getImmutableArray();
        String test = new String(datalist);
        Name testlist = new Name(test);
        long count = testlist.getChangeCount();
        Log.d(TAG, "list长度: "+ testlist.getChangeCount());
        Log.d(TAG, "list内容: "+ test);

        int begin=0 ,end=1 ;

        for(int i =0;i<count-1;i++){
            FileDetails onefile = new FileDetails();
            Log.d(TAG, "setString: "+ test.substring(test.indexOf("/",begin),test.indexOf("/",end)));
            onefile.setString(test.substring(test.indexOf("/",begin)+1,test.indexOf("/",end)));
            begin = test.indexOf("/",end); end = begin + 1;
            Log.d(TAG, "eachfile name: "+ onefile.getName());
            onefile.setPrefix(data.getName().toString());
            Log.d(TAG, "file setPrefix: "+ data.getName().toString());
            returnlistfile.add(onefile);
        }

        app = (MyApplication) getApplication();
        app.setReturnlist(returnlistfile);

    }

    private final OnInterestCallback OnAskInterest = new OnInterestCallback() {
        @Override
        public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId,
                               InterestFilter filterData) {
            Name interestName = interest.getName();
            Log.d(TAG, "OnInterestCallback() get askInterest from: "+ interestName);
            //区分精确与模糊
            if(interestName.toString().indexOf("query")>=0){
                String searchname = (interestName.get(-3)).toEscapedString();
                String fuzzyname = (interestName.get(-1)).toEscapedString();
                Log.d(TAG, "search name: "+ searchname);
                Log.d(TAG, "fuzzy name: "+ fuzzyname);
                app = (MyApplication) getApplication();
                eachfile = app.getFilelist();
                ArrayList<FileDetails> datalist = new ArrayList<FileDetails>();
                StringBuffer list = new StringBuffer();
                int size = eachfile.size();
                for(int i=0;i<size;i++){
                    if(eachfile.get(i).getName().indexOf(searchname)>= 0 || eachfile.get(i).getName().indexOf(fuzzyname)>= 0){
                        Log.d(TAG, "find a file: "+ eachfile.get(i).getName());
                        datalist.add(eachfile.get(i));
                        list.append("/");
                        list.append(eachfile.get(i));
                    }
                }
                list.append("/");
                Name testData = new Name(interestName);
                Data data = new Data(testData);
                Blob returnDatalist = new Blob(list.toString());
                data.setContent(returnDatalist);
                try {
                    Log.d(TAG, "put data to: " + interestName.toString());
                    face.putData(data);
                } catch (IOException e) {
                    raiseError("failure when responding to data interest",
                            ErrorCode.NFD_PROBLEM, e);
                }
            }else{
                String searchname = (interestName.get(-1)).toEscapedString();
                Log.d(TAG, "search name: "+ searchname);
                app = (MyApplication) getApplication();
                eachfile = app.getFilelist();
                ArrayList<FileDetails> datalist = new ArrayList<FileDetails>();
                StringBuffer list = new StringBuffer();
                int size = eachfile.size();
                for(int i=0;i<size;i++){
                    if(eachfile.get(i).getName().indexOf(searchname)>= 0){
                        Log.d(TAG, "find a file: "+ eachfile.get(i).getName());
                        datalist.add(eachfile.get(i));
                        list.append("/");
                        list.append(eachfile.get(i));
                    }
                }
                list.append("/");
                Name testData = new Name(interestName);
                Data data = new Data(testData);
                Blob returnDatalist = new Blob(list.toString());
                data.setContent(returnDatalist);
                try {
                    Log.d(TAG, "put data to: " + interestName.toString());
                    face.putData(data);
                } catch (IOException e) {
                    raiseError("failure when responding to data interest",
                            ErrorCode.NFD_PROBLEM, e);
                }
            }
        }
    };

    private final OnData OnReceivedAskData = new OnData() {
        @Override

        public void onData(Interest interest, Data data) {
            Name name = data.getName();
            //String getData = data.getContent().toString();
            Log.d(TAG, "received ask data for " + name);
            //Log.d(TAG, "received ask data : " + getData);
            returnlistfile = new ArrayList<FileDetails>();
            byte[] datalist = data.getContent().getImmutableArray();
            String test = new String(datalist);
            Name testlist = new Name(test);
            long count = testlist.getChangeCount();
            Log.d(TAG, "list长度: "+ testlist.getChangeCount());
            Log.d(TAG, "list内容: "+ test);

            int begin=0 ,end=1 ;

            for(int i =0;i<count-1;i++){
                FileDetails onefile = new FileDetails();
                Log.d(TAG, "setString: "+ test.substring(test.indexOf("/",begin),test.indexOf("/",end)));
                onefile.setString(test.substring(test.indexOf("/",begin)+1,test.indexOf("/",end)));
                begin = test.indexOf("/",end); end = begin + 1;
                Log.d(TAG, "eachfile name: "+ onefile.getName());
                onefile.setPrefix(data.getName().toString());
                returnlistfile.add(onefile);
            }

            app = (MyApplication) getApplication();
            app.setReturnlist(returnlistfile);
        }
    };

    private void expressFileInterest(Name BroadcastPrefix){
        Log.e(TAG, "expressFileInterest");
        try {
            Interest interest = new Interest(BroadcastPrefix);
            Log.e(TAG, "interest name:"+ interest.getName());
            face.expressInterest(interest, OnReceivedFileData,
                    OnInterestTimeout, OnInterestNack);
        } catch (IOException e) {
            raiseError("failed to express data interest", ErrorCode.NFD_PROBLEM, e);
        }
    }

    private final OnInterestCallback OnAskFileInterest = new OnInterestCallback() {
        @Override
        public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId,
                               InterestFilter filterData) {
            Name interestName = interest.getName();
            Log.d(TAG, "OnInterestCallback() get askInterest from: "+ interestName);
            String searchname = (interestName.get(-1)).toEscapedString();
            Log.d(TAG, "search name: "+ searchname);

            File dir = new File("/sdcard/ndnshare/");
            String filepath = findFile(dir, searchname);
            Log.d(TAG, "filepath: "+ filepath);
            String base64file;
            if(filepath == null){
                base64file = "no file";
            }else {
                base64file = fileToBase64(filepath);
                Log.d(TAG, "base64 file: "+ base64file);
            }

            String sendfile;
            if(base64file.length()<datasize){
                sendfile = base64file;
            }else{
                sendfile = "fileNeedSplit/" + String.valueOf(base64file.length()/datasize)+ "/";
            }

            Name testData = new Name(interestName);
            Data data = new Data(testData);
            Blob returnData = new Blob(sendfile);

            data.setContent(returnData);
            try {
                Log.d(TAG, "put data to: " + interestName.toString());
                face.putData(data);
            } catch (IOException e) {
                raiseError("failure when responding to data interest",
                        ErrorCode.NFD_PROBLEM, e);
            }
        }
    };

    private final OnData OnReceivedFileData = new OnData() {
        @Override

        public void onData(Interest interest, Data data) {
            Log.d(TAG, "get Data." + data.getName().toString());
            String filename = (data.getName().get(-1)).toEscapedString();
            Log.d(TAG, "file name:" + filename);
            byte[] fileByte = data.getContent().getImmutableArray();
            String fileBase64 = new String(fileByte);
            Log.d(TAG, "get file base64: "+ fileBase64);
            String dir = "/sdcard/ndnshare/download/";
            base64ToFile(dir,fileBase64,filename);
        }
    };

    public String findFile(File dir,String search) {
        //得到某个文件夹下所有的文件
        File[] files = dir.listFiles();
        //文件为空
        if (files == null) {
            return null;
        }
        //遍历当前文件下的所有文件
        for (File file : files) {
            //如果是文件夹
            if (file.isDirectory()) {
                //则递归(方法自己调用自己)继续遍历该文件夹
                findFile(file,search);
            }
            else { //如果不是文件夹 则是文件
                String name = file.getName();
                Log.d(TAG,"search = "+ search);
                if(name.equals(search)){
                    return file.getPath();
                }
            }
        }
        return null;
    }

    public static  String fileToBase64(String path) {
        String base64 = null;
        InputStream in = null;
        try {
            File file = new File(path);
            in = new FileInputStream(file);
            byte[] bytes = new byte[in.available()];
            int length = in.read(bytes);
            base64 = Base64.encodeToString(bytes, 0, length, Base64.DEFAULT);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return base64;
    }

    public static void base64ToFile(String destPath,String base64, String fileName) {
        File file = null;
        //创建文件目录
        String filePath=destPath;
        File  dir=new File(filePath);
        if (!dir.exists() && !dir.isDirectory()) {
            dir.mkdirs();
        }
        BufferedOutputStream bos = null;
        java.io.FileOutputStream fos = null;
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            file=new File(filePath+"/"+fileName);
            fos = new java.io.FileOutputStream(file);
            bos = new BufferedOutputStream(fos);
            bos.write(bytes);
        } catch (Exception e) {

            e.printStackTrace();
        } finally {
            if (bos != null) {
                try {
                    bos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private final OnTimeout OnInterestTimeout = new OnTimeout() {
        @Override
        public void onTimeout(Interest interest) {
            Name name = interest.getName();
            Log.d(TAG, "timed out waiting for " + name);
            //expressAskInterest(name);
        }
    };

    private final OnNetworkNack OnInterestNack = new OnNetworkNack() {
        @Override
        public void onNetworkNack(Interest interest, NetworkNack networkNack) {
            Name name = interest.getName();
            Log.d(TAG, "received NACK for " + name);
        }
    };

    private final OnRegisterSuccess OnAskPrefixRegisterSuccess = new OnRegisterSuccess() {
        @Override
        public void onRegisterSuccess(Name prefix, long registeredPrefixId) {
            Log.d(TAG, "successfully registered data prefix: " + prefix);
        }
    };

    private final OnRegisterFailed OnAskPrefixRegisterFailed = new OnRegisterFailed() {
        @Override
        public void onRegisterFailed(Name prefix) {
            raiseError("failed to register application prefix " + prefix.toString(),
                    ErrorCode.NFD_PROBLEM);
        }
    };

    //TEST!!!!!!!!!!!!!!!!!!!!!!!
    private void setUpChronoSync() {
        try {
            sync = new ChronoSync2013(OnReceivedChronoSyncState, OnChronoSyncInitialized,
                    dataPrefix, broadcastPrefix, session,
                    face, keyChain,
                    keyChain.getDefaultCertificateName(),
                    SYNC_LIFETIME,
                    OnBroadcastPrefixRegisterFailed);
        } catch (IOException | SecurityException e) {
            // should also be handled in callback, but in just in case...
            raiseError("exception setting up ChronoSync", ErrorCode.NFD_PROBLEM, e);
        }
    }

    private final ChronoSync2013.OnReceivedSyncState OnReceivedChronoSyncState =
            new ChronoSync2013.OnReceivedSyncState() {
                @Override
                public void onReceivedSyncState(List syncStates, boolean isRecovery) {
                    Log.d(TAG, "sync states received");
                    for (Object syncState : syncStates) {
                        processSyncState((ChronoSync2013.SyncState) syncState, isRecovery);
                    }
                    Log.d(TAG, "finished processing " + syncStates.size() + " sync states");
                }
            };

    private void processSyncState(ChronoSync2013.SyncState syncState, boolean isRecovery) {
        long syncSession = syncState.getSessionNo(),
                syncSeqNum = syncState.getSequenceNo();
        String syncDataPrefix = syncState.getDataPrefix(),
                syncDataId = syncDataPrefix + "/" + syncSession;

        Log.d(TAG, "received" + (isRecovery ? " RECOVERY " : " ") + "sync state for " +
                syncState.getDataPrefix() + "/" + syncSession + "/" + syncSeqNum);


    }

    private final ChronoSync2013.OnInitialized OnChronoSyncInitialized =
            new ChronoSync2013.OnInitialized() {
                @Override
                public void onInitialized() {
                    Log.d(TAG, "ChronoSync initialization complete; seqnum is now " +
                            sync.getSequenceNo());
                    // Ensure that sentData is in sync with the initial seqnum
                    syncInitialized = true;
                }
            };

    private final OnRegisterFailed OnBroadcastPrefixRegisterFailed = new OnRegisterFailed() {
        @Override
        public void onRegisterFailed(Name prefix) {
            raiseError("failed to register broadcast prefix " + prefix.toString(),
                    ErrorCode.NFD_PROBLEM);
        }
    };

    protected abstract void doApplicationSetup();

    protected void raiseError(String logMessage, ErrorCode code, Throwable exception) {
        if (exception == null) Log.e(TAG, logMessage);
        else Log.e(TAG, logMessage, exception);
        if (raisedErrorCode == null) raisedErrorCode = code;
        stopNetworkThread();
    }

    protected void raiseError(String logMessage, ErrorCode code) {
        raiseError(logMessage, code, null);
    }

    private void doFinalCleanup() {
        Log.d(TAG, "cleaning up and resetting service...");
        syncInitialized = false;
        if (face != null) face.shutdown();
        face = null;
        Log.d(TAG, "service cleanup/reset complete");
    }

    private void handleAnyRaisedError() {
        Log.d(TAG, "handleAnyRaisedError " + raisedErrorCode);
        if (raisedErrorCode == null) return;
        stopSelf();
        Log.d(TAG, "broadcasting error intent w/code = " + raisedErrorCode + "...");
        Intent bcast = new Intent(BCAST_ERROR);
        bcast.putExtra(EXTRA_ERROR_CODE, raisedErrorCode);
        LocalBroadcastManager.getInstance(AskService.this).sendBroadcast(bcast);
    }
}
