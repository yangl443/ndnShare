package edu.nenu.ist.ndnshare;

import android.app.Application;

import com.squareup.leakcanary.LeakCanary;

import java.util.ArrayList;

import edu.nenu.ist.ndnshare.FileFragment.FileDetails;

public class MyApplication extends Application {
    private ArrayList<FileDetails> filelist;
    private ArrayList<FileDetails> returnlist;
    @Override
    public void onCreate()
    {
        super.onCreate();
    }

    public void setFilelist(ArrayList<FileDetails> filelist)
    {
        this.filelist = filelist;
    }
    public void setReturnlist(ArrayList<FileDetails> filelist)
    {
        this.returnlist = filelist;
    }


    public ArrayList<FileDetails> getFilelist()
    {
        return filelist;
    }
    public ArrayList<FileDetails> getReturnlist()
    {
        return returnlist;
    }

}
