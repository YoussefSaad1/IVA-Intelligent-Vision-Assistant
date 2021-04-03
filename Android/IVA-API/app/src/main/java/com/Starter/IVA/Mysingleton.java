package com.Starter.IVA;

import android.content.Context;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

public class Mysingleton {
    private static com.Starter.IVA.Mysingleton mInstance;
    private RequestQueue requestQueue;
    private  static Context mctxt;

    private Mysingleton(Context context)
    {
        mctxt = context;
        requestQueue = getRequestQueue();
    }
    public RequestQueue getRequestQueue()
    {
        if( requestQueue == null )
        {
            requestQueue = Volley.newRequestQueue(mctxt.getApplicationContext());
        }
        return requestQueue;
    }
    public  static synchronized com.Starter.IVA.Mysingleton getInstance(Context context)
    {
        if( mInstance == null )
        {
            mInstance = new com.Starter.IVA.Mysingleton(context);
        }
        return mInstance;
    }
    public <T> void addToRequestqueue(Request<T> request)
    {
        requestQueue.add(request);
    }
}
