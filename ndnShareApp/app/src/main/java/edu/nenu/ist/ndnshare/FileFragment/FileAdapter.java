package edu.nenu.ist.ndnshare.FileFragment;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

import edu.nenu.ist.ndnshare.R;

public class FileAdapter extends BaseAdapter {
    private static final String TAG = "FileAdapter";

    private Context context;
    private ArrayList<FileDetails> eachfile;

    public FileAdapter(Context context,ArrayList<FileDetails> eachfile){
        this.context = context;
        this.eachfile = eachfile;
    }

    public void setEachfile (ArrayList each){
        eachfile = each;
    }

    @Override
    public int getCount() {
        return eachfile.size();
    }

    @Override
    public Object getItem(int i) {
        return null;
    }

    @Override
    public long getItemId(int i) {
        return 0;
    }

    public String getEachfilename(int i) {
        return eachfile.get(i).getName();
    }

    public String getEachfileprefix(int i) {
        return eachfile.get(i).getPrefix();
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        Log.e(TAG,"begin to getview: " + eachfile.get(i).getName());
        ViewHoder viewHoder;
        if(view == null){
            view = View.inflate(context, R.layout.item_filepage,null);
            viewHoder = new ViewHoder();
            viewHoder.fileimage = (ImageView) view.findViewById(R.id.fileimage);
            viewHoder.filename = (TextView) view.findViewById(R.id.filename);
            viewHoder.filetype = (TextView) view.findViewById(R.id.filetype);
            viewHoder.filesize = (TextView) view.findViewById(R.id.filesize);

            view.setTag(viewHoder);
        }
        else {
            viewHoder = (ViewHoder) view.getTag();
        }
        //根据position得到列表中对应位置的数据
        FileDetails filedetails = eachfile.get(i);
        viewHoder.filename.setText(filedetails.getName());
        viewHoder.filetype.setText(filedetails.getType());
        viewHoder.filesize.setText(android.text.format.Formatter.formatFileSize(context,filedetails.getSize()));

        return view;
    }

    static class ViewHoder{
        ImageView fileimage;
        TextView filename;
        TextView filetype;
        TextView filesize;
    }


}
