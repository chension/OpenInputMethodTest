package com.chen.openinputmethodtest.activity;


import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;

import com.open.inputmethod.R;


/**
 * 输入法设置界面.
 * 
 * @author hailong.qiu 356752238@qq.com
 *
 */
public class SettingsActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_settings);
//		finish();
	}

	public class MyTestTask extends AsyncTask<Void ,Integer,Void>{

		@Override
		protected Void doInBackground(Void... params) {
			return null;
		}
	}

}
