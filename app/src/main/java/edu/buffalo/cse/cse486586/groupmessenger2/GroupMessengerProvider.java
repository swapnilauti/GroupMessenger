package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * GroupMessengerProvider is a key-value table.
 */
public class GroupMessengerProvider extends ContentProvider {


    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        FileOutputStream f= null;
        String key = values.getAsString("key");
        Log.v("insert", "Key  : "+key);
        String value = values.getAsString("value");
        Log.v("insert", "value : "+value);
        try {
            f = getContext().openFileOutput(key, 0);
        }catch (FileNotFoundException e)
        {
            Log.v("insert", "Error : GroupMessengerProvider.java->insert-> File not found");
        }
        try{
            f.write(value.getBytes());
            f.close();
        }
        catch(IOException e)
        {
            Log.v("insert", "Error : GroupMessengerProvider.java->insert-> IO Exception");
        }
        Log.v("insert", values.toString());
        return uri;
    }

    @Override
    public boolean onCreate() {
        return false;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        FileInputStream f= null;
        String key = selection;
        String columnNm[] = {"key","value"};
        String columnVal[]= new String[2];
        MatrixCursor m = new MatrixCursor(columnNm);
        try {
            f = getContext().openFileInput(key);
        }catch (FileNotFoundException e)
        {
            Log.v("insert", "Error : GroupMessengerProvider.java-> query -> File not found");
        }

        try{
            BufferedReader br = new BufferedReader(new InputStreamReader(f));
            columnVal[0]=key;
            columnVal[1]=br.readLine();
            m.addRow(columnVal);
            f.close();
            br.close();
        }
        catch(IOException e)
        {
            Log.v("insert", "Error : GroupMessengerProvider.java->insert-> IO Exception");
        }Log.v("query", selection);
        return m;
    }
}
