package edu.nenu.ist.ndnshare.FileFragment;

import android.util.Log;

public class FileDetails {
    private static final String TAG = "FileDetails";
    private String name;
    private String type;
    private long size;
    private String prefix;

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }

    public long getSize() {
        return size;
    }
    public void setSize(long size) {
        this.size = size;
    }

    public String getPrefix() {
        return prefix;
    }
    public void setPrefix(String Prefix) {
        this.prefix = Prefix;
    }

    @Override
    public String toString() {
        return "eachfile{" +
                "name=" + name +
                ", type=" + type +
                ", size=" + size +
                ",}";
    }
    public void setString(String str) {
        String name = str.substring(str.indexOf("=",0)+1,str.indexOf(",",0));
        int a=str.indexOf("=",0); int b = str.indexOf(",",0);
        String type = str.substring(str.indexOf("=",a+1)+1,str.indexOf(",",b+1));
        a=str.indexOf("=",a+1); b = str.indexOf(",",b+1);
        String sizestr = str.substring(str.indexOf("=",a+1)+1,str.indexOf(",",b+1));
        Long size = Long.parseLong(sizestr);

        this.setName(name);
        this.setType(type);
        this.setSize(size);
    }
}
